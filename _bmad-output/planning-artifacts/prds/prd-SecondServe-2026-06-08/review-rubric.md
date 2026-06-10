# PRD Quality Review — SecondServe

## Overall verdict

Le PRD SecondServe est solide et prêt pour la chaîne en aval (architecture → épics → stories). La vision est précise et différenciée, les FRs sont presque tous accompagnés de conséquences testables spécifiques, et la cohérence strategique est forte du début à la fin. Les dimensions critiques — clarté du "done", honnêteté du scope, utilisabilité aval — sont à niveau **adequate** avec des gaps ciblés et corrigibles avant la première session architecture. Aucune dimension n'est cassée, aucun finding critique. Le principal effort avant handoff : combler les testable consequences manquantes sur le chemin de fallback statique (FR-4) et renommer FR-10b pour restaurer la continuité des IDs.

---

## Decision-readiness — strong

Le PRD formule les décisions comme des décisions, pas comme des "considérations". Les choix structurants sont explicites et tracés : Bluetooth-seul pour la Watch (OQ-6 fermé, addendum), Gemini Nano primaire avec deux niveaux de fallback (NFR §4.1), 3 axes maximum (FR-11), heuristique V1 pour le Style inféré (FR-14), saisie manuelle FFT (§5 Non-Goals). La table OQ (§8) ferme les six questions ouvertes avec une référence directe à la décision atterrissée. L'Index des Assumptions (§9) est cohérent avec les balises inline — roundtrip propre (7/7).

Les trade-offs sont nommés honnêtement. L'addendum documente ce qui a été éliminé (coaching vocal, suivi jeux-seulement) avec la raison. Un seul point où la narrativité décisionnelle pourrait être enrichie : la limite à 3 Axes de travail (FR-11) est taguée `[ASSUMPTION]` avec un rationale court ("éviter la dilution") — une note de décision remplacerait mieux l'assumption ici, car le choix n'est plus incertain, il est fait.

### Findings

*(Aucun finding critique ou élevé sur cette dimension.)*

- **low** Limite 3 axes taguée ASSUMPTION alors que la décision est prise (§4.3 FR-11) — `[ASSUMPTION : limite à 3 pour éviter la dilution du coaching]` devrait être une décision documentée plutôt qu'une hypothèse non confirmée. *Fix :* Supprimer la balise ASSUMPTION, retirer l'entrée de §9, et remplacer par un commentaire décisionnel court dans FR-11 ou dans le decision-log.

---

## Substance over theater — strong

La Vision (§1) est non-générique : les trois fronts de différenciation (coaching temps réel en match, intégration FFT, mono-utilisateur radical) sont ancrés dans des contraintes produit réelles, pas dans du marketing copy. Les UJs utilisent Benny comme protagoniste réel avec circonstances précises (30/2, championnat par équipes, seul sur le court). Les NFRs portent des seuils produit-spécifiques (≤ 3s Gemini Nano, ≤ 500ms Watch, ≥ 20 conseils fallback statique, ≤ 10s Mistral API) — zéro boilerplate "doit être scalable/sécurisé". Le Glossaire couvre le vocabulaire FFT natif non-trivial (séries, tie-break, super tie-break, Axe de travail).

Seul risque de théâtre : la section "Ton & Personnalité du Coach IA" (fin de document). Les cinq attributs (chaleureux, jamais générique, court, jamais condescendant, mémoriel) sont bien définis mais aucun FR ni aucune conséquence testable ne les mesure. La section gagne sa place comme intention de design pour l'auteur des prompts, mais sans ancrage testable elle est invisible aux story authors.

### Findings

- **medium** Section Ton & Personnalité sans testabilité (§ Ton & Personnalité) — Cinq attributs de voix coaching sont décrits sans critère mesurable ni référence à un FR existant. Les story authors n'ont aucun bar vérifiable. *Fix :* Soit ajouter 1-2 conséquences testables proxy dans FR-4 ou FR-10 (ex : "chaque Conseil généré fait référence à au moins l'un des éléments suivants dans son texte : score actuel, surface, Axe de travail actif, ou résultat de session récente — couvre l'attribut 'jamais générique' et 'mémoriel'") ; soit déplacer la section dans l'addendum comme guide de prompt design et extraire uniquement ce qui est déjà testé dans les FRs.

---

## Strategic coherence — strong

La thèse est claire et tenue d'un bout à l'autre : fermer le vide entre les séances avec un coach humain en apportant une intelligence de match en temps réel et une mémoire de session, ancré dans l'écosystème FFT. Chaque feature trace à cette thèse sans exception : Mode Match (coaching temps réel, cœur de valeur), Historique (fondation mémorielle), Conseils IA hors match (analyse de période), Profil joueur (carburant de personnalisation).

Les Success Metrics valident la thèse au bon niveau : SM-1 teste l'adoption en match (use case principal), SM-2 l'engagement hors match, SM-3 la qualité perçue des conseils. Les contre-métriques (SM-C1 temps d'interaction ≤ 15s, SM-C2 taux de désactivation notifications) nomment les risques d'over-optimisation corrects. Le scope MVP est cohérent : experience-first, mono-device, single-user.

Un léger bruit dans §6.2 : les entrées Out of Scope incluent des évaluations d'implémentation inline ("algorithme de simulation points FFT — travail non trivial, non bloquant pour V1") qui appartiennent à l'addendum, pas à la déclaration de scope du PRD.

### Findings

- **low** Notes techniques dans §6.2 Out of Scope (§6.2) — Des jugements d'implémentation ("travail non trivial, non bloquant pour V1") sont mélangés aux déclarations de scope. Ils ne nuisent pas à la cohérence mais ajoutent du bruit dans un section de PRD. *Fix :* Déplacer vers addendum § Options considérées — non retenues.

---

## Done-ness clarity — adequate

La grande majorité des FRs portent des "Conséquences testables" avec des conditions mesurables — FR-2 (logique de score point par point), FR-8 (seuil ≥ 3 matchs pour les stats par surface), FR-13 (liste exhaustive des formats de série FFT valides) sont exemplaires. La structure est cohérente et l'ingénieur qui lira ce PRD saura ce que "done" signifie pour la plupart des features.

Deux gaps à corriger avant la session architecture : (1) le chemin de fallback statique (bibliothèque ≥ 20 conseils, spécifiée dans le NFR de FR-4) n'a aucune conséquence testable couvrant son déclenchement ou son contenu — il existe sur papier mais aucun test ne le valide. (2) "Le Conseil fait référence à au moins un élément spécifique" dans FR-4 laisse "générique" à l'interprétation du reviewer ; une définition concrète des éléments éligibles rendrait ce critère non-ambigu.

### Findings

- **medium** Chemin de fallback statique non testé (§4.1 FR-4 NFR) — Le NFR "bibliothèque locale pré-définie (≥ 20 conseils)" est spécifié mais aucune conséquence testable ne couvre (a) le déploiement correct de la bibliothèque, (b) le déclenchement du fallback quand AICore est indisponible, (c) que le message retourné est bien issu de la bibliothèque statique. *Fix :* Ajouter à FR-4 Conséquences testables : "Avec Android AICore simulé indisponible et sans réseau, le Conseil affiché est tiré de la bibliothèque locale statique (vérifiable : le contenu correspond à l'un des messages prédéfinis de la bibliothèque)."

- **medium** "Générique" non défini dans FR-4 testable consequences (§4.1 FR-4) — "Le Conseil fait référence à au moins un élément spécifique au match en cours ou au Profil joueur (pas de message générique)" est testable en principe mais "générique" reste ouvert à interprétation. *Fix :* Préciser : "au moins l'un des éléments suivants est nommé dans le texte du Conseil : score de jeux actuel, surface, Axe de travail actif, ou résultat d'une Session récente."

- **low** NFRs sans identifiants (§ NFRs Transversaux) — Les NFRs de performance, offline, sync, UX et plateforme sont organisés par catégorie sans identifiants (ex. NFR-P1, NFR-UX1). Les stories ne pourront pas croiser-référencer un NFR précis par ID. *Fix :* Ajouter une colonne ID aux tables NFR.

---

## Scope honesty — adequate

La section Non-Goals (§5) est explicite, spécifique et alignée avec §6.2. Les balises `[ASSUMPTION]` inline sont présentes et indexées au §9 sans gap de roundtrip (7/7). La table OQ ferme les 6 questions avec références directes. L'absence de callouts `[NOTE FOR PM]` est acceptable dans un produit solo où PM = développeur = utilisateur.

Un risque de scope silencieusement effacé : OQ-6 est résolu en "Bluetooth-seul, téléphone < 10m" mais le scénario d'un court de tournoi exigeant de déposer le téléphone dans un vestiaire à plus de 10m n'est pas nommé comme risque résiduel. Il est absorbé dans l'addendum comme décision technique (Option A retenue), mais le PRD n'en fait pas mention alors que c'est une contrainte d'usage réelle pour Benny.

### Findings

- **low** Contrainte portée Bluetooth non nommée dans le PRD (§4.1 FR-2, addendum OQ-6) — La résolution de OQ-6 suppose "téléphone dans le sac, distance < 5m" mais le scénario vestiaire > 10m est une inférence implicite du lecteur, pas une déclaration de scope. *Fix :* Ajouter en §5 Non-Goals ou en note FR-2 : "Le Mode Match suppose le téléphone à portée Bluetooth (< 10m) ; les tournois imposant un vestiaire hors portée sont hors V1."

---

## Downstream usability — adequate

Le Glossaire (§3) définit 12 termes et est utilisé de façon cohérente. La traçabilité UJ → Feature ("Réalise UJ-1") sur chaque bloc feature est un aide précieuse pour l'extraction en stories. Les IDs FR, UJ, SM sont présents et les références croisées résolvent. La section §8 Open Questions et §9 Assumptions Index sont des anchors solides pour la relecture d'architecture.

Deux problèmes affectent l'extraction au niveau story : FR-10b utilise un suffixe "b" non-standard qui casse la continuité de séquence (FR-10 → FR-10b → FR-11 → … → FR-15). Les story authors et les outils BMad s'attendent à une séquence entière. Second point : le NFR UX "toute action en match accessible en ≤ 1 tap" n'est référencé dans aucune conséquence testable de FR-2 ou FR-3, laissant un gap pour la story qui implémente le design d'interaction Watch.

### Findings

- **medium** FR-10b casse la séquence d'IDs (§4.3) — Un suffixe "b" crée un identifiant non-continu qui peut poser problème aux outils et à la relecture manuelle. *Fix :* Renommer en FR-11 (synthèse multi-matchs) et décaler FR-11 → FR-12 à FR-15 → FR-16. Mettre à jour §6.1 et les références croisées.

- **low** NFR "1 tap" non ancré dans les conséquences testables (NFRs UX en match, §4.1 FR-2/FR-3) — "Toute action en match accessible en ≤ 1 tap" est déclarée en NFR mais aucune conséquence testable de FR-2 ou FR-3 ne la couvre explicitement. *Fix :* Ajouter à FR-2 ou FR-3 Conséquences testables : "Chaque action de saisie de score et de déclenchement du Conseil est accessible depuis l'écran actif en exactement 1 tap, sans navigation préalable."

---

## Shape fit — strong

Le PRD applique correctement la forme consumer/personal-product avec protagonist nommé portant le contexte inline dans chaque UJ. Les trois UJs couvrent les boucles primaires sans surcharge (coaching match, analyse inter-match, mise à jour classement). La rigueur est calibrée pour un PRD de tête de chaîne (chain-top) qui alimente architecture → épics → stories : le niveau de spécificité des FRs et des conséquences testables est approprié à ce handoff. Le design mono-utilisateur élimine le besoin de UJs multi-stakeholders.

Note de calibration : le niveau de rigueur dépasse le "hobby/solo" même si le déploiement est personnel — c'est le bon choix car ce PRD alimente une chaîne BMad complète. Le PRD correspond à son usage réel.

### Findings

*(Aucun finding sur cette dimension.)*

---

## Mechanical notes

- **Roundtrip Assumptions :** §9 liste 7 entrées ; 7 balises `[ASSUMPTION]` sont présentes inline. Roundtrip propre. À noter : la balise FR-11 (limite 3 axes) et FR-14 (heuristique Style) sont les seules qui méritent d'être converties en décisions actées plutôt qu'en assumptions (cf. Decision-readiness finding).
- **Protagoniste UJ :** Les trois UJs nomment "Benny" avec contexte spécifique. Propre.
- **Glossaire :** Aucune dérive de casse ou de synonyme détectée. "Session", "Mode Match", "Axe de travail", "Conseil", "Sync" sont capitalisés de façon cohérente partout.
- **Sections requises :** Toutes les sections attendues pour un PRD consumer chain-top sont présentes (Vision, UJs, Glossaire, Features + FRs + conséquences testables, Non-Goals, Scope MVP, Success Metrics + contre-métriques, Open Questions, Index Assumptions, NFRs).
- **Numérotation FR-10b :** Signalé dans Downstream usability — seul gap de continuité détecté.
