# Addendum technique — SecondServe

*Ce document contient les détails techniques et les décisions d'implémentation qui appartiennent à l'architecture et aux épics, pas au PRD.*

---

## Stack technique retenu (brainstorming 2026-06-08)

| Couche | Technologie |
|---|---|
| Android (app principale) | Kotlin, API 35 (Android 15), Pixel 9 Pro |
| Wear OS (companion) | Kotlin, Wear OS 4+, Pixel Watch |
| IA on-device (coaching match) | Gemini Nano via Android AICore |
| IA cloud — analyses complexes | Mistral API (modèle à préciser en architecture) |
| IA cloud — analyse vidéo (V2) | Cloud Vision / Google AI à la demande + MediaPipe en batch sur VPS |
| Stockage local | Room (SQLite) |
| Backend | Python 3.12+, FastAPI |
| Infrastructure | VPS personnel — 4 vCPU, 16 Go RAM |
| Sync | REST/JSON — protocole delta à affiner |

---

## Architecture IA hybride — frontière on-device / cloud

```
Mode Match (sur court)
├── Gemini Nano (on-device, Pixel 9 Pro Tensor)
│   ├── Intrant : profil joueur, axes de travail, score match, historique récent
│   ├── Sortie : Conseil 2-3 phrases, < 3 secondes
│   └── Offline : 100% fonctionnel sans réseau
└── Fallback (si AICore indisponible)
    ├── Mistral API (si réseau disponible)
    └── Bibliothèque locale statique (si offline total)

Hors match (analyse, synthèse)
└── Mistral API
    ├── Synthèse post-période (≥ 3 sessions)
    ├── Génération d'axes de travail suggérés
    ├── Conseils coaching section dédiée
    └── Notifications push contextualisées
```

---

## Communication Pixel Watch ↔ Téléphone

**Option A — Wearable DataLayer (Bluetooth)**
- Fiable, faible consommation, standard Android/Wear OS
- Requiert le téléphone à proximité (portée Bluetooth ~10m)
- Recommandé pour V1 — cas d'usage en match = téléphone dans la poche du sac, distance < 5m

**Option B — Wear OS standalone (Wi-Fi)**
- Montre se connecte directement au VPS si Wi-Fi disponible sur le court
- Cas rares (courts avec Wi-Fi public) — complexité ajoutée non justifiée en V1
- Réservé V2 (OQ-6 fermé — Bluetooth/DataLayer retenu pour V1)

---

## Stratégie de sync locale ↔ VPS

- **Granularité** : delta sync par entité (Session, Profil, Axe de travail) — timestamp `updated_at` sur chaque enregistrement
- **Conflit** : last-write-wins sur timestamp serveur (cas rare en mono-utilisateur)
- **Queue offline** : les opérations créées hors connexion sont stockées en queue locale (WorkManager Android) et rejouées au retour du réseau
- **Fréquence** : sync au démarrage de l'app + à la clôture de chaque Session + à la demande

---

## Ton & Personnalité du Coach IA

*Guide de prompt design pour les implémenteurs. Les attributs "jamais générique" et "mémoriel" sont couverts par les conséquences testables de FR-4 et FR-10. Les autres attributs relèvent du design de prompt.*

Le Conseil généré par l'IA (en match comme hors match) respecte une voix cohérente :

- **Chaleureux mais exigeant** — célèbre sincèrement les victoires puis pose 1-2 axes d'amélioration. Après une défaite : d'abord comprendre, puis rebondir. Jamais complaisant.
- **Jamais générique** — chaque Conseil fait référence à au moins un élément spécifique au match en cours ou à l'historique récent du joueur (voir FR-4 testable consequences pour la définition opérationnelle).
- **Court et direct en match** — 2-3 phrases maximum sur la Pixel Watch. Pas de précautions oratoires, pas de listes.
- **Jamais condescendant** — ton d'un partenaire de confiance qui connaît le joueur, pas d'un professeur qui évalue.
- **Mémoriel** — le coach "se souvient" (Profil, Axes de travail, Sessions précédentes) et y fait référence naturellement dans le Conseil (voir FR-4 testable consequences pour le critère de personnalisation).

---

## Options considérées — non retenues

### Coaching vocal (éliminé)
Coaching en mode audio plutôt que texte pendant les changements de côté. Éliminé : risque de distraction pour l'adversaire, complexité d'implémentation TTS, pas de valeur ajoutée vs affichage montre.

### Suivi score au niveau des jeux uniquement (éliminé)
Initialement envisagé pour minimiser la charge cognitive. Éliminé au profit du suivi point par point — nécessaire pour l'analyse post-match (patterns sur break points, jeux à 40-40, etc.).

### Estimation classement temps réel (différé V2)
Algorithme de simulation du classement FFT basé sur les résultats saisis. Différé : dépend d'un modèle précis des règles de points FFT par série — travail non trivial, non bloquant pour V1.
