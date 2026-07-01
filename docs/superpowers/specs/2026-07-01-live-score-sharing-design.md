# Partage de lien live pour suivi de match à distance

**Date** : 2026-07-01
**Statut** : Validé, prêt pour planification d'implémentation

## Contexte et objectif

Un joueur en match veut pouvoir partager un lien unique à un proche (ex. conjoint·e) qui ne peut pas être présent, pour que celui-ci suive l'évolution du score en direct depuis un navigateur, sans compte ni installation. Le lien doit se mettre à jour quasi instantanément à chaque point marqué, pendant toute la durée du match, et rester consultable un moment après la fin du match pour voir le résultat final.

Cette fonctionnalité était déjà anticipée dans la vision produit du design system Broadcast (`design/README.md`) : « peut partager un lien public pour que ses proches suivent le score en temps réel ». Le mockup `design/SecondServe Public.dc.html` (variante « 4B ») sert de référence visuelle haute-fidélité pour la page publique.

## Décisions validées

- **Backend** : réutilisation du backend FastAPI/VPS existant (pas de service tiers type Firebase/Supabase).
- **Transport temps réel** : Server-Sent Events (SSE), unidirectionnel serveur → navigateur. Contrainte : un seul process uvicorn (pas de multi-worker), cohérent avec le déploiement actuel à usage personnel.
- **Création du lien** : explicite, via un bouton « Partager » pendant le match (pas de génération automatique silencieuse).
- **Durée de vie** : le lien reste valide pendant le match **et** après (pour consulter le score final), puis expire — rétention par défaut **48h** après la fin du match, ajustable.
- **Format du lien** : token aléatoire non-devinable (`secrets.token_urlsafe(16)`), pas de slug lisible basé sur des noms — le score contient des informations privées (adversaire, lieu, etc.).
- **Frontend de la page publique** : application **Next.js** dédiée (nouveau projet `web/`), auto-hébergée sur le VPS (systemd + nouveau hostname public via le tunnel Cloudflare existant), plutôt qu'une page servie directement par FastAPI. Ce choix prépare l'infrastructure pour le futur mode desktop avancé mentionné dans le design system, mais le scope de cette spec se limite strictement à la page de suivi live.
- **Déroulé du set (page publique)** : affiche l'initiale du joueur qui a remporté chaque point (ex. `B` / `M`), pas un indicateur `V`/`E` relatif à un joueur local — cohérent avec le fait que la page publique montre les vrais prénoms des deux joueurs (jamais « Vous »).

## Architecture

### Backend — nouvelle feature `live_sharing`

Structure alignée sur les features existantes (`app/features/<nom>/{models,schemas,service}.py` + router dans `app/api/v1/`).

**Table `match_shares`** :

| Colonne | Type | Rôle |
|---|---|---|
| `id` | PK autoincrement | |
| `token` | string, unique, indexé | `secrets.token_urlsafe(16)` |
| `session_id` | FK `sessions.id`, unique | un seul lien actif par match ; retaper « Partager » renvoie le même lien |
| `created_at` | epoch ms | |
| `expires_at` | epoch ms, nullable | `null` tant que le match est en cours ; posé à `now + 48h` quand `isMatchOver` passe à `true` |
| `score_snapshot` | JSON (texte) | dernier état connu : score complet (`MatchScoreDto`-like) + contexte (surface, tournoi, adversaire, format, heure de début) |

**Diffusion temps réel** : registre en mémoire `dict[token, list[asyncio.Queue]]`. Chaque connexion SSE s'abonne à la queue de son token. Chaque mise à jour de score publie l'événement dans les queues correspondantes. `score_snapshot` en base est la source de vérité pour le premier chargement et pour la résilience en cas de redémarrage du process (seuls les abonnés SSE en mémoire sont perdus ; ils se reconnectent automatiquement et repartent avec l'état persisté).

**Endpoints** :

| Endpoint | Auth | Rôle |
|---|---|---|
| `POST /api/v1/live/shares` | JWT | Crée (ou retourne, idempotent) le lien pour `session_id` |
| `POST /api/v1/live/sessions/{session_id}/score` | JWT | Pousse l'état complet du score à chaque point (pas un delta — auto-réparant en cas de perte réseau ponctuelle) |
| `GET /api/v1/live/{token}` | Public (CORS activé pour l'origine Next.js) | État courant — utilisé par le composant serveur Next.js pour le premier rendu et les meta tags Open Graph |
| `GET /api/v1/live/{token}/stream` | Public (CORS) | SSE — émet l'état courant immédiatement à la connexion, puis chaque mise à jour ultérieure |

Réponses d'erreur sur les endpoints publics : `404` si token inconnu, `410` si `expires_at` dépassé.

**Nettoyage** : job planifié quotidien (même mécanisme que le scheduler existant `app/features/notifications/scheduler.py`) qui supprime les lignes de `match_shares` dont `expires_at` est dépassé depuis un moment (garde la table propre ; le `410` à la lecture gère déjà le cas limite avant le passage du job).

**CORS** : activé sur le backend pour l'origine du domaine Next.js — nécessaire uniquement pour les endpoints publics appelés depuis un navigateur. Les endpoints authentifiés continuent d'être appelés par l'app Android (hors scope CORS).

### Frontend — nouveau projet `web/` (Next.js, App Router)

- Route `app/live/[token]/page.tsx`.
- Composant serveur : fetch `GET /api/v1/live/{token}` au moment de la requête (pas de cache) pour un premier rendu sans flash de contenu vide, et pour générer les meta tags Open Graph dynamiques (ex. « Benjamin mène 5-4 · Set 2 ») — pertinent puisque le lien est destiné à être partagé par SMS/WhatsApp.
- Composant client : ouvre un `EventSource` vers `GET /api/v1/live/{token}/stream` pour les mises à jour live. Si aucun événement reçu depuis >15s, affiche un indicateur discret « reconnexion… » (l'`EventSource` retente nativement la connexion).
- États d'erreur : token inconnu (`404`) → page « Lien invalide » ; token expiré (`410`) → page « Ce match n'est plus disponible ».

**Contenu de la page** (fidèle au mockup 4B) :
- Badge « EN DIRECT » (pulsant) / « TERMINÉ » selon `isMatchOver`.
- Ligne de contexte : surface, tournoi/compétition, durée écoulée (calculée depuis l'heure de début du match).
- Tableau score façon broadcast : prénom réel des deux joueurs (jamais « Vous »), sets (S1/S2), jeux, points, indicateur de service.
- Déroulé du set courant : initiale du joueur ayant remporté chaque point.
- Footer « Suivi propulsé par SecondServe · se met à jour automatiquement ».

**Déploiement** : build Next.js en mode `standalone`, process Node géré par un nouveau service systemd (`secondserve-web.service`, même schéma que `secondserve-backend.service`), nouveau Public Hostname Cloudflare pointant vers ce process (domaine apex, cohérent avec `secondserve.app/live/{token}` du mockup), à côté du hostname `api.secondserve.app` existant pour le backend.

### Android — app existante

- **Bouton « Partager »** dans le header de `MatchScreen`, à côté du bouton fermer (cf. mockup `SecondServe Match Live`).
- **`LiveShareRepository`** (nouveau, module `:data`) : `createShare(sessionId): AppResult<LiveShareInfo>` appelle `POST /live/shares`. Résultat mis en cache localement (nouvelle colonne `live_share_token`/`live_share_url` sur l'entité session Room, ou table dédiée) pour que retaper « Partager » réutilise le lien existant sans recréer un enregistrement côté serveur.
- **`ShareMatchUseCase`** : orchestre création (ou réutilisation) du lien puis déclenche la feuille de partage système Android (`Intent.ACTION_SEND`) avec l'URL et un texte du type « Suis mon match en direct : {url} ».
- **Poussée du score en direct** : sur chaque émission de `ScoreRepository.latestScore` — si la session courante a un lien actif (flag local) — envoi fire-and-forget vers `POST /live/sessions/{id}/score` avec le `MatchScoreDto` existant (déjà utilisé pour la sync montre↔téléphone via `toDto()`) et le contexte du match. Échec réseau ignoré sans retry : le prochain point renvoie l'état complet et se rattrape automatiquement (idempotence par snapshot complet).
- **Origine montre** : aucune logique spécifique nécessaire — les points saisis sur la montre remontent déjà au téléphone via le Data Layer et alimentent le même `latestScore`, en amont du hook de poussée.
- **Fin de match** : `CloseMatchUseCase` ne change pas ; le dernier point poussé porte déjà `isMatchOver=true`/`matchWinner`, ce qui suffit au backend pour poser `expires_at` et faire basculer la page publique en « Terminé ».

## Gestion des erreurs et cas limites

- **Perte réseau ponctuelle côté téléphone** : poussées silencieusement ignorées, pas de retry ; auto-réparation au point suivant grâce à l'envoi de l'état complet (pas de delta).
- **Redémarrage backend en cours de match** : `score_snapshot` persisté permet aux nouvelles connexions SSE de repartir avec le bon état ; les abonnés en mémoire perdus se reconnectent automatiquement côté navigateur.
- **Plusieurs spectateurs simultanés** : chaque connexion SSE est indépendante ; le registre en mémoire diffuse à toutes les queues abonnées à un token donné.
- **Lien expiré ou inconnu** : `404`/`410` sur les endpoints publics, page dédiée côté Next.js.

## Tests

- **Backend** : création idempotente du lien par session ; persistance + diffusion SSE à la mise à jour ; premier événement SSE = snapshot courant ; `404`/`410` selon expiration ; endpoints publics accessibles sans JWT, endpoints de poussée protégés par JWT.
- **Android** : `ShareMatchUseCase` (création vs réutilisation du lien existant) ; poussée du score déclenchée uniquement si un lien est actif pour la session ; tolérance aux échecs réseau (pas d'exception non gérée, pas de retry).
- **Next.js** : rendu du composant serveur (snapshot + meta OG) ; reconnexion SSE après coupure ; états `404`/`410`.

## Hors scope (YAGNI pour cette itération)

- Mode desktop avancé (analytics, saisie point par point) — le projet `web/` ne contient que la page de suivi live.
- Authentification/PIN pour la page publique — le token non-devinable est jugé suffisant.
- Révocation manuelle d'un lien avant expiration naturelle.
- Interaction retour du spectateur vers le match (raison du choix SSE plutôt que WebSocket).
