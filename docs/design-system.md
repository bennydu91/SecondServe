# SecondServe — Design System

## Identité visuelle

**Références** : Strava (données sportives denses) · Nike Run Club (émotion typographique) · ESPN (hiérarchie du score)  
**Principe directeur** : dark mode first, plein soleil first, chiffres first.

---

## Couleurs

### Palette principale (dark mode — valeurs de fallback)

| Rôle | Token | Hex | Usage |
|------|-------|-----|-------|
| Primary | `primary` | `#52D68A` | Actions primaires, scores actifs |
| On Primary | `onPrimary` | `#00391B` | Texte sur primary |
| Primary Container | `primaryContainer` | `#00522A` | Fond des chips, badges |
| On Primary Container | `onPrimaryContainer` | `#7FFDB5` | Texte dans primaryContainer |
| Secondary | `secondary` | `#E8C73E` | Accents, jaune balle de tennis |
| Secondary Container | `secondaryContainer` | `#554400` | Fond des éléments secondaires |
| On Secondary Container | `onSecondaryContainer` | `#FFE178` | Texte dans secondaryContainer |
| Tertiary | `tertiary` | `#7BCFFB` | Data viz, graphiques |
| Background | `background` | `#0B160E` | Fond global (vert noir profond) |
| Surface | `surface` | `#0B160E` | Fond des cards |
| Surface Variant | `surfaceVariant` | `#1C2B1E` | Fond des inputs, séparateurs |
| On Surface | `onSurface` | `#DCE9DC` | Texte principal |
| On Surface Variant | `onSurfaceVariant` | `#9EB5A1` | Texte secondaire, labels |
| Outline | `outline` | `#45644A` | Bordures des inputs |
| Outline Variant | `outlineVariant` | `#2B3D2E` | Séparateurs légers |
| Error | `error` | `#FFB3BA` | Erreurs, alertes |

### Dynamic Color
Sur API 31+ (Android 12+), le Color Scheme s'adapte aux couleurs du fond d'écran via `dynamicDarkColorScheme` / `dynamicLightColorScheme`. Les couleurs ci-dessus servent de fallback.

---

## Typographie

**Règle scores** : toujours utiliser `fontFeatureSettings = "tnum"` pour les chiffres afin d'éviter les décalages visuels lors des changements de score.

### Échelle d'usage dans l'app

| Style MD3 | Taille | Gras | Usage |
|-----------|--------|------|-------|
| `displayLarge` | 57sp | Non | Jamais utilisé |
| `headlineLarge` | 32sp | Oui | Score sets (jamais plus grand) |
| `headlineMedium` | 28sp | Semi | Score jeux en cours |
| `titleLarge` | 22sp | Semi | Score points |
| `titleMedium` | 16sp | Oui | Titres de section |
| `bodyLarge` | 16sp | Non | Contenu principal |
| `bodyMedium` | 14sp | Non | Cartes, listes |
| `labelMedium` | 12sp | Oui | Labels, badges |
| `labelSmall` | 11sp | Non | Métadonnées, dates |

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
| `spacing.xxxl` | 48dp | Espacement header |

---

## Composants

### ScoreCard (composant central)
- Fond : `surfaceVariant` avec border radius 16dp
- Hiérarchie verticale : Sets (petit, haut) → Jeux (XXL, centre) → Points (grand, bas)
- Le joueur "actif" (dernier à avoir marqué) reçoit la couleur `primary`
- Taille minimale : ne jamais comprimer en dessous de 140dp de hauteur

### Button hierarchy
1. `FilledButton` — un seul par écran, action principale (Nouveau match, Confirmer)
2. `FilledTonalButton` — action secondaire positive (Générer l'analyse)
3. `OutlinedButton` — action neutre (Annuler, Retour)
4. `TextButton` — action tertiaire (Voir plus, Filtrer)

**Jamais deux `FilledButton` sur le même écran.**

### NavigationBar (Bottom Nav)
3 destinations :
1. **Accueil** — tableau de bord, dernières données
2. **Stats** — historique + statistiques (routes unifiées)
3. **Profil** — profil joueur + paramètres + axes de travail

### MatchListItem
- Score en `bodyMedium` **bold** à gauche
- Résultat (V/D) en badge coloré à droite : `primaryContainer` pour V, `errorContainer` pour D
- Surface et adversaire en `labelSmall` sous le score
- Taille minimale 64dp pour le tap en plein air

### StatBar (barre comparative)
- Barre horizontale duale : joueur à gauche (primary), adversaire à droite (outlineVariant)
- Valeur en chiffre centré au-dessus
- Pas de camembert. Jamais.

---

## Règles non négociables

1. **Dark mode always on** — ne jamais forcer le light mode
2. **Tabular nums sur les scores** — `fontFeatureSettings = "tnum"`
3. **Cibles tactiles ≥ 48×48dp** — contexte court en plein soleil, mains moites
4. **Empty states designés** — jamais juste un `Text("Aucune donnée")`
5. **Un seul FilledButton par écran** — si tout est important, rien ne l'est
6. **Coaching cards** — fond `primaryContainer`, jamais `secondary` (trop agressif)
7. **Animations intentionnelles** — uniquement `animateContentSize` et `AnimatedVisibility`, pas de transitions aléatoires

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
