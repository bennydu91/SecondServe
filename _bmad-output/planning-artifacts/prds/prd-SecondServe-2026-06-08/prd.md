---
title: SecondServe — Application Android de coaching tennistique personnel
status: draft
created: 2026-06-08
updated: 2026-06-08
---

# PRD : SecondServe

## 0. Objet du document

Ce PRD définit SecondServe V1 — une application Android de coaching tennistique personnel alimentée par l'IA. Il s'adresse au développeur-utilisateur unique (Benny) et sert de socle pour les phases suivantes du workflow BMad : architecture technique, épics & stories.

Les fonctionnalités sont groupées par feature avec des exigences fonctionnelles (FR) numérotées globalement. Les hypothèses non confirmées sont taguées `[ASSUMPTION]` et indexées en §9. Les détails d'implémentation (stack technique, choix d'API, configuration VPS, schéma de données) vivent dans `addendum.md`.

---

## 1. Vision

SecondServe est le coach de poche du joueur de tennis amateur sérieux — présent exactement là où aucun entraîneur humain ne peut être : pendant les matchs, en temps réel, au moment où chaque changement de côté offre une fenêtre de conseil actionnable.

La proposition de valeur est double. D'abord, combler le vide entre les séances avec un coach humain : un joueur de club joue des dizaines de matchs de compétition par saison, souvent seul face à ses patterns défaillants. SecondServe lui apporte un regard analytique continu, une mémoire de ses performances et une voix qui l'accompagne au cœur du match. Ensuite, transformer chaque session en données exploitables — pas pour produire des tableaux de bord, mais pour générer des recommandations concrètes et personnalisées.

Le projet se démarque du paysage existant sur trois fronts : (1) le coaching mental et tactique en temps réel pendant le match, inexistant sur Android ; (2) l'intégration native de l'écosystème FFT (classement, séries), complètement non servi par les acteurs actuels ; (3) une approche mono-utilisateur radicalement personnalisée, où chaque conseil est filtré par l'ADN tennistique spécifique du joueur.

---

## 2. Utilisateur cible

### 2.1 Jobs To Be Done

- **Fonctionnel** : Recevoir des conseils tactiques et mentaux personnalisés pendant les changements de côté, sans distraire de la compétition
- **Fonctionnel** : Avoir une mémoire complète et exploitable de tous ses matchs (surface, score, adversaire, ressenti) en un seul endroit
- **Fonctionnel** : Obtenir, après chaque période de jeu, une analyse IA de ses patterns récurrents et des axes d'amélioration prioritaires
- **Fonctionnel** : Suivre l'évolution de son classement FFT et ses performances par surface et type de compétition
- **Émotionnel** : Entrer sur le court avec la confiance de ne pas être seul — avoir un "deuxième service" mental
- **Émotionnel** : Progresser de façon mesurable et visible dans le temps, pas juste ressentir qu'on joue "mieux"
- **Contextuel** : Accéder au coaching en plein match, une main sur la montre, en moins de 20 secondes

### 2.2 Non-utilisateurs (V1)

- Autres joueurs ou membres d'une équipe — SecondServe est mono-utilisateur by design
- Coachs humains cherchant un outil de suivi de leurs élèves
- Joueurs non licenciés FFT ou débutants sans historique de match à analyser

### 2.3 User Journeys

**UJ-1. Benny entre en match — coaching au changement de côté.**
- **Persona + contexte :** Benny, classé 30/2 FFT, joue un match de championnat par équipes, premier set serré. Il est seul sur le court.
- **Entry state :** App ouverte en Mode Match avant le match (informations de session déjà saisies). Pixel Watch active, interface score visible.
- **Chemin :**
  1. Pendant le set, Benny tape sur sa Pixel Watch à chaque point perdu ou gagné — le score se met à jour en temps réel.
  2. À la fin d'un jeu (changement de côté), un bouton "Conseil" apparaît sur la montre.
  3. Il tape une fois. La montre affiche en 2-3 secondes un Conseil court (ex : "Tu perds trop de points sur ta 2e balle côté revers. Sécurise d'abord, construis ensuite.").
  4. Le téléphone, dans la poche, affiche une version enrichie avec contexte.
  5. Benny reprend le match avec un focus clair.
- **Climax :** Un conseil personnel et actionnable, délivré en moins de 20 secondes, sans navigation, sans sortir le téléphone.
- **Résolution :** La session continue de se logger automatiquement. Benny ne touche plus à rien jusqu'au prochain changement de côté.
- **Edge case :** Pas de réseau sur le court → Gemini Nano prend le relais 100% on-device, latence identique.

**UJ-2. Benny analyse la semaine après trois matchs.**
- **Persona + contexte :** Benny a joué 3 matchs en 10 jours, résultats mitigés. Il ouvre l'app le soir pour comprendre ce qui coince.
- **Entry state :** App ouverte sur l'accueil, 3 sessions synchronisées sur le VPS.
- **Chemin :**
  1. Il ouvre la section "Coaching". L'app affiche une synthèse IA : "Sur tes 3 derniers matchs, tu as perdu 67% de tes jeux quand tu étais mené. Tes revers cross semblent être le point de rupture en pression."
  2. Il consulte les détails du dernier match, relit les Conseils délivrés en cours de match.
  3. Il met à jour son Axe de travail : "Revers défensif en pression".
  4. L'app intègre ce focus dans les Conseils des prochains matchs.
- **Climax :** Benny comprend un pattern concret et peut le verbaliser à son coach humain.
- **Résolution :** L'Axe de travail est sauvegardé, les prochains Conseils match en tiennent compte.

**UJ-3. Benny met à jour son classement après la publication mensuelle FFT.**
- **Persona + contexte :** La FFT publie les nouveaux classements. Benny monte de 30/2 à 30/1.
- **Entry state :** App ouverte sur son profil.
- **Chemin :**
  1. Il navigue vers "Mon profil" → "Classement officiel FFT".
  2. Il saisit sa nouvelle série (30/1) et le nombre de points.
  3. L'app enregistre et affiche sa progression historique.
- **Climax :** Le classement est mis à jour, les prochaines recommandations tiennent compte du nouveau niveau.
- **Résolution :** Le profil est à jour, la timeline de classement s'allonge.

---

## 3. Glossaire

- **Session** — Unité de base de l'activité : un match ou une séance d'entraînement, avec date, type, infos contextuelles et résultat. Une Session a exactement un Type et appartient au profil de l'utilisateur unique.
- **Mode Match** — État actif de l'app pendant une compétition. Implique le suivi de score en temps réel via la Pixel Watch et le coaching aux changements de côté.
- **Mode Entraînement** — État actif pendant une séance d'entraînement. En V1, limité à la saisie de Session post-entraînement ; aucun coaching live. [ASSUMPTION]
- **Changement de côté** — Pause réglementaire entre les jeux lors d'un match de tennis. Fenêtre d'intervention du coaching SecondServe.
- **Conseil** — Message court généré par l'IA (Gemini Nano ou Mistral API) au changement de côté ou dans la section Coaching hors match. Format match : 2-3 phrases, actionnable dans les secondes suivantes.
- **Classement FFT** — Classement officiel de la Fédération Française de Tennis. Format : séries (de 40 à 1/6) et points. Aucune API publique disponible — saisie manuelle uniquement.
- **Axe de travail** — Objectif d'amélioration défini par l'utilisateur (ou dicté par son coach humain), intégré dans le moteur de coaching pour personnaliser les Conseils.
- **Profil joueur** — Ensemble des données persistantes décrivant l'utilisateur : Classement FFT, Style de jeu, Axes de travail, surfaces de prédilection, historique de Sessions.
- **Style de jeu** — Caractérisation de l'approche tennistique de l'utilisateur (défenseur / attaquant / contre-puncheur / all-court), inférée à partir des données et amendable manuellement.
- **Sync** — Synchronisation bidirectionnelle des données entre le stockage local (Room/SQLite sur l'appareil) et le backend VPS.
- **On-device** — Traitement exécuté localement sur le Pixel 9 Pro via Gemini Nano / Android AICore, sans appel réseau.
- **Pixel Watch** — Montre connectée Wear OS utilisée comme interface secondaire en Mode Match pour la saisie du score et l'affichage des Conseils.

---

## 4. Features

### 4.1 Mode Match — Coaching en temps réel

**Description :** SecondServe entre en Mode Match dès que l'utilisateur démarre une Session de match. La Pixel Watch prend le relais comme interface primaire sur le court : saisie du score jeu par jeu, déclenchement du coaching aux changements de côté. Le coaching est généré on-device par Gemini Nano — avec le Profil joueur, les Axes de travail et le contexte du match en cours comme intrants — ou via Mistral API en fallback réseau. L'interface est ultra-minimale : aucune navigation, un seul tap pour demander un Conseil. Le téléphone reste dans la poche et affiche en parallèle une version enrichie du Conseil, mais son usage n'est jamais obligatoire en match.

Réalise UJ-1.

**Functional Requirements :**

#### FR-1 : Démarrage de session match
L'utilisateur peut démarrer une Session de type Match depuis l'écran d'accueil. Les champs obligatoires sont : surface (Terre battue / Gazon / Dur / Carpet) et format du match. Le format du match comprend : nombre de sets (1 / 3) et règle du 3e set (avantage complet / super tie-break à 10 points / set décisif raccourci). Les champs optionnels sont : adversaire (nom libre), type de compétition (championnat par équipes / tournoi homologué / match amical / non précisé), tournoi (nom libre). La session est créée et persistée localement avant l'entrée sur le court.

**Conséquences testables :**
- L'app accepte une session avec surface et format seuls (sans adversaire ni type de compétition).
- Le format choisi conditionne la logique de score tout au long de la session (ex. : super tie-break déclenché à 1 set partout si format en 3 sets avec super tie-break).
- La session est accessible dans l'historique même si elle est interrompue sans fin formelle.
- Aucune information réseau n'est requise pour créer et démarrer une session.

#### FR-2 : Suivi de score sur Pixel Watch
En Mode Match actif, la Pixel Watch affiche une interface de saisie de score point par point. L'utilisateur enregistre chaque point gagné ou perdu via deux boutons (Point A / Point B). L'app maintient le score complet en temps réel : points dans le jeu en cours (15-0, 30-15, Avantage...), jeux du set, sets. En fin de jeu (point décisif atteint), la montre affiche brièvement le score de jeux mis à jour avant de revenir à l'interface de saisie de points. En fin de set, le score de sets est affiché. En cas de tie-break ou de super tie-break, la logique de comptage bascule automatiquement selon le format configuré en FR-1.

**Conséquences testables :**
- Un tap "Point A" fait progresser correctement le score selon les règles tennis : 0 → 15 → 30 → 40 → Jeu (ou Avantage / Égalité si 40-40).
- Le score de jeux et de sets se met à jour correctement à chaque fin de jeu.
- Le tie-break se déclenche automatiquement à 6-6 dans un set, avec comptage 0-1-2... jusqu'à 7 (ou plus en cas d'égalité).
- Le super tie-break se déclenche automatiquement si le format configuré le prévoit, avec comptage jusqu'à 10 (ou plus en cas d'égalité).
- Chaque mise à jour du score s'affiche sur la montre en moins de 500ms après le tap.
- Le score affiché en permanence sur la montre indique : points du jeu en cours + score de jeux + sets.

#### FR-3 : Déclenchement du coaching au changement de côté
À chaque changement de côté (total des jeux du set = nombre impair), la Pixel Watch affiche un bouton "Conseil". Un tap déclenche la génération d'un Conseil. L'utilisateur peut ignorer sans tap. Si l'utilisateur n'interagit pas dans 60 secondes, l'interface revient à l'affichage du score.

**Conséquences testables :**
- Le bouton "Conseil" apparaît automatiquement aux bons changements de côté.
- L'absence d'interaction dans les 60 secondes ramène l'écran à l'affichage du score.
- Le bouton est absent lors des jeux sans changement de côté.
- Chaque action de saisie de score et de déclenchement du Conseil est accessible depuis l'écran actif en exactement 1 tap, sans navigation préalable.

#### FR-4 : Génération du Conseil on-device
Gemini Nano génère un Conseil en prenant en compte : score actuel du match, Profil joueur (Style de jeu, Axes de travail), historique des Sessions récentes. Le Conseil est affiché sur la Pixel Watch en ≤ 3 secondes.

**Conséquences testables :**
- Le Conseil affiché sur la montre fait au maximum 3 phrases.
- Le délai entre le tap "Conseil" et l'affichage est ≤ 3 secondes sur Pixel 9 Pro.
- En l'absence de connexion réseau, le Conseil est généré on-device sans dégradation de la latence.
- Le Conseil nomme au moins l'un des éléments suivants dans son texte : score de jeux actuel, surface, Axe de travail actif, ou résultat d'une Session récente.
- Avec Android AICore simulé indisponible et sans réseau, le Conseil affiché est tiré de la bibliothèque locale statique (vérifiable : le contenu correspond à l'un des messages prédéfinis de la bibliothèque).

**Feature-specific NFRs :**
- Disponibilité offline totale : 100% fonctionnel sans réseau (Gemini Nano on-device).
- Fallback si Android AICore est indisponible : basculer vers Mistral API si réseau disponible ; sinon, afficher un Conseil générique tiré d'une bibliothèque locale pré-définie (≥ 20 conseils couvrant différentes situations score/contexte).

#### FR-5 : Affichage enrichi sur téléphone
En parallèle de la Pixel Watch, le téléphone affiche une version enrichie du Conseil (contexte, raisonnement, 1-2 points d'attention) sans nécessiter de manipulation de l'écran (notification ou affichage ambient). Réalise UJ-1.

**Conséquences testables :**
- Le Conseil enrichi apparaît sur le téléphone dans les 5 secondes suivant le tap sur la montre.
- Le Conseil téléphone est accessible sans déverrouillage ou navigation.

#### FR-6 : Clôture de session match
En fin de match, l'utilisateur saisit le score final (sur montre ou téléphone) et une évaluation rapide (ressenti : 1-5 étoiles ; commentaire optionnel). La Session est marquée "terminée", sauvegardée localement et mise en queue de Sync.

**Conséquences testables :**
- Une session peut être clôturée avec score final uniquement (sans évaluation).
- La session clôturée apparaît dans l'historique avec statut "terminé" et résultat (victoire/défaite).
- La Sync est déclenchée automatiquement si une connexion réseau est disponible à la clôture.

---

### 4.2 Suivi des matchs & historique

**Description :** SecondServe est la mémoire tennistique de l'utilisateur. Chaque Session est enregistrée avec ses métadonnées, son score et son ressenti. L'historique offre une vue globale des performances et des statistiques agrégées. [ASSUMPTION : les stats couvrent les Sessions de type Match uniquement, pas les entraînements]

Réalise UJ-2 (en partie), UJ-3.

**Functional Requirements :**

#### FR-7 : Historique des Sessions
L'utilisateur accède à la liste de toutes ses Sessions (matchs et entraînements), triées par date décroissante. Chaque entrée affiche : date, adversaire (si renseigné), surface, score final, résultat (victoire/défaite/non applicable), type de compétition.

**Conséquences testables :**
- L'historique affiche toutes les Sessions créées, y compris les sessions sans score final.
- Le tri chronologique inverse est l'ordre par défaut.
- Une Session incomplète (interrompue) est visible dans l'historique avec un indicateur de statut.

#### FR-8 : Statistiques agrégées
L'app calcule et affiche : win rate global (matchs avec résultat enregistré), win rate par surface, nombre de Sessions par type, séquence active de victoires ou de défaites.

**Conséquences testables :**
- Le win rate se recalcule automatiquement après chaque nouvelle Session terminée.
- Les stats par surface ne s'affichent que si ≥ 3 matchs sur cette surface sont enregistrés (sinon "données insuffisantes").
- Les statistiques sont consultables hors connexion (données locales).

#### FR-9 : Saisie manuelle de sessions historiques
L'utilisateur peut ajouter une Session pour un match joué sans l'app (saisie rétrospective). Les champs disponibles sont identiques à une session démarrée en temps réel.

**Conséquences testables :**
- Une session saisie manuellement apparaît dans l'historique et est incluse dans les statistiques.
- La saisie manuelle est accessible depuis l'historique sans passer par le Mode Match. [ASSUMPTION : pas d'indicateur différentiel "saisie manuelle" vs session live en V1]

---

### 4.3 Conseils IA personnalisés (hors match)

**Description :** Entre les matchs, SecondServe joue le rôle de coach analyste. Via Mistral API, l'app analyse les Sessions récentes pour identifier des patterns, générer des insights et proposer des recommandations d'entraînement personnalisées. Le ton est celui d'un coach humain — chaleureux, direct, jamais générique. L'utilisateur peut également interroger le coach IA en mode conversationnel. *Guide de voix et personnalité du Coach IA : voir `addendum.md` § Ton & Personnalité du Coach IA.*

Réalise UJ-2.

**Functional Requirements :**

#### FR-10 : Analyse individuelle post-match
À la clôture de chaque Session de type Match, l'app génère automatiquement une analyse individuelle via Mistral API. L'analyse inclut : points forts observés dans ce match, point(s) faible(s) mis en évidence, écart avec les Axes de travail actifs, 1-2 recommandations concrètes pour la prochaine séance d'entraînement. L'analyse est accessible depuis le détail de la Session dans l'historique.

**Conséquences testables :**
- La génération est déclenchée automatiquement à la clôture de la Session si une connexion réseau est disponible.
- Si le réseau est absent à la clôture, la génération est mise en queue et exécutée à la prochaine connexion.
- L'analyse est persistée localement et consultable hors connexion après génération.
- L'analyse fait référence à des données spécifiques de la Session (score, format, surface — pas de contenu générique).

#### FR-11 : Synthèse IA multi-matchs
L'app génère automatiquement une synthèse coaching transversale lorsque ≥ 3 nouvelles Sessions Match ont été enregistrées depuis la dernière synthèse. La synthèse identifie des patterns sur la période : tendances récurrentes, évolution par rapport aux synthèses précédentes, axe d'amélioration prioritaire multi-matchs, recommandation d'entraînement structurée.

**Conséquences testables :**
- La synthèse multi-matchs est générée via Mistral API et requiert une connexion réseau.
- La synthèse générée est persistée localement et consultable hors connexion.
- L'utilisateur peut forcer une génération à la demande, même si le seuil de 3 Sessions n'est pas atteint.
- La synthèse se distingue visuellement de l'analyse individuelle post-match dans la section Coaching.
- La synthèse fait référence à des données agrégées des Sessions concernées (pas de contenu générique).

#### FR-12 : Axes de travail
L'utilisateur peut créer, modifier et supprimer des Axes de travail. Maximum 3 axes actifs simultanément. L'app propose des axes suggérés par l'IA à partir des Sessions récentes ; l'utilisateur peut les accepter ou les ignorer.

**Conséquences testables :**
- Un Axe de travail saisi est intégré dans le contexte envoyé à Gemini Nano et Mistral API dès la prochaine Session.
- L'app affiche un message d'erreur si l'utilisateur tente d'ajouter un 4e axe actif.
- Les axes suggérés par l'IA sont clairement distingués des axes saisis manuellement.

#### FR-13 : Notifications coaching actionnables
L'app envoie des notifications push personnalisées : conseil du jour (fréquence configurable) et rappel de préparation avant un match programmé. Chaque notification fait référence à un élément spécifique du Profil joueur ou de l'historique récent.

**Conséquences testables :**
- La fréquence des notifications est configurable (quotidien / tous les 2 jours / hebdomadaire / désactivé).
- Un mode silencieux est activable manuellement pour une période définie.
- Le contenu de la notification inclut au moins une référence spécifique (surface, Axe de travail, résultat récent).
- Aucune notification n'est envoyée si aucune Session n'a été enregistrée depuis 30 jours (app dormante).

---

### 4.4 Profil joueur

**Description :** Le profil est le socle de la personnalisation. Il regroupe le Classement FFT officiel, le Style de jeu inféré, les Axes de travail, les performances par surface et les consignes du coach humain. Il évolue dans le temps et alimente tous les modules de coaching.

Réalise UJ-3.

**Functional Requirements :**

#### FR-14 : Classement FFT
L'utilisateur saisit son Classement FFT officiel (série + points) à la demande. L'historique des classements est conservé pour visualiser la progression dans le temps.

**Conséquences testables :**
- La saisie accepte les formats de séries FFT valides (40, 30/5, 30/4, 30/3, 30/2, 30/1, 15/5, 15/4, 15/3, 15/2, 15/1, 4/6, 3/6, 2/6, 1/6).
- L'historique des classements est affiché sous forme de timeline chronologique.
- Le classement actuel est visible sur l'écran de profil et intégré dans les prompts IA.

#### FR-15 : Style de jeu inféré
L'app infère le Style de jeu à partir des données de Sessions après ≥ 10 Sessions enregistrées. Le Style inféré est affiché sur le profil et amendable manuellement. [ASSUMPTION : l'inférence est heuristique en V1, pas un modèle ML dédié]

**Conséquences testables :**
- La section Style de jeu reste vide ou affiche "insuffisant" avant 10 Sessions.
- L'utilisateur peut corriger ou écraser le Style inféré par une saisie manuelle à tout moment.
- Le Style (inféré ou manuel) est intégré dans les prompts envoyés à Gemini Nano et Mistral API.

#### FR-16 : Données de profil complémentaires
L'utilisateur peut renseigner : surfaces de prédilection (préférence déclarée), numéro de licence FFT (optionnel), et les consignes de son coach humain via une structure semi-guidée à 3 champs : "Axe de travail principal", "Axe de travail secondaire", "Points à éviter / mauvaises habitudes à corriger". Chaque champ accepte du texte libre.

**Conséquences testables :**
- Le numéro de licence FFT est stocké localement et sur le VPS personnel uniquement — jamais transmis à Mistral API ou tout autre tiers.
- Le contenu des 3 champs "consignes coach" est structuré dans le prompt de génération des Conseils (chaque champ envoyé comme élément distinct pour maximiser la précision du contexte IA).
- Les surfaces de prédilection sont reflétées dans les statistiques et les recommandations IA.
- Les champs "consignes coach" sont optionnels individuellement — un seul champ renseigné suffit pour enrichir le contexte IA.

---

## 5. Non-Goals (explicites)

- **Pas de social ni de multi-utilisateurs** — SecondServe est mono-utilisateur. Pas de classements comparatifs, pas de partage, pas d'espace communautaire.
- **Pas d'analyse vidéo en V1** — l'upload et l'analyse de clips vidéo sont réservés à V2.
- **Pas de suggestions de tournois en V1** — la recommandation intelligente de tournois FFT est réservée à V2.
- **Pas d'estimation de classement temps réel en V1** — l'algorithme d'estimation interne basé sur les résultats est réservé à V2.
- **Pas de coaching Mode Entraînement en temps réel** — en V1, l'entraînement est uniquement tracké post-session, sans coaching live.
- **Pas de dashboard coach humain** — SecondServe n'est pas un outil de suivi pour les entraîneurs.
- **Pas de version iOS / web** — Android uniquement en V1.
- **Pas d'intégration API FFT** — aucune API publique disponible ; la saisie manuelle est la seule option.
- **Mode Match en dehors de la portée Bluetooth** — le Mode Match suppose le téléphone à portée Bluetooth (< 10m) ; les tournois imposant un vestiaire hors de cette portée sont hors V1.

---

## 6. Périmètre MVP

### 6.1 In Scope (V1)

- Mode Match avec suivi de score sur Pixel Watch (Wear OS companion)
- Coaching aux changements de côté via Gemini Nano on-device + fallback Mistral API
- Affichage enrichi du Conseil sur téléphone en parallèle
- Suivi des matchs : historique complet, saisie manuelle, statistiques agrégées
- Synthèse IA post-période (Mistral API)
- Axes de travail (3 actifs maximum)
- Notifications coaching contextualisées, fréquence configurable
- Profil joueur : Classement FFT, Style de jeu, surfaces, consignes coach
- Stockage local (Room) + Sync VPS (backend Python/FastAPI)

### 6.2 Out of Scope pour le MVP (V2+)

- **Analyse vidéo** — upload et analyse de vidéos de match. *(V2 — différenciateur technique majeur)*
- **Suggestions de tournois** — recommandation de tournois FFT adaptés au niveau et à la localisation. *(V2)*
- **Estimation classement temps réel** — simulation du classement en fonction des résultats saisis. *(V2)*
- **Style joueur V2** — modèle ML dédié remplaçant l'heuristique V1. *(V2)*
- **Gamification discrète** — streaks, objectifs mensuels, paliers. *(V2)*
- **Journal IA-guidé post-session** — questionnaire structuré après chaque match. *(V2)*
- **Mode Entraînement live** — coaching en temps réel pendant les séances. *(V2)*
- **Coaching adaptatif à l'état de forme** — inférence de signaux indirects (fatigue, régularité). *(V2)*
- **Distribution Play Store** — la V1 est un build personnel sideloaded.

---

## 7. Success Metrics

**Primaires**

- **SM-1 : Rétention en match** — SecondServe est activé sur ≥ 80% des matchs de compétition joués dans les 90 jours suivant le premier usage en production. *Méthode : comptage des Sessions de type Match vs. estimation subjective du nombre de matchs joués.* Valide FR-1, FR-2, FR-3, FR-4.

- **SM-2 : Engagement hors match** — La section Coaching ou le Profil est consulté ≥ 2 fois par semaine lors des semaines d'activité tennistique (≥ 1 session dans la semaine). Valide FR-10, FR-12, FR-13.

**Secondaires**

- **SM-3 : Qualité perçue des Conseils** — Satisfaction subjective ≥ 4/5 lors d'une auto-évaluation mensuelle (notation rapide dans l'app). Valide FR-4, FR-10.

- **SM-4 : Complétude des données** — ≥ 85% des Sessions Match ont un score final enregistré. Valide FR-6, FR-7.

- **SM-5 : Utilisation des Axes de travail** — Au moins 1 Axe de travail actif en permanence sur ≥ 80% de la période d'utilisation active. Valide FR-12.

**Contre-métriques (ne pas optimiser)**

- **SM-C1 : Temps d'interaction en match** — L'interaction moyenne sur la Pixel Watch pendant un changement de côté ne doit pas dépasser 15 secondes. Contrebalance SM-1 : évite d'optimiser l'engagement au détriment de la concentration en match.

- **SM-C2 : Taux de désactivation des notifications** — L'utilisateur ne désactive pas les notifications dans les 30 premiers jours d'usage. Contrebalance SM-2 : indique que la pertinence des notifications ne génère pas de notification fatigue.

---

## 8. Open Questions

*Toutes les questions d'origine ont été résolues. Aucune question ouverte résiduelle.*

| OQ | Question | Décision |
|---|---|---|
| OQ-1 | Granularité score Pixel Watch | Points individuels inclus (15-0, 30-15, Avantage) — voir FR-2 |
| OQ-2 | Super tie-break | Format configuré au démarrage de la session — voir FR-1 |
| OQ-3 | Protocole de Sync | Delta sync retenu — voir NFRs Sync |
| OQ-4 | Trigger synthèse IA | Analyse individuelle post-match (chaque match) + synthèse multi-matchs (seuil 3) — voir FR-10, FR-11 |
| OQ-5 | Consignes du coach humain | Structure semi-guidée à 3 champs — voir FR-16 |
| OQ-6 | Wear OS communication | Bluetooth/DataLayer uniquement en V1 (téléphone dans le sac = distance < 10m) — standalone Wi-Fi réservé V2 |

---

## 9. Index des Assumptions

- **§3, Mode Entraînement** — En V1, le Mode Entraînement est limité à la saisie post-session ; aucun coaching live pendant l'entraînement.
- **§4.2, FR-8** — Les statistiques agrégées couvrent les Sessions de type Match uniquement.
- **§4.2, FR-9** — Pas d'indicateur différentiel "saisie manuelle" vs session live en V1.
- **§4.4, FR-15** — L'inférence du Style de jeu est heuristique en V1, pas un modèle ML dédié.
- **§NFRs, Sync** — Le VPS est la source de vérité en cas de conflit (last-write-wins sur timestamp serveur).
- **§NFRs, Plateforme** — Seul le Pixel 9 Pro est ciblé en V1 (API 35) ; aucun autre appareil n'est validé.

---

## NFRs Transversaux

### Performance

| ID | Interaction | Cible |
|---|---|---|
| NFR-P1 | Conseil Gemini Nano on-device | ≤ 3 secondes sur Pixel 9 Pro |
| NFR-P2 | Mise à jour du score sur Pixel Watch | ≤ 500ms après tap |
| NFR-P3 | Chargement de l'historique (< 200 sessions) | ≤ 1 seconde |
| NFR-P4 | Appel Mistral API (synthèse hors match) | ≤ 10 secondes en 4G/Wi-Fi |

### Disponibilité offline

- **NFR-OFF1** Mode Match 100% fonctionnel sans connexion réseau (Gemini Nano on-device).
- **NFR-OFF2** Historique et Profil consultables hors connexion (données locales Room).
- **NFR-OFF3** Sessions créées hors connexion mises en queue de Sync ; synchronisées automatiquement au retour du réseau.

### Sync & stockage

- **NFR-S1** Stockage local : Room (SQLite) sur Android.
- **NFR-S2** Sync bidirectionnelle avec le VPS via API REST.
- **NFR-S3** Sync delta-based : seules les modifications depuis la dernière sync sont transmises.
- **NFR-S4** Résolution de conflits : timestamp serveur (last-write-wins). [ASSUMPTION]
- **NFR-S5** Données utilisateur transmises à Mistral API : profil générique et statistiques uniquement — aucun identifiant personnel.

### Confidentialité

- **NFR-C1** Architecture mono-utilisateur : aucune donnée partagée entre utilisateurs.
- **NFR-C2** Données stockées uniquement sur l'appareil et le VPS personnel de l'utilisateur.
- **NFR-C3** Le numéro de licence FFT n'est jamais inclus dans les prompts envoyés à Mistral API.

### UX en match

- **NFR-UX1** Interface Pixel Watch : toute action en match accessible en ≤ 1 tap (voir FR-3).
- **NFR-UX2** Conseil Pixel Watch : ≤ 3 phrases, police ≥ 16sp, lisible en conditions lumineuses extérieures.
- **NFR-UX3** Aucune action ne termine ou interrompt une Session active sans confirmation explicite de l'utilisateur.

### Plateforme

- **NFR-PLT1** Android : Pixel 9 Pro, Android 15 (API 35) — cible V1 unique. [ASSUMPTION]
- **NFR-PLT2** Wear OS : Pixel Watch, Wear OS 4+.
- **NFR-PLT3** Langages : Kotlin (Android + Wear OS) / Python + FastAPI (backend VPS).

