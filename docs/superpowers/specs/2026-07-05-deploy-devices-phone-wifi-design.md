# ADB WiFi pour le phone — Design

## Contexte

`scripts/deploy-devices.sh` (voir `docs/superpowers/specs/2026-07-05-deploy-devices-script-design.md`) gère déjà le débogage sans fil pour la Pixel Watch : une IP optionnelle en config (`WATCH_IP`), un fallback vers une connexion déjà active, une saisie interactive en dernier recours, et une sauvegarde automatique de l'IP saisie. Le Pixel 9 Pro (Android 11+) supporte le même mécanisme natif de "débogage sans fil" (appairage par code, connexion persistante tant que l'appareil reste sur le même réseau WiFi). Ce chantier étend le script pour détecter et installer sur le phone aussi bien via USB que via WiFi, sans jamais perdre la détection USB existante.

## Objectif

- Ajouter une clé `PHONE_IP` optionnelle à `scripts/deploy-devices.conf` / `.example`, avec le même comportement best-effort que `WATCH_IP` (connexion déjà active → IP config → saisie interactive → sauvegarde).
- Garantir que **si un phone est branché en USB, il est toujours utilisé en priorité**, même si une entrée WiFi (`ip:5555`) pour un phone est aussi visible dans `adb devices` au même moment.
- Généraliser `save_watch_ip_to_config(conf_file, ip)` en `save_ip_to_config(conf_file, var_name, ip)` pour servir aux deux cas (watch et phone) sans dupliquer la logique de mise à jour de fichier.

## Détection et priorité USB > WiFi

Le scan actuel (`scripts/deploy-devices.sh`, boucle sur `SERIALS`) classe chaque serial connu d'`adb devices` en `watch`/`phone` via `classify_by_characteristics`, puis affecte le dernier serial trouvé à `PHONE_SERIAL`/`WATCH_SERIAL` (dernier gagne, sans autre règle).

Nouvelle règle pour le phone uniquement : un serial est considéré "USB" s'il ne contient pas de `:` (un serial WiFi est toujours de la forme `ip:5555`). Lors du scan :
- Si un serial USB est trouvé, il écrase toujours `PHONE_SERIAL` (même si une entrée WiFi a été vue avant ou après).
- Si aucun serial USB n'est trouvé mais qu'une entrée WiFi phone existe, elle est utilisée.

Ça ne change rien à la classification watch (une seule watch attendue, pas de notion USB pour elle).

## Fallback WiFi phone (si aucun serial phone, USB ou WiFi, n'est déjà visible)

Identique au flux watch existant :
1. Si `PHONE_IP` est renseigné en config, tenter `adb connect $PHONE_IP:5555`, vérifier avec `adb -s $PHONE_IP:5555 get-state`.
2. Si toujours introuvable, demander l'IP interactivement (message adapté : "Adresse IP du phone"), tenter la connexion, et si elle réussit, sauvegarder via `save_ip_to_config "$CONF_FILE" "PHONE_IP" "$INPUT_IP"`.
3. Si le phone reste introuvable après ces tentatives : avertissement, installation phone sautée (comportement inchangé, `PHONE_STATUS="sauté (non détecté)"`).

## Généralisation de `save_watch_ip_to_config`

Nouvelle signature : `save_ip_to_config(conf_file, var_name, ip)`. Comportement identique à l'actuel (met à jour la ligne `${var_name}=` existante ou l'ajoute), mais paramétré par le nom de variable au lieu de `WATCH_IP` en dur. L'appel existant pour la watch devient `save_ip_to_config "$CONF_FILE" "WATCH_IP" "$INPUT_IP"` ; un nouvel appel identique est ajouté pour `PHONE_IP`. Les tests existants de `save_watch_ip_to_config` sont adaptés au nouveau nom/signature sans changement de comportement testé.

## Hors périmètre

- Pas de nouveau flag CLI (la détection reste automatique, comme pour la watch).
- Pas de gestion de plusieurs phones/watches simultanés au-delà de la règle USB > WiFi ci-dessus.
- Le mécanisme d'appairage initial du débogage sans fil (code affiché sur le phone, `adb pair`) reste manuel, hors script — déjà le cas pour la watch et documenté dans `android/DEPLOY.md`.
