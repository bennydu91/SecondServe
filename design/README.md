# Handoff : SecondServe — Refonte UX/UI « Broadcast »

## Vue d'ensemble
SecondServe est une application de scoring de tennis pour joueur amateur/club. L'utilisateur saisit ses points **en direct pendant le match** (montre Wear OS + téléphone Android), consulte ses **statistiques**, reçoit un **coaching IA** (conseils live + synthèses post-match), et peut **partager un lien public** pour que ses proches suivent le score en temps réel. Une **interface Web desktop** ajoute un mode avancé (saisie point par point détaillée, analytics).

Cette refonte remplace l'UI du POC par un langage visuel unique — nom de code **« Broadcast »** (esprit scoreboard TV : chiffres géants, contraste net, un accent néon) — décliné sur 4 surfaces : montre, téléphone, web desktop, page publique.

## À propos des fichiers de design
Les fichiers de ce bundle sont des **références de design réalisées en HTML** — des prototypes qui montrent l'apparence et le comportement voulus, **pas du code de production à copier tel quel**. Ils sont écrits dans un format interne (« Design Component », `.dc.html`) qui s'ouvre dans un navigateur mais n'est pas destiné à être importé dans l'app.

La tâche est de **recréer ces designs dans l'environnement existant du codebase SecondServe** :
- **Android (smartphone + Wear OS)** : Jetpack Compose / Compose for Wear OS (Kotlin) — le repo `bennydu91/SecondServe`, module `android/`, features déjà en place (`feature/match`, `feature/history`, `feature/coaching`, module `wear/`).
- **Web desktop + page publique** : à créer. Choisir le framework adapté au projet (React/Next.js recommandé pour la page publique temps réel + le mode avancé). 

Ne pas livrer le HTML directement — le recréer avec les composants et patterns de chaque plateforme.

## Fidélité
**Haute fidélité (hifi).** Couleurs, typographies, espacements et interactions sont définitifs. Reproduire l'UI au pixel près en utilisant les librairies/patterns de chaque plateforme. Les valeurs exactes (hex, tailles, poids) sont dans « Design Tokens » ci-dessous.

---

## Décisions produit verrouillées
- **Montre — modèle de correction « 2B »** : deux boutons permanents sous le score, `↩ annuler` le dernier point et `⇄ basculer` le dernier point vers l'autre joueur. C'est la priorité #1 de l'utilisateur (corriger un point attribué du mauvais côté doit être imbattable). Le geste de base reste : moitié gauche de l'écran = point pour soi, moitié droite = point adverse.
- **Match live téléphone — variante « 3B » gamecast** : tableau type broadcast (une ligne par joueur : Sets S1/S2, Jeux, Points), déroulé du set jeu par jeu, barre de momentum, carte coaching.
- **Page publique — variante « 4B »** : thème clair épuré (score live + contexte + déroulé). 
- **Label adversaire** : afficher le **nom de l'adversaire** (ex. « Marceau »), jamais « EUX ». Repli « Adversaire » si le nom est inconnu. Le joueur principal est « Vous » / son prénom.

## Thèmes
- **Mobile (téléphone + montre)** : **dark-first** (lisibilité plein soleil, jeu en nuit).
- **Web desktop** : **light-first** avec dark mode disponible (usage bureau/maison).
- **Page publique** : claire.

---

## Écrans / Vues

### 1. Montre Wear OS — Score (montre ronde)
- **But** : saisir les points et les corriger, mains moites, bras levé, plein soleil.
- **Layout** : cadran rond, fond `void #0C0D0F`. Deux demi-zones invisibles gauche/droite (tap = point). Score centré verticalement.
  - Haut : `1 — 0 SETS` (14px, muted).
  - Centre : **JEUX** en Barlow Semi Condensed 800, ~76–82px, joueur actif en `lime #C8FF3D`, adversaire en `text #F2F3F0`. Label « JEUX » 10px tracking 3px.
  - Sous le score : **POINTS** en `data #4EA8FF` 28px (`40 — 30`).
  - Labels latéraux verticaux : « VOUS » (lime) à gauche, nom adversaire (muted) à droite.
- **Correction 2B** : deux ronds de **52×52px** en bas, `panel2 #1B1E23`, bordure `#2f333a`, icônes `↩` et `⇄` en `text`. Cibles tactiles ≥ 48px impératif.
- **États** : hors-ligne = pastille `hot #FF5C7A` + « Hors ligne · sauvegarde locale » (la montre score sans le téléphone, sync à la reconnexion).

### 2. Montre — Démarrage / Fin de match
- **Démarrage** : titre « NOUVEAU MATCH », chips format (1 set / 3 sets), bouton pill lime « Démarrer » (156×48). Reprise possible d'un match lancé sur le téléphone.
- **Fin** : « VICTOIRE » (lime) ou défaite (hot), score sets 56px, bouton « Terminer ».

### 3. Téléphone — Match live (gamecast 3B)
- **Layout** : colonne pleine hauteur, padding 18px, fond `void`.
  - Header : chip `LIVE` (hot + point pulsant) · « Set 2 · 47 min » · bouton fermer rond.
  - **Tableau gamecast** (`panel #131518`, bordure `line #24272D`, radius 18) : ligne d'en-tête (JOUEUR / S1 / S2 / JEUX / PTS, colonnes JEUX en lime, PTS en data), puis une ligne par joueur. Colonne JEUX = 34px Barlow 800 ; PTS = 26px data ; sets = 24px muted. Serveur = point coloré devant le nom.
  - **Déroulé du set** : rangée de cases `V` (lime, fond `rgba(200,255,61,.16)`) / `E` (muted, fond `#1B1E23`), + case pointillée pour le point en cours.
  - **Momentum** : barre horizontale lime/panel2 + pourcentage.
  - **Carte coaching** : `panel`, bordure gauche 3px lime, icône `✦`, label « CONSEIL COACHING » + texte.
- **Prototype interactif** : voir `SecondServe Match Live.dc.html` — logique de scoring de tennis réelle (voir « Logique de scoring » plus bas).

### 4. Téléphone — Accueil
- Header logo + avatar. Titre « Prêt à jouer, {prénom} ? ». Bouton primaire lime « + Nouveau match » (pleine largeur, radius 14, padding 17). Deux tuiles stats (Win rate, série). Liste « Derniers matchs » (voir composant MatchListItem). Carte promo synthèse coaching. Bottom nav 3 onglets.

### 5. Téléphone — Nouveau match
- Sections : SURFACE (chips couleur, sélection = chip pleine couleur surface), FORMAT (1 set / 3 sets, sélection = bordure lime + fond `rgba(200,255,61,.1)`), RÈGLE 3E SET (radios), ADVERSAIRE (input), toggle « Planifier ». Bouton primaire « Démarrer le match ».

### 6. Téléphone — Statistiques
- Carte Win rate global (56px lime + barre comparative). Carte « Par surface » (barres colorées par surface). Deux tuiles (série, volume). Jamais de camembert.

### 7. Téléphone — Historique / Détail / Coaching / Profil
- **Historique** : filtres chips, groupes par mois, MatchListItem (barre couleur surface à gauche, score Barlow, badge V/D). Entraînements = badge indoor `#A97CF0`.
- **Détail de session** : bandeau score géant, stats comparatives (StatBar), ressenti (étoiles lime), analyse post-match (carte coaching).
- **Coaching** : synthèse multi-matchs (carte à léger dégradé vert), bouton « Regénérer », liste des analyses post-match.
- **Profil** : avatar, classement, chips « axes de travail », liste de réglages (dont « Partage & liens publics »).

### 8. Web desktop — Tableau de bord (light)
- **Layout** : sidebar gauche 224px (`surface #FFFFFF`, bordure droite) + zone contenu scroll.
  - Sidebar : logo, nav (item actif = fond `ink #14161A`, texte blanc, pastille lime), profil en bas.
  - Contenu : titre + toggle dark + bouton lime « + Nouveau match ». 4 cartes KPI (radius 14). Graphe « Win rate par mois » (barres, mois courant en lime). Carte « Par surface » (barres couleur surface). Table « Derniers matchs » (colonnes Date/Adversaire/Surface/Score/Résultat, badges).

### 9. Web desktop — Console de saisie point par point
- **Layout** : header match + 3 colonnes : (gauche 340px) ScoreCard light + bouton « Annuler le dernier point » ; (centre) grille de boutons d'attribution avec **contexte** (Ace, Coup gagnant, Faute provoquée, Faute adverse / côté adverse : coup gagnant adverse, ace adverse, ma faute directe, ma double faute) + tuiles stats live ; (droite 288px) déroulé point par point (liste). C'est le mode avancé : saisir la stat fine impossible au geste rapide de la montre.

### 10. Page publique de suivi (4B, claire, lien partageable)
- **But** : un spectateur ouvre `secondserve.app/live/{slug}` sans compte et suit le match.
- **Layout** : fond `paper #F4F4F1`, centré. Chip `EN DIRECT` (hot) + contexte (surface · tournoi · durée). Grand tableau (`surface`, radius 22, ombre douce) : ligne joueur principal surlignée `rgba(200,255,61,.14)`, avatars, colonnes S1/S2/JEUX/POINTS (JEUX 46px, POINTS en data). Déroulé du set (cases V/E). Footer « Suivi propulsé par SecondServe · se met à jour automatiquement ».
- **Temps réel** : le score doit se rafraîchir tout seul (WebSocket / SSE / polling). Contenu visible = score live + contexte + déroulé (pas de stats avancées, pas d'identité du spectateur).

---

## Logique de scoring (référence : SecondServe Match Live.dc.html)
Règles de tennis standard à implémenter côté domaine :
- **Points** : 0 → 15 → 30 → 40. À 40-40 = **égalité** (les deux affichent « 40 »). Avantage : le meneur affiche « AD », l'autre « 40 ». Jeu gagné à **+2 points** après 40.
- **Jeux** : set gagné à **6 jeux avec 2 d'écart**, ou **7-5**. À **6-6** → **tie-break** (points comptés 1, 2, 3… premier à 7 avec 2 d'écart), le vainqueur gagne le set 7-6.
- **Sets** : match en **best-of-3** (premier à 2 sets).
- **Annuler (undo)** : retire le dernier point (pile de snapshots de l'état avant chaque point).
- **Basculer (swap)** : annule le dernier point puis l'attribue à l'autre joueur (= corrige un point du mauvais côté en un geste). C'est le cœur de l'exigence utilisateur.
Le fichier `SecondServe Match Live.dc.html` contient une implémentation JS complète et testée de cette logique (classe `Component`, méthodes `award`, `undo`, `swap`, `ptLabel`) — à porter en Kotlin/TS.

---

## Design Tokens

### Couleurs — DARK (mobile, montre, nuit)
| Rôle | Hex |
|---|---|
| void (fond app) | `#0C0D0F` |
| panel (cards) | `#16181C` (variante élevée `#131518`) |
| panel2 (inputs/élevé) | `#1B1E23` |
| line (bordures) | `#24272D` |
| text | `#F2F3F0` |
| muted | `#8A8F98` |
| faint | `#6B7079` |
| lime (primary / joueur actif) | `#C8FF3D` — texte dessus : `#0C0D0F` |
| hot (live, erreur, défaite) | `#FF5C7A` |
| data (points, stats, liens) | `#4EA8FF` |

### Couleurs — LIGHT (web desktop, jour)
| Rôle | Hex |
|---|---|
| paper (fond) | `#F4F4F1` |
| surface (cards) | `#FFFFFF` |
| surface2 (élevé/inputs) | `#FBFBF9` |
| line | `#E4E5E2` |
| ink (text) | `#14161A` |
| muted | `#6A6F78` |
| faint | `#9AA0A8` |
| lime | `#C8FF3D` (en fill uniquement ; texte dessus = ink) |
| hot | `#E63958` |
| data (accent texte sur clair) | `#1F6FE5` |

### Couleurs de surface de court (chips, accents contextuels)
| Surface | dark | light |
|---|---|---|
| Terre battue | `#E0703F` | `#C85A2C` |
| Dur | `#3E8EF0` | `#2C6FD8` |
| Gazon | `#4FB477` | `#3E9E66` |
| Indoor | `#A97CF0` | `#8A5FD6` |

### Badges résultat
- Victoire (dark) : fond `rgba(200,255,61,.14)`, texte `#C8FF3D`. (light) : fond `rgba(31,111,229,.1)`, texte `#1F6FE5`.
- Défaite (dark) : fond `rgba(255,92,122,.14)`, texte `#FF5C7A`. (light) : fond `rgba(230,57,88,.1)`, texte `#E63958`.

### Typographie
- **Barlow Semi Condensed** (500/600/700/800) — scores, chiffres, labels. **Toujours `font-feature-settings: 'tnum'`** sur les chiffres de score (pas de décalage). Labels : 12px, tracking +2px, uppercase.
  - Jeux (la star) : ~74–108px / 800 selon l'écran. Sets & Points secondaires : 24–42px / 800.
- **Space Grotesk** (400/500/600/700) — interface, titres, corps.
  - Titre display 32/700 · Section 20/600 · Corps 16/500 · Secondaire 14/400 · Métadonnée 12/500.
- Les deux sont sur Google Fonts.

### Hiérarchie tennis (règle d'or)
Sets (petit, change rarement) → **Jeux (XXL, l'élément dominant)** → Points (accent data/hot, change à chaque échange). Le joueur actif est toujours signalé en lime.

### Espacement (base 4px)
`4` (xs) · `8` (sm) · `12` (md) · `16` (lg) · `24` (xl) · `32` (xxl). Cible tactile **≥ 48px** (impératif montre + mobile).

### Rayons
card `16` · input `12` · small `8` · chip/pill `100`. (Cartes plus grandes / tableaux : 18–22.)

### Ombres
Légères et rares. Cards dark : pas d'ombre (contraste porté par la couleur). Cards light élevées : `0 20px 50px -30px rgba(20,22,26,.3)`. Cadran montre : `0 20px 50px -18px rgba(0,0,0,.5)`.

### Animation
- Point live (LIVE dot) : pulse 1.4s (`opacity 1 → .2 → 1`).
- Carte coaching : slide-in depuis le bas, discret. Animations sobres uniquement, jamais intrusives.

### Composants récurrents
- **ScoreCard** : bloc Sets / Jeux (XXL) / Points empilés, séparateurs `line`, joueur actif lime.
- **MatchListItem** : barre verticale 4px couleur-surface + score Barlow + méta + badge V/D.
- **StatBar** : barre comparative (joueur en data/lime à gauche, adversaire en neutre à droite). Jamais de camembert.
- **Chip surface** : pill couleur de surface. **Chip LIVE** : hot + point pulsant.
- **Bottom nav** (mobile) : 3 destinations (Accueil, Stats, Profil), onglet actif lime.
- Règle : **un seul bouton primaire plein (lime) par écran**.

---

## Assets
Aucune image bitmap requise. Icônes : jeu d'icônes ligne standard de chaque plateforme (Material Symbols côté Android, Lucide/équivalent côté web). Glyphes utilisés dans les maquettes (`✦ ↩ ⇄ ✕ ‹ ›`) = à remplacer par les icônes du système cible. Logo = carré arrondi `ink` avec point `lime` centré (recréable en vecteur, pas de fichier fourni). Polices via Google Fonts (Barlow Semi Condensed, Space Grotesk).

## Fichiers de ce bundle
- `SecondServe Design System.dc.html` — référence des tokens, couleurs, typo, espacements, composants (light + dark).
- `SecondServe Watch.dc.html` — écrans montre (score + correction 2B, démarrage, fin, hors-ligne).
- `SecondServe Mobile.dc.html` — écrans téléphone (Match live gamecast, Accueil, Nouveau match, Stats, Historique, Détail, Coaching, Profil).
- `SecondServe Match Live.dc.html` — **prototype interactif** du Match live avec logique de scoring réelle (à porter).
- `SecondServe Web.dc.html` — web desktop (tableau de bord + console de saisie point par point).
- `SecondServe Public.dc.html` — page publique de suivi (lien partageable, temps réel).
- `android-frame.jsx`, `browser-window.jsx`, `support.js` — utilitaires du format de maquette (cadres d'appareil / navigateur, runtime). **Non pertinents pour l'implémentation** — présents seulement pour que les `.dc.html` s'ouvrent dans un navigateur.

### Ouvrir les maquettes
Ouvrir n'importe quel `.dc.html` dans un navigateur (double-clic). Le fichier Match Live est interactif : cliquer « Point Benjamin » / « Point Marceau », puis tester « Annuler » et « Basculer ».
