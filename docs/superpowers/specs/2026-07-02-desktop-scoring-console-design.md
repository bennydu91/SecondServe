# Console de saisie point par point (mode avancé desktop)

**Date** : 2026-07-02
**Statut** : Validé, prêt pour planification d'implémentation

## Contexte et objectif

Second et dernier sous-projet du mode desktop avancé prévu par le design system « Broadcast » (`design/README.md`, écran 9), après le tableau de bord (`docs/superpowers/specs/2026-07-02-desktop-dashboard-design.md`, déjà livré). Le README décrit cet écran comme : « C'est le mode avancé : saisir la stat fine impossible au geste rapide de la montre. »

**Usage principal** : ressaisir manuellement des matchs déjà joués (rétro-saisie), avec la date réelle du match, pour construire un historique riche en contexte tactique fin (ace, coup gagnant, faute provoquée…) — une granularité que l'app Android/Wear ne capture pas aujourd'hui (elle ne connaît que le vainqueur de chaque point, pas pourquoi).

**Usage secondaire** : secours en direct si l'app Android ou la montre plante en cours de match. La console ne remplace jamais le flux normal montre/téléphone — elle prend le relais ponctuellement, avec un score de départ saisi manuellement puisque l'historique point par point du match en cours n'a pas été persisté avant la panne.

Les deux usages partagent le même écran et le même moteur de scoring ; seul le point d'entrée diffère (nouvelle session vs reprise d'une session `ACTIVE` existante).

## Décisions validées

- **Projet** : extension du `web/` existant (même app que le tableau de bord), nouvel item de navigation dans le `Sidebar` du dashboard.
- **Portée des points d'entrée** :
  1. **Nouveau match** (rétro-saisie) : formulaire identique à la création de session existante (surface, adversaire, format, `third_set_rule`) **+ un champ date modifiable** (par défaut aujourd'hui), car les matchs rétro-saisis se sont produits dans le passé et doivent rester chronologiquement cohérents avec l'historique/le win-rate mensuel du tableau de bord.
  2. **Reprise d'une session active** (secours en direct) : liste des sessions `status=ACTIVE`, bouton « Reprendre » → petit formulaire de score de départ (sets terminés, jeux et points du set en cours) avant d'entrer dans la console.
- **Contexte tactique** : 8 tags fixes, chacun impliquant à la fois le vainqueur du point et la raison — pas de sélection séparée « qui a gagné » puis « pourquoi » :
  - Mon point : `ACE`, `WINNER`, `FORCED_ERROR` (faute provoquée chez l'adversaire), `UNFORCED_ERROR_OPPONENT` (faute adverse simple, non provoquée)
  - Point adverse : `ACE_OPPONENT`, `WINNER_OPPONENT`, `UNFORCED_ERROR_SELF` (ma faute directe), `DOUBLE_FAULT` (ma double faute)
- **Pas de bouton « Basculer »** : avec 8 boutons contextuels distincts (contre 2 dans le mockup montre/téléphone), un mapping miroir automatique serait ambigu. Seul « Annuler le dernier point » est conservé ; corriger une erreur de saisie = annuler puis retaper le bon des 8 boutons.
- **Persistance immédiate** : chaque point cliqué est envoyé au backend et persisté avant de considérer l'action terminée (pas de purement-en-mémoire) — la console sert justement de filet de sécurité, elle ne doit pas perdre son propre état sur un refresh accidentel.
- **Finalisation automatique** : dès que le moteur de scoring détecte la fin du match (vainqueur décidé), la session passe en `COMPLETED` avec `result`/`score_text` calculés, sans clic de confirmation supplémentaire. « Annuler » reste actif juste après, au cas où le dernier point serait une erreur (repasse alors la session en `ACTIVE`).
- **Live-share opportuniste** : la console pousse le score courant vers le flux de partage public existant **uniquement si un lien existe déjà** pour cette session. Pour la rétro-saisie (aucun lien créé), rien n'est poussé.

## Architecture

### Backend

**Nouvelle feature `features/points/`** (models/schemas/repository/service + router, même structure que les autres features) :
- Table `points` (existe déjà en base via la migration `e5f6a7b8c9d0_add_points_sync_queue_feeling.py`, jamais utilisée jusqu'ici) : colonnes actuelles `id`, `session_id` (FK), `scorer` (`A`/`B`), `sequence_num`, `recorded_at`. **Nouvelle migration Alembic** : ajout d'une colonne `context` (String, nullable) portant l'une des 8 valeurs ci-dessus.
- `POST /api/v1/sessions/{session_id}/points` (JWT requis) — corps `{ context: <une des 8 valeurs> }` ; le `scorer` (`A`/`B`) est dérivé du `context` côté service (mapping fixe 1:1, pas transmis par le client) ; `sequence_num` = auto-incrémenté par session (max existant + 1). Retourne le point créé.
- `DELETE /api/v1/sessions/{session_id}/points/last` (JWT requis, 204) — supprime le point de plus haut `sequence_num` pour la session ; no-op silencieux si aucun point n'existe.
- `GET /api/v1/sessions/{session_id}/points` (JWT requis) — liste triée par `sequence_num` croissant, pour la reconstruction d'état au chargement de la console et l'affichage du déroulé (colonne de droite).

**`SessionModel`** (`features/sessions/models.py`) : nouvelle colonne nullable `score_seed_json` (String/Text) — migration Alembic dédiée. Contient un JSON représentant l'état de `MatchScore` au moment où l'opérateur a saisi le score de départ (voir ci-dessous), avec exactement les champs suivants : `completed_sets: [{games_a, games_b}]`, `current_set_games_a`, `current_set_games_b`, `current_game_points_a`/`current_game_points_b` (`ZERO`/`FIFTEEN`/`THIRTY`/`FORTY`/`ADVANTAGE`), `tie_break_points_a`, `tie_break_points_b`, `is_tie_break`, `is_super_tie_break` — mêmes noms/valeurs que `MatchScore` côté moteur, pour une désérialisation directe sans mapping. `current_set_point_log` n'en fait pas partie (vide par construction : le déroulé du set en cours au moment du seed n'a jamais été persisté, seuls les points saisis après reprise apparaîtront dans le déroulé de droite). `null` = le match a été suivi depuis 0-0 par la console (cas rétro-saisie et cas secours si aucun score de départ n'a été renseigné).
- `SessionCreateRequest`/`SessionResponse` (`features/sessions/schemas.py`) : ajout du champ optionnel `score_seed_json` en écriture (au moment de reprendre une session active, un endpoint dédié — voir ci-dessous — l'enregistre) et en lecture.
- Nouvelle route `PUT /api/v1/sessions/{session_id}/score-seed` (JWT requis) — corps = même forme JSON que `score_seed_json`, l'enregistre sur la session. Appelée une seule fois, avant d'entrer dans la console, quand l'opérateur reprend une session `ACTIVE` avec un score de départ non nul.

**Réutilisation sans modification** :
- `POST /api/v1/sessions` (création, déjà accepte `created_at` en epoch ms modifiable — le champ date du formulaire "Nouveau match" l'utilise directement).
- `POST /api/v1/sync/push` — finalisation automatique (`status=COMPLETED`, `result`, `score_text` calculés côté web à partir de l'état final du moteur, envoyés dans un `SyncSessionDto`).
- `POST /api/v1/live/shares` + `POST /api/v1/live/sessions/{id}/score` — push opportuniste vers le partage public existant.

### Frontend (`web/`)

**Moteur de scoring** : `web/lib/scoreEngine.ts`, port TypeScript fidèle de `TennisScoreEngine.kt`/`MatchScore.kt` (mêmes règles : 0/15/30/40, avantage/deuce, jeu à +2, set à 6 jeux ou 7-5, tie-break à 6-6 compté à 7 avec 2 d'écart, super tie-break à 10 points si `third_set_rule=SUPER_TIE_BREAK_10`, super jeu décisif 3-3 si `SHORT_DECISIVE_SET`, best-of-1/3). API : `createEngine(format, seed?)`, `recordPoint(engine, scorer)`, `undo(engine)`, état exposé `MatchScore` (mêmes champs que côté Kotlin, camelCase déjà natif en TS).

**Navigation** : nouvel item dans `web/components/dashboard/Sidebar.tsx` → `app/dashboard/console/page.tsx` (écran de sélection).

**Écran de sélection** (`app/dashboard/console/page.tsx`) :
- Liste des sessions `status=ACTIVE` (via `GET /sessions`, déjà existant, filtré côté client) avec bouton « Reprendre ».
- Bouton « Nouveau match » → formulaire (surface, adversaire, format, `third_set_rule`, **date**) → `POST /sessions` → redirection vers la console pour cette session, seed = 0-0 (pas de `score_seed_json`).
- « Reprendre » sur une session active → petit formulaire de score de départ (sets terminés, jeux et points du set en cours, tie-break le cas échéant) → `PUT /sessions/{id}/score-seed` → redirection vers la console.

**Console** (`app/dashboard/console/[sessionId]/page.tsx`, 3 colonnes conformes au README #9) :
- **Gauche (340px)** : `ScoreCard` (réutilise la logique d'affichage de `ScoreTable.tsx`/`SetTrail.tsx` de la page publique, adaptée au thème clair/sombre du dashboard) + bouton « Annuler le dernier point ».
- **Centre** : grille des 8 boutons contextuels (4 « mon point » mis en avant visuellement, 4 « point adverse ») + tuiles de stats live dérivées d'un simple comptage des `context` déjà enregistrés pour la session (nombre d'aces, de coups gagnants, de fautes, etc. — calcul client, pas de nouvel endpoint d'agrégation).
- **Droite (288px)** : déroulé point par point (liste scrollable), alimentée par `GET /sessions/{id}/points`.
- **Au chargement** : `GET /sessions/{id}/points` + lecture de `score_seed_json` sur la session → reconstruction de l'état en initialisant le moteur avec le seed (ou 0-0) puis en rejouant chaque point dans l'ordre (`recordPoint` pour chacun) — couvre le cas où l'onglet est rechargé en cours de saisie, qu'il s'agisse d'une rétro-saisie interrompue ou d'une reprise en mode secours.
- **Chaque clic** sur un des 8 boutons : `POST /sessions/{id}/points` (attend la confirmation serveur avant de considérer le point acquis, cohérent avec l'exigence de résilience) → mise à jour du moteur local → si un lien de partage existe pour la session, push vers `/sessions/{id}/score`. Si le moteur signale la fin du match, appel `sync/push` pour finaliser la session.
- **Annuler** : `DELETE /sessions/{id}/points/last` → `undo()` sur le moteur local (recalcule l'état, y compris repasser `isMatchOver=false` si le dernier point finalisait le match — dans ce cas, un `sync/push` correctif repasse aussi la session en `ACTIVE`).

## Gestion des erreurs et cas limites

- **Échec réseau lors d'un clic de point** : le point n'est pas considéré comme acquis tant que le `POST` n'a pas réussi (pas de mise à jour optimiste avant confirmation) ; message d'erreur inline, le bouton reste cliquable pour réessayer.
- **Aucune session active à reprendre** : l'écran de sélection affiche un état vide sobre, seul « Nouveau match » est proposé.
- **Session déjà `COMPLETED`** consultée via une URL directe (`/dashboard/console/{id}`) : redirection vers l'écran de sélection (rien à saisir).
- **`score_seed_json` incohérent ou absent alors qu'il y a déjà des points enregistrés** : le seed n'est appliqué qu'à la création de la session dans ce flux (avant tout point) — pas de cas où les deux entrent en conflit, garanti par le fait que `PUT /score-seed` n'est appelé qu'une fois, avant le premier point.
- **Double-clic rapide sur un bouton de point** : `sequence_num` calculé côté serveur (max + 1) à chaque requête, pas de condition de course problématique pour un usage mono-utilisateur ; au pire un point en trop, corrigible via « Annuler ».
- **Live-share indisponible/erreur lors du push opportuniste** : n'interrompt pas la saisie — le point reste acquis même si le push vers le lien public échoue (best-effort, erreur loguée côté client, pas de blocage utilisateur).

## Tests

- **Backend (`features/points`)** : création de point (mapping `context` → `scorer` correct pour les 8 valeurs), auto-incrément de `sequence_num` par session, suppression du dernier point (et no-op si vide), liste triée, auth JWT requise sur les 3 routes.
- **Backend (`score-seed`)** : écriture/lecture de `score_seed_json`, auth JWT requise.
- **Frontend (`web/lib/scoreEngine.ts`)** : port direct des cas de test déjà couverts côté Kotlin (`TennisScoreEngineTest` si présent, sinon dérivés des règles du spec) — progression 0/15/30/40, deuce/avantage, jeu à 6-6 → tie-break, 7-5, super tie-break à 10, super jeu décisif 3-3, best-of-1, undo simple et undo après fin de match.
- **Reconstruction d'état** : test d'intégration simulant un rechargement — seed + N points rejoués reproduisent exactement l'état obtenu par un enchaînement direct de `recordPoint`.
- **Finalisation** : passage automatique en `COMPLETED` avec `result`/`score_text` corrects à la fin d'un match, et retour en `ACTIVE` après un `undo` qui annule le point final.

## Hors scope (YAGNI pour cette itération)

- Bouton « Basculer » / mapping miroir de tags (cf. décision ci-dessus).
- Écran d'agrégation dédié aux stats fines par `context` (aces, coups gagnants...) au-delà des tuiles live de la console elle-même — une exploitation plus poussée (ex: dans le tableau de bord) est un sous-projet futur si le besoin se confirme.
- Édition/suppression d'un point autre que le tout dernier (pas de correction en plein milieu du déroulé).
- Mode hors-ligne (la console suppose une connexion au backend disponible ; en son absence, retour au papier/mémoire comme aujourd'hui).
- Résolution de conflit si la montre/le téléphone recommence à pousser des données pendant qu'une session est en cours de reprise sur la console (cas jugé suffisamment rare pour ne pas être traité explicitement — `updated_at`/LWW du mécanisme `sync/push` existant limite déjà les dégâts).
