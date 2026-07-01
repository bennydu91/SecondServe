# SecondServe — Design System « Broadcast »

## Identité visuelle

**Langage** : scoreboard TV — fond noir, chiffres géants, un seul accent néon (lime).
**Origine** : handoff design importé depuis claude.ai/design (projet *SecondServe UX/UI redesign*),
implémenté dans `core/ui/src/main/kotlin/com/secondserve/core/ui/theme/`.
**Principe directeur** : dark mode first (mobile + montre), plein soleil first, chiffres first.

---

## Couleurs

Tokens définis dans `BroadcastColors.kt`, exposés via `LocalBroadcastColors.current`. Le
`ColorScheme` Material 3 (`Theme.kt`) est mappé dessus pour les composants standards, mais les
écrans doivent lire `LocalBroadcastColors.current` directement pour les valeurs exactes (lime,
data, surfaces de court...) que M3 ne modélise pas nativement.

| Rôle | Token | Hex | Usage |
|------|-------|-----|-------|
| Void | `void` | `#0C0D0F` | Fond de l'app |
| Panel | `panel` | `#16181C` | Avatar/chip/éléments de chrome |
| Panel élevé | `panelHigh` | `#131518` | Fond des cards principales |
| Line | `line` | `#24272D` | Bordures |
| Text | `text` | `#F2F3F0` | Texte principal |
| Muted | `muted` | `#8A8F98` | Texte secondaire |
| Faint | `faint` | `#6B7079` | Labels de section (uppercase, tracking) |
| Lime | `lime` / `onLime` | `#C8FF3D` / `#0C0D0F` | Accent primaire unique, joueur actif |
| Hot | `hot` | `#FF5C7A` | Live, erreur, défaite |
| Data | `data` | `#4EA8FF` | Points, liens, stats |
| Clay / Hard / Grass / Indoor | `clay`/`hard`/`grass`/`indoor` | `#E0703F`/`#3E8EF0`/`#4FB477`/`#A97CF0` | Accent par surface de court |

**Pas de dynamic color, pas de variante light mobile** — le thème est forcé en dark Broadcast
(`SecondServeTheme` dans `Theme.kt`), le handoff ne définissant aucun token clair pour le
téléphone/la montre (réservé au web desktop, hors scope Android).

---

## Typographie

Deux familles (polices statiques bundlées dans `core/ui/src/main/res/font/`, téléchargées depuis
Google Fonts) :
- **Barlow Semi Condensed** (500/600/700/800) — scores, JEUX/SETS/PTS, labels de section uppercase.
- **Space Grotesk** (400/500/600/700) — interface, titres, corps de texte.

**Règle scores** : toujours `fontFeatureSettings = "tnum"` sur les chiffres pour éviter les
décalages visuels lors des changements de score.

### Échelle d'usage dans l'app (`SecondServeTypography`, `Typography.kt`)

| Style MD3 | Police | Taille | Usage |
|-----------|--------|--------|-------|
| `displayLarge` | Barlow ExtraBold | 80sp | Jamais utilisé tel quel (référence d'échelle) |
| `displayMedium`/`displaySmall` | Barlow ExtraBold/Bold | 56/40sp | Scores géants (détail session, gamecast) |
| `headlineLarge` | Barlow Bold | 32sp | JEUX (l'élément dominant du gamecast) |
| `headlineMedium`/`headlineSmall` | Space Grotesk SemiBold | 26/22sp | Titres d'écran |
| `titleLarge`/`titleMedium`/`titleSmall` | Space Grotesk | 18/16/14sp | Sections, boutons |
| `bodyLarge`/`bodyMedium`/`bodySmall` | Space Grotesk | 16/14/12sp | Contenu, cartes, listes |
| `labelMedium`/`labelSmall` | Barlow Bold, tracking 2sp | 12/10sp | Labels uppercase, badges |

### Hierarchy tennis
```
Sets :   1 — 0      → headlineLarge (change rarement)
Jeux :   5 — 4      → headlineMedium (change toutes les minutes)
Points : 40 — 30    → titleLarge + couleur accent (change à chaque point)
```

---

## Espacement

Base : **4dp**. Toutes les valeurs sont des multiples de 4.

| Token | Valeur | Usage |
|-------|--------|-------|
| `spacing.xs` | 4dp | Espacement minimal (icon/label) |
| `spacing.sm` | 8dp | Espacement dans une card |
| `spacing.md` | 12dp | Espacement inter-éléments |
| `spacing.lg` | 16dp | Padding de page standard |
| `spacing.xl` | 24dp | Espacement entre sections |
| `spacing.xxl` | 32dp | Grandes zones de respiration |
| `spacing.minTouchTarget` | 48dp | Cible tactile minimale (pas un espacement — nommage à part) |

**Écart connu** : le padding horizontal de page (18dp, ex. `HomeScreen`, `MatchScreen`,
`NewMatchScreen`) et le padding interne des cards de section (20dp, `BroadcastSectionCard`) ne
correspondent à aucun token de l'échelle 4dp. Ce sont des valeurs héritées du handoff HTML
(`SecondServe Design System.dc.html`, padding 18-26px) volontairement laissées telles quelles
plutôt que forcées dans un token voisin (16 ou 24) qui changerait la densité visuelle des écrans.
Si vous introduisez un nouvel écran, réutilisez ces deux valeurs constantes (18dp page, 20dp card)
par cohérence — n'inventez pas une troisième valeur proche.

### Rayons (`BroadcastRadius.kt`)

| Token | Valeur | Usage |
|-------|--------|-------|
| `radius.small` | 8dp | Petits éléments (badge, icône ronde) |
| `radius.input` | 12dp | Champs de texte, boutons, chips carrées |
| `radius.card` | 16dp | Cards standard (`BroadcastSectionCard`, `ProfileHeaderCard`) |
| `radius.table` | 18dp | Tableaux/cards larges (`GamecastTable`, bandeau score) |
| `radius.pill` | 100dp | Chips et badges arrondis (`SurfaceChip`, `LiveChip`, `ResultBadge`) |

---

## Composants partagés (`core/ui/src/main/kotlin/com/secondserve/core/ui/components/`)

### GamecastTable
- Tableau broadcast (JOUEUR / S1 / S2 / JEUX / PTS), une `Row` par joueur.
- JEUX en Barlow 34sp (lime si meneur du set), PTS en Barlow 26sp `data`, sets en Barlow 24sp `faint`.
- Pas d'indicateur de serveur (aucune source fiable sans changer le protocole watch↔téléphone).

### MatchListItem
- Barre latérale 4dp couleur-surface (`forSurfaceKey`) + score Barlow ExtraBold 22sp + méta + badge V/D.
- Utilisé par Accueil (derniers matchs) et Historique (variante entraînement : badge `indoor`).

### StatBar / DualStatBar
- Barre comparative horizontale. Jamais de camembert.
- `StatBar` : un seul segment proportionnel (win rate, par surface).
- `DualStatBar` : deux segments proportionnels (stats face-à-face).

### CoachingCard
- Bordure gauche 3dp `lime`, icône `✦`, label uppercase + texte.

### LiveChip
- Pill `hot`, point pulsant (`InfiniteTransition`, alpha 1→0.2→1 sur 1.4s).

### SurfaceChip
- Pill de sélection : plein `color` si sélectionné, contour `line` sinon.
- Pas réservé aux surfaces de court : réutilisé pour tout choix de filtre à un seul niveau
  (ex. filtres Historique, avec `color = colors.lime`).

### BroadcastTextField
- `OutlinedTextField` stylé (`panel` en fond, bordure `lime` au focus, label `muted`→`lime`).
- Support `label` ou `placeholder`, `readOnly`, `supportingText`, `trailingIcon`, `keyboardOptions`.
- **Toujours utiliser ce composant pour un champ texte** — ne pas réappliquer
  `OutlinedTextFieldDefaults.colors(...)` à la main (c'était dupliqué 3 fois avant l'extraction).

### BroadcastSectionCard
- Card `panelHigh`, radius `card`, padding interne 20dp, titre + contenu.
- Deux styles de titre via `titleStyle` :
  - `SectionCardTitleStyle.FORM` (défaut) — titre `titleSmall` SemiBold, pour les cards de
    formulaire (Profil, Paramètres).
  - `SectionCardTitleStyle.STAT` — label `labelSmall` discret + icône optionnelle, pour les
    cards de statistiques (win rate, séquence, volume).

### CircleIconButton
- Bouton icône rond sur fond `panel` (retour, fermeture, ajout). Taille par défaut 38dp, `tint`
  personnalisable (ex. `lime` pour l'avatar profil de l'Accueil).
- Remplace tout `Box.size(...).clip(CircleShape).clickable().background(colors.panel)` fait main.

### BroadcastPrimaryButton
- Bouton primaire lime unique par écran (hauteur 54dp, radius `input`, `isLoading` intégré).
- **Règle stricte** : un seul par écran (cf. règles non négociables). Exception connue et
  assumée : `ProfileScreen` a un bouton "Enregistrer" par section (identité, classement, style de
  jeu, licence) — ce sont des actions de sauvegarde indépendantes, pas des CTA concurrents pour
  la même intention utilisateur. Ne pas dupliquer ce pattern hors de ce cas précis sans y réfléchir.

### ResultBadge (dans `MatchListItem.kt`)
- Pill de badge générique (label + couleurs fond/texte). Utilisé pour V/D dans `MatchListItem`
  et pour VICTOIRE/DÉFAITE dans le bandeau de `SessionDetailScreen`.

### NavigationBar (Bottom Nav)
3 destinations (conforme au handoff, remplace les 4 onglets historiques Accueil/Historique/Coaching/Profil) :
1. **Accueil** — tableau de bord, dernières données, cartes vers Historique/Coaching
2. **Stats** — statistiques agrégées
3. **Profil** — profil joueur + paramètres + axes de travail

Historique et Coaching restent des routes du `NavHost`, jointes depuis des cartes sur l'Accueil.

**Implémentation** : `NavigationBar`/`NavigationBarItem` Material 3 standard (`AppNavGraph.kt`),
sans composant Broadcast dédié. L'onglet actif hérite du lime car `SecondServeTheme` mappe
`colorScheme.primary = lime` — l'apparence est correcte mais par héritage du thème M3, pas par un
style Broadcast explicite (pas de pill d'indicateur personnalisée). Acceptable en l'état ; à
revisiter si un jour l'indicateur par défaut de M3 ne suffit plus visuellement.

---

## Avant d'ajouter un nouvel écran ou composant

1. **Cherchez dans `core/ui/components/` avant d'écrire un `Surface`/`OutlinedTextField`/`Button`
   fait main.** La plupart des patterns visuels (card titrée, champ texte, bouton rond, bouton
   primaire, badge, chip) existent déjà — voir la liste ci-dessus.
2. Si aucun composant ne correspond et que le pattern sera réutilisé ailleurs, ajoutez-le dans
   `core/ui/components/` plutôt que de le dupliquer localement en `private fun`.
3. Utilisez `BroadcastSpacing`/`BroadcastRadius` pour toute valeur qui correspond exactement à
   l'échelle (4/8/12/16/24/32dp ; 8/12/16/18/100dp). Les deux exceptions documentées ci-dessus
   (18dp page, 20dp card) sont les seules valeurs "magiques" tolérées.
4. Le module `:wear` ne peut pas dépendre de `:core:ui` (minSdk 35 vs 33) : ses tokens couleur
   sont dupliqués manuellement dans `wear/.../presentation/theme/BroadcastColors.kt`. Si vous
   changez une valeur hex côté `:core:ui`, répercutez-la manuellement côté `:wear`.

---

## Règles non négociables

1. **Dark mode always on (mobile)** — pas de dynamic color, pas de fallback light sur téléphone/montre
2. **Tabular nums sur les scores** — `fontFeatureSettings = "tnum"`
3. **Cibles tactiles ≥ 48×48dp** — contexte court en plein soleil, mains moites
4. **Empty states designés** — jamais juste un `Text("Aucune donnée")`
5. **Un seul bouton primaire (lime) par écran** — si tout est important, rien ne l'est
6. **Coaching cards** — bordure gauche lime, jamais de fond agressif
7. **Pas de données fabriquées** — si une donnée du mockup n'existe pas dans le domaine (ex.
   nom du joueur, % 1re balle en détail de session), l'élément est omis plutôt qu'inventé
8. **Animations intentionnelles** — `animateContentSize`, `AnimatedVisibility`, `InfiniteTransition` ciblée ; pas de transitions par défaut non maîtrisées

---

## Wear OS

La montre est utilisée sur le court, bras levé, plein soleil. Contraintes spécifiques :

- Taille minimale des zones tactiles : 50% de l'écran (côté gauche = point A, droit = point B)
- Jeux en `24sp` minimum — c'est la donnée la plus lue en cours de jeu
- Indicateur de déconnexion : `error color`, minimum 11sp, jamais en dessous
- Long-press pour undo : toujours accompagné d'une indication visuelle (label "← Annuler" sous les jeux)
- Couleur active : joueur qui vient de marquer reçoit `primary` le temps d'une courte animation

---

## Navigation

### Téléphone
```
Home ──────────── Bottom Nav ─────────────── Profil
  │                    │                       │
  └── Nouveau match    └── Stats/Historique    └── Settings
        │                      │                    Work Axes
        └── Match en cours     └── Détail session
```

### Montre
```
ScoreScreen (unique, full-screen)
  ├── Tap gauche : point A
  ├── Tap droit : point B
  ├── Long-press : undo
  └── isMatchOver → MatchOverScreen
        └── Confirm → (signal vers téléphone pour clôturer)
```
