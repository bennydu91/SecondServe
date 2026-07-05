# Script de déploiement automatisé sur devices — Design

## Contexte

Le workflow actuel (`android/DEPLOY.md`) est entièrement manuel : build Gradle sur le VPS (`/root/SecondServe`), rapatriement d'APK via `scp` vers le poste local, puis installation via `adb` (les devices — Pixel 9 Pro en USB, Pixel Watch en ADB WiFi — ne sont jamais branchés au VPS). Ce script automatise l'ensemble de ce cycle depuis une seule commande, lancée sur le poste local.

## Objectif

Un script bash unique, `scripts/deploy-devices.sh`, exécuté sur le **poste local** de Benny, qui :
1. déclenche le build Gradle sur le VPS via SSH,
2. rapatrie les APK produits via `scp`,
3. détecte et installe sur le Pixel 9 Pro (USB) et/ou la Pixel Watch (ADB WiFi), indépendamment l'un de l'autre,
4. affiche un résumé clair de ce qui a été fait ou sauté.

Le script est committé dans le repo Git (donc versionné et documenté aux côtés de `android/DEPLOY.md`), mais son usage réel se fait depuis une copie/checkout du repo sur le poste local de Benny (pas sur le VPS).

## Fichier de configuration

`scripts/deploy-devices.conf` — non commité (ajouté à `.gitignore`), chargé par le script via `source`. Un template `scripts/deploy-devices.conf.example` est commité pour documenter le format :

```bash
VPS_HOST=<ip-ou-domaine>
VPS_USER=root
VPS_SSH_PORT=22
VPS_REPO_PATH=/root/SecondServe
WATCH_IP=192.168.x.x   # optionnel — best-effort, voir section détection
```

Si `WATCH_IP` est absent du fichier et qu'aucune montre n'est trouvée déjà connectée via `adb devices`, le script demande l'IP interactivement, puis **propose de la sauvegarder** dans `deploy-devices.conf` pour les exécutions suivantes (écriture automatique si l'utilisateur confirme).

## Flags CLI

| Flag | Effet |
|---|---|
| `--phone-only` | Ne build/installe que sur le Pixel 9 Pro (`:app`) |
| `--watch-only` | Ne build/installe que sur la Pixel Watch (`:wear`) |
| `--release` | Build `assembleRelease` au lieu de `assembleStaging` pour `:app` (nécessite `KEYSTORE_PASSWORD` et `KEY_PASSWORD` dans l'environnement local, transmises à la commande SSH distante) |

Sans flag : les deux devices sont ciblés, `:app:assembleStaging` + `:wear:assembleDebug`.

## Déroulé

1. **Chargement config** — source `deploy-devices.conf`, valide `VPS_HOST`/`VPS_USER`/`VPS_SSH_PORT`/`VPS_REPO_PATH`. Erreur bloquante si absents (le build ne peut pas démarrer sans ça).
2. **Build distant** — une seule connexion SSH vers le VPS lance les tâches Gradle nécessaires selon les flags (`:app:assembleStaging`/`assembleRelease` et/ou `:wear:assembleDebug`). Échec du build → le script s'arrête (rien à rapatrier).
3. **Rapatriement** — `scp` des APK produits vers `./deploy-artifacts/` en local (créé si absent, gitignored).
4. **Installation Pixel 9 Pro** (si ciblé) :
   - Cherche un device USB à l'état `device` dans `adb devices`.
   - Absent → avertissement, le script continue (n'interrompt pas l'installation watch).
   - Présent → `adb install -r app-staging.apk` (ou `app-release.apk`), puis `adb shell pm grant com.secondserve android.permission.POST_NOTIFICATIONS`.
5. **Installation Pixel Watch** (si ciblée) :
   - Cherche une entrée `<ip>:5555` déjà à l'état `device` dans `adb devices` (connexion WiFi déjà active).
   - Sinon, tente `adb connect <WATCH_IP>:5555` avec l'IP de la config.
   - Si `WATCH_IP` absent ou connexion échouée → demande l'IP interactivement, retente `adb connect`.
   - Échec final → avertissement, le script continue.
   - Succès → `adb -s <ip>:5555 install -r wear-debug.apk`.
6. **Résumé** — récapitulatif final : ce qui a été buildé, rapatrié, installé sur quel device, et ce qui a été sauté avec la raison (device non trouvé, connexion échouée, etc.).

## Gestion d'erreurs

- Build ou scp en échec → arrêt total (rien à installer, erreur explicite).
- Échec d'installation sur un device (non trouvé/non joignable) → avertissement, ne bloque pas l'autre device. Le script se termine avec un code de sortie non-nul si au moins une installation ciblée a échoué, 0 sinon.
- Toute installation utilise `-r` (conserve les données Room/JWT existantes) — jamais de désinstallation préalable.

## Hors périmètre

- Configuration Google Sign-In / SHA-1 (setup one-shot déjà documenté dans `DEPLOY.md`, pas un besoin répété à chaque déploiement).
- Génération du keystore release (déjà fait une fois, documenté).
- Logcat / vérification post-installation (le script installe ; le suivi des logs reste manuel via les commandes déjà documentées dans `DEPLOY.md`).
