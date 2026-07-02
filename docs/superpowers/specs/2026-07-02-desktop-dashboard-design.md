# Tableau de bord desktop (mode avancé)

**Date** : 2026-07-02
**Statut** : Validé, prêt pour planification d'implémentation

## Contexte et objectif

Le design system « Broadcast » (`design/README.md`) prévoit une **interface web desktop** en mode avancé, en complément du téléphone/montre : un tableau de bord de consultation (stats, historique récent) et une console de saisie point par point. Ce document couvre uniquement le **tableau de bord** — premier des deux sous-projets, la console de saisie point par point étant traitée séparément (spec dédiée à venir).

La maquette de référence est `design/SecondServe Web.dc.html` (première fenêtre, « TABLEAU DE BORD »). Le projet `web/` (Next.js) existe déjà pour la page publique de suivi live (`docs/superpowers/specs/2026-07-01-live-score-sharing-design.md`), qui anticipait explicitement cette extension.

## Décisions validées

- **Projet** : extension du `web/` existant (pas de nouveau projet Next.js) — un seul déploiement/service systemd, tokens et fonts déjà en place.
- **Authentification** : réutilisation du flow Google Sign-In existant côté backend (`POST /auth/init`), mais initié depuis le navigateur via Google Identity Services JS. Le JWT SecondServe est géré **entièrement côté serveur Next.js** (cookie httpOnly), jamais exposé au JS navigateur. Aucun changement CORS backend nécessaire (contrairement aux endpoints publics `live`, ces appels serveur→serveur ne partent jamais du navigateur).
- **Backend** : ajout d'un unique endpoint `GET /sessions` (le repository `get_all()` existe déjà, non exposé). Pas d'endpoint d'agrégation dédié — les KPI sont calculés côté Next.js à partir de la liste complète (volume mono-utilisateur, sans enjeu de perf).
- **Portée V1** : tableau de bord en lecture seule uniquement. Pas de bouton « + Nouveau match » (créer/saisir un match depuis le desktop appartient au sous-projet console de saisie). Pas de page Historique complète avec filtres — reportée à un sous-projet futur si le besoin se confirme.
- **Table « Derniers matchs »** : affiche les 8 premiers matchs dans un conteneur à hauteur fixe avec scroll interne (`overflow-y: auto`) pour atteindre les matchs suivants, sans agrandir la carte ni pousser le reste de la page. Pas de lien « Tout voir ».
- **Dark mode** : inclus dès cette V1 (pas différé). Détection automatique de la préférence système (`prefers-color-scheme`) au premier chargement + switch manuel qui écrase ce choix et le persiste (`localStorage`). Palette dark = réutilisation telle quelle de la palette **DARK** déjà définie dans `design/README.md` (mobile/montre), pour cohérence du design system.

## Architecture

### Backend — extension de la feature `sessions`

- **Nouvelle route** : `GET /api/v1/sessions` (protégée JWT, comme les autres routes de la feature), retourne `SessionsResponse` (`items: SessionResponse[]`, `total: int`) — schéma déjà défini dans `schemas.py`, non exposé actuellement. Nouvelle méthode `SessionService.list_sessions()` qui délègue à `SessionRepository.get_all()` (existe déjà, trié par `created_at desc`).
- Pas de nouveau modèle, pas de migration Alembic.

### Frontend — `web/`, nouvelles routes authentifiées

**Auth** :
- `app/login/page.tsx` : bouton Google Identity Services JS. Au succès, envoie le `credential` (Google ID token) à `POST /api/auth/callback` (route handler Next.js).
- `app/api/auth/callback/route.ts` : appelle `POST {API_BASE_URL}/auth/init` avec le token, reçoit le JWT SecondServe, le pose dans un cookie `ss_session` (`httpOnly`, `secure`, `sameSite=lax`, expiration alignée sur celle du JWT — 30 jours), redirige vers `/dashboard`.
- `middleware.ts` : intercepte `/dashboard`, vérifie la présence du cookie ; absent → redirection `/login`. La validité du JWT (expiration/signature) est vérifiée en aval par le backend à chaque appel — si le backend renvoie `401`, la page serveur redirige aussi vers `/login`.
- `app/logout/route.ts` (ou action serveur) : supprime le cookie, redirige vers `/login`.

**Dashboard** :
- `app/dashboard/page.tsx` (composant serveur) : lit le cookie, appelle `GET {API_BASE_URL}/sessions` avec `Authorization: Bearer <jwt>`, calcule les agrégats côté serveur (voir ci-dessous), passe les données aux composants d'affichage.
- Calculs dérivés de la liste de sessions (nouveau module `web/lib/stats.ts`) :
  - **Win rate global** + tendance vs mois précédent : sur les sessions `session_type=MATCH` avec `status=COMPLETED` (implique `result` non nul).
  - **Victoires · Défaites** + nombre de matchs terminés : idem.
  - **Séquence active** : résultat du dernier match en remontant tant que le résultat est identique (V ou D), sur les matchs `COMPLETED` triés par date.
  - **Temps de jeu** : somme de `updated_at - created_at` sur **toutes** les sessions `status=COMPLETED` (matchs + entraînements), converti en heures ; nombre de sessions = total de ces sessions `COMPLETED`.
  - **Win rate par mois** (5 derniers mois, fidèle au décompte du mockup) et **par surface** : agrégation simple sur `result`/`surface`/`created_at`.
- Composants (`web/components/dashboard/`) : `KpiCard`, `MonthlyWinRateChart`, `SurfaceBreakdown`, `RecentMatchesTable` (avec conteneur à hauteur fixe + scroll interne), `ThemeToggle`, `Sidebar`.

**Thème clair/sombre** :
- Script inline dans `app/layout.tsx` (`<head>`, exécuté avant hydratation) : lit `localStorage.getItem('ss-theme')` ; si absent, utilise `window.matchMedia('(prefers-color-scheme: dark)')` ; pose `data-theme="light"|"dark"` sur `<html>`. Évite le flash de mauvais thème.
- Migration de `web/lib/design-tokens.ts` (constantes JS) vers des **variables CSS** définies dans `globals.css`, sous `:root` (valeurs light) et `[data-theme="dark"]` (valeurs dark, reprises de `design/README.md`). Les composants CSS Modules consomment `var(--color-*)`.
- `ThemeToggle` (composant client) : au clic, bascule `data-theme` sur `<html>` et écrit le choix explicite dans `localStorage`.

## Gestion des erreurs et cas limites

- **Pas de cookie / cookie invalide** sur `/dashboard` → redirection `/login`.
- **JWT expiré** (401 du backend lors du fetch) → redirection `/login` (le cookie est supprimé au passage).
- **Backend indisponible** → page d'erreur générique avec bouton « Réessayer ».
- **Aucun match joué** → cartes KPI affichent un état vide sobre (« Pas encore de match ») plutôt que des graphes à 0 ou des divisions par zéro dans les calculs de taux.
- **Un seul mois de données** → le graphe « win rate par mois » n'affiche que les mois disponibles (pas de mois vides fictifs).
- **Table de moins de 8 matchs** → pas de scroll (contenu plus court que le conteneur), comportement naturel de `overflow-y: auto`.

## Tests

- **Backend** : `GET /sessions` — auth requise (401 sans JWT), tri par date décroissante, réponse conforme à `SessionsResponse`.
- **Frontend (`web/lib/stats.ts`)** : tests unitaires des calculs d'agrégats à partir de fixtures de sessions — cas limites : aucune session, un seul match, que des entraînements (pas de matchs), séquence en cours vs rompue, calcul par surface avec une seule surface représentée.
- **Middleware** : redirection si cookie absent ; laisse passer si présent (la validité fine du JWT est testée côté page/fetch).
- **Thème** : test du script d'init (mock `matchMedia` + `localStorage`) — priorité au choix explicite stocké, sinon système.

## Hors scope (YAGNI pour cette itération)

- Console de saisie point par point (sous-projet séparé).
- Page Historique complète avec filtres/groupement par mois — le tableau de bord ne montre que les 8 derniers matchs avec scroll interne.
- Bouton « + Nouveau match » / création de session depuis le desktop.
- Endpoint d'agrégation backend dédié aux stats (calcul côté client suffisant au volume mono-utilisateur).
- Détail de session cliquable depuis la table.
