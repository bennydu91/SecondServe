# Validation Report — SecondServe

- **PRD :** `_bmad-output/planning-artifacts/prds/prd-SecondServe-2026-06-08/prd.md`
- **Rubric :** `.claude/skills/bmad-prd/assets/prd-validation-checklist.md`
- **Run at :** 2026-06-08T00:00:00Z
- **Grade :** Good

## Overall verdict

Le PRD SecondServe est solide et prêt pour la chaîne en aval (architecture → épics → stories). La vision est précise et différenciée, les FRs sont presque tous accompagnés de conséquences testables spécifiques, et la cohérence stratégique est forte du début à la fin. Les dimensions critiques — clarté du "done", honnêteté du scope, utilisabilité aval — sont à niveau **adequate** avec des gaps ciblés et corrigibles avant la première session architecture.

Aucune dimension n'est cassée, aucun finding critique ou élevé. Le principal effort avant handoff : combler les conséquences testables manquantes sur le chemin de fallback statique (FR-4), clarifier la définition de "générique" dans FR-4, et renommer FR-10b pour restaurer la continuité des IDs.

## Dimension verdicts

- Decision-readiness — **strong**
- Substance over theater — **strong**
- Strategic coherence — **strong**
- Done-ness clarity — **adequate**
- Scope honesty — **adequate**
- Downstream usability — **adequate**
- Shape fit — **strong**

## Findings by severity

### Critical (0)

*(Aucun finding critique.)*

### High (0)

*(Aucun finding élevé.)*

### Medium (4)

**[Substance over theater]** — Section Ton & Personnalité sans testabilité (§ Ton & Personnalité du Coach IA)
Cinq attributs de voix coaching sont décrits sans critère mesurable ni référence à un FR existant. Les story authors n'ont aucun bar vérifiable.
Fix : Ajouter 1-2 conséquences testables proxy dans FR-4 ou FR-10 — ex : "chaque Conseil généré nomme au moins l'un des éléments suivants : score actuel, surface, Axe de travail actif, ou résultat de session récente". Ou déplacer la section dans l'addendum comme guide de prompt design.

**[Done-ness clarity]** — Chemin de fallback statique non testé (§4.1 FR-4 NFR)
Le NFR "bibliothèque locale pré-définie (≥ 20 conseils)" est spécifié mais aucune conséquence testable ne couvre (a) le déploiement de la bibliothèque, (b) le déclenchement du fallback quand AICore est indisponible, (c) que le message retourné est bien issu de la bibliothèque statique.
Fix : Ajouter à FR-4 Conséquences testables : "Avec Android AICore simulé indisponible et sans réseau, le Conseil affiché est tiré de la bibliothèque locale statique (vérifiable : le contenu correspond à l'un des messages prédéfinis de la bibliothèque)."

**[Done-ness clarity]** — "Générique" non défini dans FR-4 testable consequences (§4.1 FR-4)
"Le Conseil fait référence à au moins un élément spécifique au match en cours ou au Profil joueur (pas de message générique)" laisse "générique" ouvert à interprétation.
Fix : Préciser : "au moins l'un des éléments suivants est nommé dans le texte du Conseil : score de jeux actuel, surface, Axe de travail actif, ou résultat d'une Session récente."

**[Downstream usability]** — FR-10b casse la séquence d'IDs (§4.3)
Un suffixe "b" crée un identifiant non-continu (FR-10 → FR-10b → FR-11 → … → FR-15) qui peut poser problème aux outils et à la relecture manuelle.
Fix : Renommer en FR-11 (synthèse multi-matchs) et décaler FR-11 → FR-12 à FR-15 → FR-16. Mettre à jour §6.1 et les références croisées.

### Low (5)

**[Decision-readiness]** — Limite 3 axes taguée ASSUMPTION alors que la décision est prise (§4.3 FR-11)
`[ASSUMPTION : limite à 3 pour éviter la dilution du coaching]` devrait être une décision documentée plutôt qu'une hypothèse non confirmée.
Fix : Supprimer la balise ASSUMPTION et l'entrée §9 correspondante. Remplacer par un commentaire décisionnel court dans FR-11 ou le decision-log.

**[Strategic coherence]** — Notes techniques mélangées dans §6.2 Out of Scope (§6.2)
Des jugements d'implémentation ("algorithme de simulation points FFT — travail non trivial, non bloquant pour V1") sont inclus dans des déclarations de scope PRD.
Fix : Déplacer vers addendum § Options considérées — non retenues.

**[Done-ness clarity]** — NFRs sans identifiants (§ NFRs Transversaux)
Les NFRs de performance, offline, sync, UX et plateforme sont organisés sans identifiants (ex. NFR-P1, NFR-UX1). Les stories ne pourront pas croiser-référencer un NFR précis par ID.
Fix : Ajouter une colonne ID aux tables NFR.

**[Scope honesty]** — Contrainte portée Bluetooth non nommée dans le PRD (§4.1 FR-2, addendum OQ-6)
La résolution de OQ-6 suppose "téléphone dans le sac, distance < 5m" mais le scénario vestiaire > 10m est une inférence implicite du lecteur, pas une déclaration de scope.
Fix : Ajouter en §5 Non-Goals ou en note FR-2 : "Le Mode Match suppose le téléphone à portée Bluetooth (< 10m) ; les tournois imposant un vestiaire hors portée sont hors V1."

**[Downstream usability]** — NFR "1 tap" non ancré dans les conséquences testables (NFRs UX en match, §4.1 FR-2/FR-3)
"Toute action en match accessible en ≤ 1 tap" est déclarée en NFR mais aucune conséquence testable de FR-2 ou FR-3 ne la couvre explicitement.
Fix : Ajouter à FR-2 ou FR-3 Conséquences testables : "Chaque action de saisie de score et de déclenchement du Conseil est accessible depuis l'écran actif en exactement 1 tap, sans navigation préalable."

## Mechanical notes

- Roundtrip Assumptions : §9 liste 7 entrées ; 7 balises `[ASSUMPTION]` inline. Roundtrip propre. À noter : les assumptions FR-11 (limite 3 axes) et FR-14 (heuristique Style) méritent d'être converties en décisions actées.
- Protagoniste UJ : Les trois UJs nomment "Benny" avec contexte spécifique. Propre.
- Glossaire : Aucune dérive de casse ou de synonyme détectée. "Session", "Mode Match", "Axe de travail", "Conseil", "Sync" capitalisés de façon cohérente partout.
- Sections requises : Toutes les sections attendues pour un PRD consumer chain-top sont présentes.
- Numérotation : FR-10b est le seul gap de continuité détecté.

## Reviewer files

- `review-rubric.md`
