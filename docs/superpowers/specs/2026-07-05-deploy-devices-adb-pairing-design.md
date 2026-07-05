# Association ADB (pairing) en dernier recours — Design

## Contexte

Le "débogage sans fil" moderne d'Android (11+, seul mode disponible sur la Pixel Watch qui n'a pas de port USB data) sépare deux étapes distinctes :

1. **Association (pairing)** — écran "Associer un appareil" : affiche une IP:port *différente* du port de connexion habituel, plus un code à 6 chiffres, à usage unique. Établit une confiance persistante entre l'ordinateur et l'appareil (`adb pair <ip>:<port> <code>`).
2. **Connexion** — écran principal "Débogage sans fil" : affiche l'IP:port de connexion, utilisable seulement *après* une association réussie depuis cette machine (`adb connect <ip>:<port>`).

`scripts/deploy-devices.sh` (et son support du port variable ajouté le même jour, voir `docs/superpowers/specs/2026-07-05-deploy-devices-phone-wifi-design.md`) ne gère que l'étape 2. Sans association préalable réussie depuis la machine locale, toute tentative de connexion échoue silencieusement (le script la traite comme "appareil introuvable"), quels que soient l'IP et le port renseignés — c'est le bug remonté par Benny : mettre l'IP:port affiché ne suffit pas.

## Objectif

Ajouter un palier de dernier recours, symétrique pour le phone et la watch : quand la connexion échoue avec l'adresse déjà connue (config) ou saisie interactivement, proposer d'associer l'appareil (IP:port d'association + code), puis retenter la connexion sur l'adresse de connexion d'origine.

## Flux détaillé (identique pour phone et watch, seuls les noms de variables diffèrent)

Après l'échec des deux tentatives déjà existantes (config IP, puis saisie interactive de l'IP de connexion) :

1. **Condition de déclenchement** : ce palier ne s'active que si une adresse de connexion a été *tentée* (via config ou saisie) mais a échoué — pas si l'utilisateur a explicitement sauté la saisie de l'IP (rien à retenter ensuite).
2. **Message** : explique la distinction association/connexion, avec le chemin exact dans les paramètres développeur, et demande une seule ligne combinant adresse d'association et code, séparés par un espace (ex. `192.168.1.5:41235 123456`), vide pour sauter.
3. **Saisie** : `read -r PAIR_ADDR PAIR_CODE` — bash découpe automatiquement l'entrée sur l'espace en deux variables, sans logique de parsing supplémentaire.
4. **Association** : si les deux valeurs sont non vides, `adb pair "$PAIR_ADDR" "$PAIR_CODE"`. L'adresse d'association n'est **jamais sauvegardée** dans `deploy-devices.conf` (usage unique, régénérée à chaque ouverture de l'écran "Associer un appareil").
5. **Nouvelle tentative de connexion** : si l'association réussit (code de sortie 0), retente `adb connect`/`get-state` sur l'**adresse de connexion** déjà tentée à l'étape précédente (celle envoyée par l'utilisateur ou tirée de la config) — pas l'adresse de pairing. En cas de succès, sauvegarde de cette adresse de connexion dans la config comme le fait déjà le palier de saisie interactive existant.
6. **Échec** : si l'association échoue, ou si l'utilisateur saute cette étape, message d'échec et appareil sauté — comportement final inchangé.

## Interface web locale

Nouveau marqueur machine-lisible, même mécanisme que les marqueurs existants : `AWAITING_INPUT:PHONE_PAIR` / `AWAITING_INPUT:WATCH_PAIR`, émis sur stderr juste avant le nouveau `read -r PAIR_ADDR PAIR_CODE`. Aucun changement structurel côté serveur (`scripts/deploy-ui/server.js`) : la saisie reste une seule valeur texte transmise via `POST /api/deploy/input`, l'utilisateur tapant l'adresse et le code sur une seule ligne dans le champ existant, exactement comme au terminal.

Dans `scripts/deploy-ui/public/app.js`, ajout de libellés dédiés pour ces deux nouveaux marqueurs (au lieu du fallback générique `Valeur attendue (...)`), avec une indication explicite du format attendu ("adresse:port code").

## Documentation

Mise à jour de la section watch de `android/DEPLOY.md` pour expliquer la distinction association vs connexion, et mentionner que le script gère maintenant lui-même l'association en cas d'échec de connexion.

## Hors périmètre

- Pas de sauvegarde de l'adresse ou du code d'association (usage unique par nature).
- Pas de détection automatique de la nécessité d'association avant d'essayer la connexion — le script tente toujours la connexion directe en premier (chemin le plus rapide si déjà associé), l'association n'est proposée qu'en cas d'échec.
- Pas de mécanisme de retry automatique au-delà de la tentative unique post-association (si elle échoue encore, l'utilisateur relance le script).
