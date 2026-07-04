# Web : responsive, historique des matchs (édition/suppression), saisie rétroactive

**Date** : 2026-07-04
**Statut** : Validé, prêt pour planification d'implémentation

## Contexte et objectif

Après premier usage réel de la partie web (`web/`), trois manques bloquants sont remontés :

1. **Non responsive** : la mise en page (sidebar fixe, grilles à colonnes fixes, tableau à largeurs fixes) casse sur mobile/tablette.
2. **Pas d'édition/suppression** des matchs déjà enregistrés : aucun endroit dans l'UI ne permet de revenir sur un match, et le backend n'expose aucun `PATCH`/`DELETE` sur `/sessions/{id}`.
3. **Pas de saisie rétroactive** : la console (`ConsoleSelectionView` → `NewMatchForm`) ne sait créer qu'une session `ACTIVE` destinée à la saisie point par point en direct (`ConsoleScreen`). Impossible d'enregistrer directement le score final d'un match déjà joué (papier, hors ligne, oublié sur le moment).

Ces trois chantiers sont traités dans un seul document car ils partagent une bonne partie de leur architecture backend (un unique nouvel endpoint `PATCH`, réutilisé par l'édition et par la saisie rétroactive) et parce que l'utilisateur a choisi de les valider ensemble plutôt qu'en specs séparées.

Périmètre : application mono-utilisateur (pas de notion de propriétaire sur `SessionModel`), toutes les routes `/sessions` sont déjà protégées par JWT au niveau du router — aucune vérification d'ownership supplémentaire n'est nécessaire.

## Décisions validées

- **Backend** : un seul nouvel endpoint générique `PATCH /api/v1/sessions/{id}` (mise à jour partielle) + `DELETE /api/v1/sessions/{id}` (hard delete). Réutilisé pour l'édition d'un match existant **et** pour la finalisation d'un match saisi rétroactivement (créé `ACTIVE` via le `POST` existant, puis basculé `COMPLETED` via ce même `PATCH`). Pas de 3ᵉ mécanisme.
- **Restriction** : `PATCH`/`DELETE` ne sont exposés dans l'UI que pour les sessions `session_type == "MATCH"` (les séances d'entraînement restent hors périmètre).
- **Historique** : nouvelle page dédiée `/dashboard/history`, paginée côté client (20/page), car `GET /sessions` renvoie déjà l'intégralité des sessions sans pagination serveur (volume mono-utilisateur, pas de changement backend nécessaire pour le listing).
- **Saisie rétroactive** : intégrée dans le `NewMatchForm` existant via un toggle « Match en cours » / « Match déjà joué », plutôt qu'un point d'entrée séparé — un seul formulaire de création de match.
- **Navigation mobile** : bottom tab bar fixe (3 items : Tableau de bord / Console / Historique) sous 640px, plutôt qu'un menu burger — adapté à un nombre réduit d'items et à un usage one-handed.
- **Breakpoints responsive** : mobile `<640px`, tablette `640–1024px`, desktop `>1024px` (cohérents avec les media queries déjà présentes côté console).
- **Cohérence des stats** : pour un match saisi rétroactivement, `updated_at` est fixé à la même valeur que `created_at` (durée nulle), afin de ne pas fausser `computePlayTime` (qui calcule `updated_at - created_at`) avec le délai entre la date réelle du match et sa saisie tardive.

## Architecture

### Backend — extension de la feature `sessions`

**`app/features/sessions/schemas.py`** — nouveau schéma :

```python
class SessionUpdateRequest(BaseModel):
    surface: Optional[Literal["CLAY", "GRASS", "HARD", "CARPET"]] = None
    match_format: Optional[Literal["BEST_OF_1", "BEST_OF_3"]] = None
    third_set_rule: Optional[Literal["FULL_ADVANTAGE", "SUPER_TIE_BREAK_10", "SHORT_DECISIVE_SET"]] = None
    opponent: Optional[str] = None
    competition_type: Optional[str] = None
    tournament: Optional[str] = None
    status: Optional[Literal["ACTIVE", "COMPLETED"]] = None
    result: Optional[Literal["VICTORY", "DEFEAT"]] = None
    score_text: Optional[str] = None
    created_at: Optional[int] = None
    updated_at: Optional[int] = None
```

Tous les champs sont optionnels : seuls les champs fournis (non-`None` dans le payload JSON reçu, via `model_dump(exclude_unset=True)`) sont appliqués — distinction importante entre « champ absent » et « champ explicitement remis à `null` » n'est pas nécessaire ici (aucun des champs ci-dessus n'a besoin d'être remis à `null` depuis l'UI, sauf `opponent`/`tournament`/`competition_type` qui acceptent déjà `None` comme valeur légitime ; ces trois-là seront donc traités séparément avec un sentinel si le besoin de les vider apparaît — hors scope V1, l'UI ne permet pas aujourd'hui de vider l'adversaire).

**`app/features/sessions/repository.py`** — nouvelles méthodes :

```python
async def update(self, session_id: int, request: SessionUpdateRequest) -> SessionModel | None:
    session = await self.get_by_id(session_id)
    if session is None:
        return None
    for field, value in request.model_dump(exclude_unset=True).items():
        setattr(session, field, value)
    await self.db.flush()
    return session

async def delete(self, session_id: int) -> bool:
    session = await self.get_by_id(session_id)
    if session is None:
        return False
    await self.db.delete(session)
    await self.db.flush()
    return True
```

**`app/features/sessions/service.py`** — nouvelles méthodes `update_session` (404 `SESSION_NOT_FOUND` si absent, émet `match.updated`) et `delete_session` (404 si absent, émet `match.deleted` — même convention d'événements que `sync/push`).

**`app/api/v1/sessions.py`** — nouvelles routes :

```python
@router.patch("/{session_id}", response_model=SessionResponse)
async def update_session(session_id: int, request: SessionUpdateRequest, service=Depends(get_session_service)):
    return await service.update_session(session_id, request)

@router.delete("/{session_id}", status_code=204)
async def delete_session(session_id: int, service=Depends(get_session_service)):
    await service.delete_session(session_id)
```

Pas de migration Alembic (aucun changement de modèle, seulement de nouvelles opérations sur les colonnes existantes).

### Frontend — responsive

- **`app/dashboard/layout.tsx` / `Sidebar.tsx`** : sous 640px, la sidebar latérale est masquée et remplacée par une barre de navigation fixe en bas de l'écran (`position: fixed; bottom: 0`), 3 icônes + labels courts (Dashboard / Console / Historique). Entre 640–1024px, sidebar conservée mais compacte.
- **`DashboardView.module.css`** : grille KPI (`kpiGrid`) : 4 colonnes desktop → 2 colonnes tablette → 1 colonne mobile. `middleGrid` (graphique + surface breakdown) : 2 colonnes → empilé sous 1024px.
- **`RecentMatchesTable` / `HistoryView`** : le tableau à colonnes fixes ne peut pas rétrécir sans tronquer le texte. Sous 640px, chaque ligne (`.row`) devient une carte empilée : ligne du haut (date + badge résultat), titre (adversaire), ligne du bas (surface + score). Basculement purement CSS (`display: table-row` → `display: block` via media query), pas de composant React séparé.
- **`ConsoleScreen.module.css`** (grille 3 colonnes) : empilée verticalement sous 1024px — ordre : score (en haut, `position: sticky`), grille de boutons de points, puis déroulé des points (`PointTrail`) en dernier (scrollable, non prioritaire sur mobile).
- **`login/page.module.css`**, `live/[token]` : ajustements mineurs de padding/largeur max pour éviter tout débordement horizontal, sans restructuration.
- Aucun changement de structure HTML/React n'est nécessaire pour le responsive — uniquement des media queries CSS Modules, sauf pour la sidebar (nouveau composant `MobileTabBar` rendu conditionnellement à côté de `Sidebar`, les deux pilotés en CSS par les mêmes breakpoints pour éviter un flash/hydration mismatch).

### Frontend — historique (édition/suppression)

- **`web/lib/api.ts`** : nouvelles fonctions `updateSession(token, sessionId, patch)` (`PATCH /api/v1/sessions/{id}`) et `deleteSession(token, sessionId)` (`DELETE /api/v1/sessions/{id}`), même convention que les fonctions existantes (mapping snake_case ↔ camelCase, gestion `UnauthorizedError`).
- **Routes proxy Next.js** (comme pour `console/sessions/*`) : `app/api/console/sessions/[sessionId]/route.ts` avec handlers `PATCH` et `DELETE`, qui relaient vers le backend avec le cookie de session (le token JWT ne doit jamais être exposé au client — même pattern que l'existant).
- **`app/dashboard/history/page.tsx`** (composant serveur) : lit les sessions via `getSessions`, filtre `sessionType === "MATCH"`, trie par date desc, passe la liste complète à `HistoryView`.
- **`components/history/HistoryView.tsx`** (client) : pagination client (20/page, état local `page`), rend une liste de `HistoryRow`. Chaque ligne : infos du match + boutons **Modifier** / **Supprimer**.
  - **Supprimer** : `window.confirm`-like modal de confirmation légère (composant, pas `window.confirm` natif pour rester cohérent avec le design system), puis appel `deleteSession` + retrait optimiste de la ligne (`router.refresh()` en fallback si l'appel échoue).
  - **Modifier** : ouvre `MatchEditForm` (inline sous la ligne ou modal — inline, cohérent avec le pattern déjà utilisé par `ConsoleSelectionView`/`ScoreSeedForm` pour l'édition en place). Champs pré-remplis : adversaire, surface, date, règle 3ᵉ set, format, **et score par set** (réutilise le sous-composant `SetScoreInputs` décrit ci-dessous), résultat recalculé automatiquement à partir des sets modifiés. Soumission → `updateSession` avec uniquement les champs modifiés.
- **`Sidebar.tsx`** / **`MobileTabBar`** : ajout de l'item « Historique » → `/dashboard/history`.

### Frontend — saisie rétroactive

- **`components/console/NewMatchForm.tsx`** : ajout d'un toggle en tête de formulaire (deux boutons radio stylés) : `mode: "LIVE" | "PAST"`.
  - `mode === "LIVE"` : comportement actuel inchangé (POST puis redirection vers `/dashboard/console/{id}`).
  - `mode === "PAST"` : affiche en plus, sous les champs existants, le sous-composant **`SetScoreInputs`** — liste de sets (2 par défaut, bouton « Ajouter un 3ᵉ set », visible seulement si `matchFormat === "BEST_OF_3"`), chaque set = 2 `<input type="number">` (jeux moi / jeux adversaire). Pas de validation de cohérence tennis (un set à 9-7 est accepté tel quel) — validation minimale : au moins un set rempli avec deux valeurs numériques ≥ 0.
  - Soumission en mode `PAST` :
    1. `POST /api/console/sessions` (existant, inchangé) → session créée `ACTIVE`.
    2. Calcul local : `setsWonSelf = sets.filter(s => s.self > s.opponent).length`, idem `setsWonOpponent` ; `result = setsWonSelf > setsWonOpponent ? "VICTORY" : "DEFEAT"` ; `scoreText = sets.map(s => \`${s.self}-${s.opponent}\`).join(" · ")`.
    3. `PATCH /api/console/sessions/{id}` avec `{ status: "COMPLETED", result, scoreText, updatedAt: createdAt }` (nouvelle route proxy créée pour l'historique, réutilisée ici).
    4. Redirection vers `/dashboard` (pas vers la console live — rien à saisir point par point).
  - Le composant `SetScoreInputs` est partagé entre `NewMatchForm` (mode `PAST`) et `MatchEditForm` (historique) — même logique de saisie de sets dans les deux cas.

## Gestion des erreurs et cas limites

- **`PATCH`/`DELETE` sur session inexistante** : 404 `SESSION_NOT_FOUND`, le frontend affiche un message d'erreur inline et rafraîchit la liste (le match a pu être supprimé depuis un autre onglet).
- **Suppression** : pas de confirmation email/2FA (mono-utilisateur), juste une confirmation UI in-app pour éviter un clic accidentel.
- **Édition d'un match dont le résultat change** (ex: correction 6-4/4-6/6-2 → victoire au lieu de défaite) : le recalcul de `result`/`scoreText` à partir des sets modifiés est systématique côté `MatchEditForm`, jamais laissé en désaccord avec les sets affichés.
- **Sets vides ou incohérents en saisie rétroactive** : aucune session n'est corrompue si l'utilisateur se trompe — il pourra corriger ensuite via l'édition (historique).
- **Aucun match dans l'historique** : état vide sobre, cohérent avec `RecentMatchesTable` (« Pas encore de match »).
- **Pagination** : dernière page potentiellement incomplète (comportement naturel), pas de page vide affichée si `matches.length === 0`.
- **Responsive / hydration** : sidebar et bottom tab bar sont toutes deux rendues côté serveur et basculées uniquement par CSS (`display: none` selon breakpoint) pour éviter tout flash ou mismatch d'hydratation lié à la détection de la largeur d'écran en JS.

## Tests

- **Backend** :
  - `PATCH /sessions/{id}` : 200 avec mise à jour partielle (un seul champ modifié, les autres inchangés) ; 404 si session inexistante ; 401 sans JWT.
  - `DELETE /sessions/{id}` : 204 + vérification que la session n'est plus retournée par `GET /sessions` ; 404 si déjà supprimée ; 401 sans JWT.
- **Frontend (`lib/api.ts`)** : tests unitaires `updateSession`/`deleteSession` (mapping des champs, gestion 401/erreurs).
- **`SetScoreInputs`** : calcul de `result`/`scoreText` à partir de sets (cas victoire 2-0, victoire 2-1, défaite, set unique en `BEST_OF_1`).
- **`NewMatchForm`** : test du toggle `LIVE`/`PAST`, soumission en mode `PAST` (mock `fetch` : vérifie l'appel `POST` puis `PATCH` avec `updatedAt === createdAt`).
- **`HistoryView`** : pagination (nombre de pages, contenu de chaque page), suppression (appel + retrait de la ligne), édition (pré-remplissage du formulaire, soumission avec champs partiels uniquement).
- **Responsive** : pas de test automatisé de rendu visuel (hors outillage actuel) — vérification manuelle sur les 3 breakpoints après implémentation (dashboard, console, historique, login, live).

## Hors scope (YAGNI pour cette itération)

- Édition/suppression des séances d'entraînement (`session_type = TRAINING`).
- Pagination côté serveur pour `GET /sessions` (volume mono-utilisateur, pagination client suffisante).
- Validation de cohérence des scores de sets (empêcher un 9-7 ou un set à 2 jeux d'écart insuffisant).
- Reconstitution point par point d'un match saisi rétroactivement (juste le score final, décision validée avec l'utilisateur).
- Historisation/audit des modifications (qui a changé quoi, quand) — pas de besoin exprimé, app mono-utilisateur.
- Undo de suppression (corbeille) — la confirmation UI avant suppression est jugée suffisante.
- Menu burger / alternative de navigation mobile — bottom tab bar retenue directement.
