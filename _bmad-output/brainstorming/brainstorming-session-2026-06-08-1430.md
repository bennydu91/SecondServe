---
stepsCompleted: [1]
inputDocuments: []
session_topic: 'SecondServe — Application Android de coaching tennistique intelligent (IA)'
session_goals: 'Couverture exhaustive de tous les éléments du projet (fonctionnel, technique, UX, business, risques) + socle solide pour la suite du workflow BMad (PRD, Architecture, Épics & Stories)'
selected_approach: 'ai-recommended'
techniques_used: ['SCAMPER Method', 'Six Thinking Hats', 'Question Storming']
ideas_generated: [18]
stepsCompleted: [1, 2, 3]
context_file: ''
---

# Brainstorming Session — SecondServe

**Facilitateur :** Benny
**Date :** 2026-06-08

## Session Overview

**Sujet :** SecondServe — Application Android de coaching tennistique intelligent (IA)

**Objectifs :** Couverture exhaustive de tous les éléments du projet (fonctionnel, technique, UX, business, risques) + socle solide pour la suite du workflow BMad

### Vision Produit

- Application Android native
- Assistant tennistique personnel alimenté par l'IA
- Fonctionnalités envisagées : suivi des résultats, conseils sur les points faibles, analyse vidéo (upload → suggestions IA), coaching en temps réel pendant les matchs, suggestions de tournois, suivi du classement
- Stack : Android natif + Backend VPS personnel

### Session Setup

Approche retenue : **Techniques recommandées par l'IA** — séquence en 3 phases adaptée à un projet déjà conceptualisé mais nécessitant une couverture exhaustive.

---

## Technique Selection

**Approche :** Techniques recommandées par l'IA
**Contexte d'analyse :** SecondeServe — projet déjà conceptualisé, besoin de couverture exhaustive, output orienté BMad workflow

**Techniques retenues :**
- **SCAMPER Method** : Expansion exhaustive des fonctionnalités par 7 lentilles
- **Six Thinking Hats** : Couverture multi-angles (technique, UX, business, risques)
- **Question Storming** : Cartographie des inconnues critiques → pont vers le PRD

---

## Technique Execution Results

### SCAMPER Method

**S — Substituer**

**[SCAMPER-S #1] : Sélection intelligente de clips**
*Concept :* L'app filme en arrière-plan pendant l'entraînement/match et propose automatiquement les extraits les plus pertinents à analyser (services, échanges, points décisifs), plutôt que de laisser l'utilisateur chercher manuellement dans une longue vidéo.
*Nouveauté :* Transforme l'upload passif en curation active par l'IA — réduction de la friction entre "je me filme" et "j'obtiens du feedback".

**[SCAMPER-S #2] : Classement temps réel estimé**
*Concept :* Un algorithme interne calcule le classement estimé en continu à partir des résultats saisis, sans attendre la mise à jour officielle FFT. Affichage en parallèle du classement officiel + estimation live.
*Nouveauté :* Donne au joueur une visibilité immédiate sur l'impact de chaque match sur sa position — motivant et actionnable.

**[SCAMPER-S #3] : Coach mental aux changements de côté**
*Concept :* Pendant les matchs, SecondeServe délivre des micro-conseils mentaux et physiques (respiration, relâchement, focus, routine) aux changements de côté — format audio + texte affiché à l'écran. Pas de caméra, pas de distraction.
*Nouveauté :* Distingue clairement deux modes : "analyse vidéo à l'entraînement" vs "coaching mental en match" — deux UX distinctes pour deux contextes radicalement différents.

**C — Combiner**

**[SCAMPER-C #1] : Tournois optimisés par classement**
*Concept :* L'app croise le classement estimé en temps réel, l'historique de résultats et la localisation pour suggérer automatiquement les tournois où l'utilisateur a le meilleur rapport "points gagnables / déplacement".
*Nouveauté :* Transforme la recherche de tournois (manuelle et fastidieuse) en recommandation intelligente personnalisée — GPS de progression tennistique.

**[SCAMPER-C #2] : Fiche match auto-générée par l'IA vidéo**
*Concept :* Après l'upload d'un clip, l'IA pré-remplit automatiquement la fiche de match (score, surface, patterns détectés, points forts/faibles observés). L'utilisateur valide et complète en quelques secondes.
*Nouveauté :* Élimine la double saisie — la vidéo devient la source unique de vérité qui nourrit à la fois l'analyse technique et le journal de progression.

**A — Adapter**

**[SCAMPER-A #1] : Gamification discrète de la régularité**
*Concept :* Indicateurs de progression subtils (streak d'entraînement, objectifs mensuels, paliers de sessions) visibles dans un coin du tableau de bord sans dominer l'interface. L'app reste un outil sérieux, pas un jeu.
*Nouveauté :* Inspiration Duolingo en "mode silencieux" — motivation par la régularité sans aspect infantilisant.

**[SCAMPER-A #2] : Journal de progression IA-guidé**
*Concept :* Après chaque session, l'IA pose 3 questions structurées (points forts, blocages, objectif suivant). Les réponses alimentent le journal ET enrichissent le moteur de coaching — croisement ressenti subjectif + données objectives.
*Nouveauté :* Le joueur devient co-auteur de son propre coaching. Effet flywheel : plus il renseigne, plus les recommandations sont fines.

**M — Modifier/Amplifier**

**[SCAMPER-M #1] : Notifications coaching ultra-contextualisées**
*Concept :* Les notifications croisent toutes les données disponibles (prochain tournoi, stats récentes, points faibles, historique) pour délivrer des messages actionnables et personnalisés — jamais génériques.
*Nouveauté :* Passe du "rappel passif" au "conseil actif au bon moment" — l'app parle comme un coach qui a étudié le dossier.

**E — Éliminer**

**[SCAMPER-E #1] : Éliminer le social et la dimension multi-utilisateurs**
*Concept :* SecondeServe est un outil personnel de progression. Pas de classements entre utilisateurs, pas de partage social, pas d'espace communautaire. Architecture mono-utilisateur assumée.
*Nouveauté :* Contrainte = force — chaque décision de design optimisée pour UN joueur spécifique, sans compromis de généralisation.

**R — Renverser**

**[SCAMPER-R #1] : Objectifs hybrides — IA + intention joueur**
*Concept :* L'utilisateur fixe ses propres axes de travail (lui ou son entraîneur humain), l'IA construit le plan pour les atteindre ET propose ses propres recommandations en parallèle. SecondeServe se positionne comme complément numérique au coach humain, pas substitut.
*Nouveauté :* L'app mémorise les consignes de l'entraîneur humain pour les intégrer dans ses suggestions.

---

### Six Thinking Hats

**Chapeau Blanc — Les Faits**

**[Chapeau Blanc #1] : Stack technique**
- Backend : Python (écosystème IA/ML optimal)
- Coaching textuel complexe : Mistral API
- Coaching on-device / offline : Gemini Nano (Pixel Tensor)
- Analyse vidéo : Cloud IA à la demande (Google/AWS) + MediaPipe CPU en batch sur VPS
- Frontend : Android natif (Kotlin)
- Infrastructure : VPS 4 vCPU / 16 Go RAM

**[Chapeau Blanc #2] : Architecture IA hybride — version finale**
- **On-device (Gemini Nano / Tensor)** : coaching temps réel en match, mode offline, latence zéro, sans coût API
- **Mistral API** : analyses complexes, bilans de progression, génération de plans d'entraînement, synthèse multi-sessions
- **Cloud vidéo (à la demande)** : traitement des clips uploadés
- **MediaPipe (VPS CPU)** : analyse posturale en batch post-entraînement

**[Chapeau Blanc #3] : Contrainte classement FFT**
- Pas d'API FFT accessible publiquement
- Profils de joueurs parfois privés (scraping non fiable)
- Saisie manuelle du classement officiel retenue
- Algorithme interne pour estimation temps réel en parallèle

**Chapeau Rouge — Les Émotions**

**[Chapeau Rouge #1] : Ton "coach de poche"**
*Concept :* L'app doit avoir une personnalité chaleureuse et humaine dans tous ses messages. Pas de données froides en premier plan. Le coach IA parle comme un vrai entraîneur : il encourage, recadre, célèbre, challenge.
*Nouveauté :* La personnalité du coach IA est une feature à part entière — ton, vocabulaire, empathie.

**[Chapeau Rouge #2] : Équilibre célébration / exigence**
*Concept :* Après une victoire, célébration sincère puis 1-2 axes d'amélioration naturels. Après une défaite : d'abord comprendre, puis rebondir. Le coach a une mémoire émotionnelle — adapte son registre au contexte tout en gardant le cap sur la progression long terme.
*Nouveauté :* Jamais condescendant, jamais complaisant.

**Chapeau Jaune — Les Bénéfices**

**[Chapeau Jaune #1] : Progression mesurable et usage régulier**
*Concept :* La valeur ultime est de générer une progression tennistique réelle et visible sur la durée. Le critère de succès n'est pas "l'app est riche en fonctionnalités" mais "mon tennis s'est amélioré grâce à elle."

**[Chapeau Jaune #2] : Le coach invisible en match**
*Concept :* SecondeServe comble le vide entre les séances avec le coach humain — présent là où le coach ne peut pas être : pendant les matchs. Coaching mental en temps réel là où l'utilisateur est seul face à lui-même.
*Nouveauté :* **Proposition de valeur unique et différenciante** — pas une feature parmi d'autres, la raison d'être de l'app.

**Chapeau Noir — Les Risques**

**[Chapeau Noir #1 — Mitigé] : Mode match ultra-minimal**
*Concept :* Un seul bouton "Changement de côté" — un tap déclenche le mode coaching. Zéro navigation, zéro distraction. 20 secondes d'attention max.
*Mitigation :* UX pensée pour le court, pas pour un bureau.

**[Chapeau Noir #2 — Mitigé] : Friction de saisie maîtrisée**
*Concept :* Combinaison "utilisateur engagé + pré-remplissage IA" réduit la charge à quelques validations rapides. Toutes les saisies en mode "valider ou corriger", jamais "remplir from scratch".

**Chapeau Vert — La Créativité**

**[Chapeau Vert #1] : Profil de style joueur évolutif**
*Concept :* SecondeServe construit progressivement un "ADN tennistique" (défenseur, attaquant, contre-puncheur...) basé sur stats, vidéos et résultats. Tous les conseils filtrés à travers ce profil — jamais génériques. Le profil évolue avec le joueur.
*Nouveauté :* L'identité tennistique devient un axe de progression à part entière.

**[Chapeau Vert #2] : Coaching adaptatif à l'état de forme**
*Concept :* L'app infère des signaux indirects (fréquence d'usage, heure, régularité) pour moduler l'intensité du coaching. En période de fatigue, ton plus doux et récupération mise en avant.
*Nouveauté :* Le coaching s'adapte à l'humain derrière le joueur, pas seulement aux stats tennistiques.

**Chapeau Bleu — Le Processus**

**[Chapeau Bleu #1] : V1 — Le trio indispensable**
1. **Coaching pendant les matchs** — mode changement de côté, conseils mentaux/physiques, un tap pour activer
2. **Suivi des matchs** — saisie des résultats, historique, stats de base, pré-remplissage IA
3. **Conseils assistés par IA** — recommandations personnalisées basées sur les données accumulées, ton coach bienveillant et exigeant

*V2 et au-delà : analyse vidéo, suggestions de tournois, classement temps réel, style joueur évolutif, gamification, journal IA-guidé*

---

### Question Storming — Inconnues critiques pour le PRD

**[QS #1] : IA on-device**
- Gemini Nano on-device couvre-t-il suffisamment les besoins de coaching textuel pour le mode match ?
- Quelle est la limite de contexte de Gemini Nano — peut-il mémoriser le profil joueur et l'historique du match en cours ?
- Le mode match tourne-t-il entièrement en local (Gemini Nano) pendant que Mistral API reste pour l'analyse approfondie hors-match ?
- Quelle est la stratégie de fallback si AICore n'est pas disponible ?
- Quelle est la frontière précise entre ce qui reste on-device et ce qui monte vers Mistral ?

**[QS #2] : Lancement de session**
- Quels sont les champs obligatoires minimaux pour lancer un match (surface ? adversaire ? tournoi ?) ?
- Le mode entraînement a-t-il ses propres champs spécifiques (exercice travaillé, avec coach humain ou solo ?) ?
- Comment l'app distingue-t-elle une session d'entraînement filmée d'un match filmé pour orienter l'analyse IA ?
- Le bouton "mode match" / "mode entraînement" est-il l'écran d'accueil principal ou accessible en 2 taps max ?

**[QS #3] : Classement officiel**
- La saisie du classement officiel FFT est-elle mensuelle ou à la demande ?
- Comment l'app gère-t-elle l'affichage côte à côte classement officiel vs classement estimé temps réel ?
- L'algorithme d'estimation doit-il reproduire fidèlement le système de points FFT ou rester indicatif ?
- Faut-il prévoir la saisie du numéro de licence FFT pour contextualiser le profil joueur ?

**[QS #4] : Analyse vidéo**
- Comment l'app détecte-t-elle automatiquement le type de coup filmé pour orienter l'analyse ?
- Quels sont les critères de qualité minimale d'une vidéo analysable (luminosité, distance, stabilité) ?
- Comment gérer les angles variables selon le type de coup filmé ?
- Faut-il guider l'utilisateur avant la prise de vue avec des instructions de positionnement ?
- L'analyse fonctionne-t-elle sur extraits courts (5-30s) ou vidéos longues à segmenter ?

**[QS #5] : Planification et notifications**
- Comment l'utilisateur saisit-il son calendrier (matchs et entraînements prévus) ?
- Quels types de notifications en lien avec le calendrier (rappel J-1, conseil de préparation, bilan attendu) ?
- À quelle fréquence les conseils réguliers sont-ils envoyés ?
- Les conseils réguliers sont-ils génériques ou personnalisés selon le profil et les axes de travail ?
- Comment éviter la notification fatigue — faut-il un mode "silencieux" avant les matchs importants ?

---

## Synthèse — Décisions clés de session

| Dimension | Décision |
|-----------|----------|
| **MVP V1** | Coaching match + Suivi matchs + Conseils IA |
| **Architecture IA** | Gemini Nano (on-device) + Mistral API (complexe) + Cloud vidéo (à la demande) |
| **Backend** | Python + FastAPI sur VPS 4 vCPU / 16 Go |
| **Frontend** | Android natif Kotlin |
| **Ton** | Coach humain — chaleureux, exigeant, jamais froid |
| **Utilisateurs** | Mono-utilisateur, zéro social |
| **UX match** | Ultra-minimale — 1 tap pour mode changement de côté |
| **Classement** | Saisie manuelle FFT + estimation temps réel interne |
| **Valeur unique** | Coach invisible pendant les matchs sans coach humain |
