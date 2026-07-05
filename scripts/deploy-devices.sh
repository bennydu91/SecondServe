#!/usr/bin/env bash
#
# deploy-devices.sh — Build sur le VPS, rapatrie les APK, installe sur le
# Pixel 9 Pro (USB ou ADB WiFi) et/ou la Pixel Watch (ADB WiFi).
#
# Usage :
#   ./deploy-devices.sh                # build + install phone et watch (staging/debug)
#   ./deploy-devices.sh --phone-only   # ne cible que le Pixel 9 Pro
#   ./deploy-devices.sh --watch-only   # ne cible que la Pixel Watch
#   ./deploy-devices.sh --release      # build app:assembleRelease (nécessite
#                                      # KEYSTORE_PASSWORD et KEY_PASSWORD dans l'env)
#
# Prérequis : adb + ssh + scp dans le PATH, scripts/deploy-devices.conf renseigné
# (copier deploy-devices.conf.example). À lancer sur TON ordinateur local (pas sur le VPS).

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONF_FILE="$SCRIPT_DIR/deploy-devices.conf"
ARTIFACTS_DIR="$SCRIPT_DIR/../deploy-artifacts"

# shellcheck source=lib/deploy-devices-lib.sh
source "$SCRIPT_DIR/lib/deploy-devices-lib.sh"

# --- Vérifications de base ---
for bin in adb ssh scp; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "❌ '$bin' introuvable dans le PATH." >&2
    exit 1
  fi
done

if [ ! -f "$CONF_FILE" ]; then
  echo "❌ Fichier de config introuvable : $CONF_FILE" >&2
  echo "   Copie scripts/deploy-devices.conf.example vers scripts/deploy-devices.conf et renseigne-le." >&2
  exit 1
fi
# shellcheck source=/dev/null
source "$CONF_FILE"

if ! validate_config; then
  exit 1
fi

if ! parse_deploy_args "$@"; then
  exit 1
fi

if [ "$BUILD_VARIANT" = "release" ] && { [ -z "${KEYSTORE_PASSWORD:-}" ] || [ -z "${KEY_PASSWORD:-}" ]; }; then
  echo "❌ --release nécessite KEYSTORE_PASSWORD et KEY_PASSWORD dans l'environnement." >&2
  exit 1
fi

# --- Détermine les tâches Gradle et chemins d'APK selon les cibles ---
GRADLE_TASKS=""
APP_APK_PATH=""
WEAR_APK_PATH="wear/build/outputs/apk/debug/wear-debug.apk"

if [ "$TARGET_PHONE" = "1" ]; then
  if [ "$BUILD_VARIANT" = "release" ]; then
    GRADLE_TASKS="$GRADLE_TASKS :app:assembleRelease"
    APP_APK_PATH="app/build/outputs/apk/release/app-release.apk"
  else
    GRADLE_TASKS="$GRADLE_TASKS :app:assembleStaging"
    APP_APK_PATH="app/build/outputs/apk/staging/app-staging.apk"
  fi
fi
if [ "$TARGET_WATCH" = "1" ]; then
  GRADLE_TASKS="$GRADLE_TASKS :wear:assembleDebug"
fi

echo "🏗️  Build distant sur $VPS_HOST : gradlew$GRADLE_TASKS"
REMOTE_ENV=""
if [ "$BUILD_VARIANT" = "release" ] && [ "$TARGET_PHONE" = "1" ]; then
  REMOTE_ENV="KEYSTORE_PASSWORD='$KEYSTORE_PASSWORD' KEY_PASSWORD='$KEY_PASSWORD'"
fi

if ! ssh -p "$VPS_SSH_PORT" "$VPS_USER@$VPS_HOST" \
  "cd '$VPS_REPO_PATH/android' && $REMOTE_ENV ./gradlew$GRADLE_TASKS"; then
  echo "❌ Le build a échoué sur le VPS." >&2
  exit 1
fi

# --- Rapatriement des APK ---
mkdir -p "$ARTIFACTS_DIR"

if [ "$TARGET_PHONE" = "1" ]; then
  echo "📦 Rapatriement de l'APK phone…"
  if ! scp -P "$VPS_SSH_PORT" "$VPS_USER@$VPS_HOST:$VPS_REPO_PATH/android/$APP_APK_PATH" "$ARTIFACTS_DIR/"; then
    echo "❌ Échec du rapatriement de l'APK phone." >&2
    exit 1
  fi
fi
if [ "$TARGET_WATCH" = "1" ]; then
  echo "📦 Rapatriement de l'APK watch…"
  if ! scp -P "$VPS_SSH_PORT" "$VPS_USER@$VPS_HOST:$VPS_REPO_PATH/android/$WEAR_APK_PATH" "$ARTIFACTS_DIR/"; then
    echo "❌ Échec du rapatriement de l'APK watch." >&2
    exit 1
  fi
fi

# --- Scan des appareils déjà connectés et classification phone/watch ---
PHONE_SERIAL=""
WATCH_SERIAL=""
SERIALS=()
while IFS= read -r line; do
  [ -n "$line" ] && SERIALS+=("$line")
done < <(adb devices | filter_connected_serials)

for s in "${SERIALS[@]:-}"; do
  [ -n "$s" ] || continue
  chars="$(adb -s "$s" shell getprop ro.build.characteristics 2>/dev/null)"
  kind="$(classify_by_characteristics "$chars")"
  if [ "$kind" = "watch" ]; then
    WATCH_SERIAL="$s"
  else
    # Un serial WiFi est toujours de la forme ip:5555 ; un serial USB n'a pas
    # de ':'. L'USB gagne toujours, même si une entrée WiFi a été vue avant.
    case "$s" in
      *:*)
        [ -z "$PHONE_SERIAL" ] && PHONE_SERIAL="$s"
        ;;
      *)
        PHONE_SERIAL="$s"
        ;;
    esac
  fi
done

INSTALL_FAILED=0
PHONE_STATUS="non ciblé"
WATCH_STATUS="non ciblée"

# --- Installation Pixel 9 Pro ---
if [ "$TARGET_PHONE" = "1" ]; then
  if [ -z "$PHONE_SERIAL" ] && [ -n "${PHONE_IP:-}" ]; then
    echo "🔌 Tentative de connexion au phone ($PHONE_IP:5555)…"
    adb connect "$PHONE_IP:5555" >/dev/null 2>&1 || true
    if adb -s "$PHONE_IP:5555" get-state >/dev/null 2>&1; then
      PHONE_SERIAL="$PHONE_IP:5555"
    fi
  fi

  if [ -z "$PHONE_SERIAL" ]; then
    echo "📱 Phone non détecté (ni USB, ni IP configurée). Adresse IP du phone en WiFi (vide pour sauter) :"
    read -r INPUT_PHONE_IP
    if [ -n "$INPUT_PHONE_IP" ]; then
      adb connect "$INPUT_PHONE_IP:5555" >/dev/null 2>&1 || true
      if adb -s "$INPUT_PHONE_IP:5555" get-state >/dev/null 2>&1; then
        PHONE_SERIAL="$INPUT_PHONE_IP:5555"
        save_ip_to_config "$CONF_FILE" "PHONE_IP" "$INPUT_PHONE_IP"
        echo "💾 IP sauvegardée dans $(basename "$CONF_FILE")."
      fi
    fi
  fi

  if [ -z "$PHONE_SERIAL" ]; then
    echo "⚠️  Aucun téléphone détecté (USB ni WiFi) — installation phone sautée."
    INSTALL_FAILED=1
    PHONE_STATUS="sauté (non détecté)"
  else
    echo "📱 Installation sur le phone ($PHONE_SERIAL)…"
    if adb -s "$PHONE_SERIAL" install -r "$ARTIFACTS_DIR/$(basename "$APP_APK_PATH")"; then
      adb -s "$PHONE_SERIAL" shell pm grant com.secondserve android.permission.POST_NOTIFICATIONS 2>/dev/null || true
      echo "✅ Phone installé."
      PHONE_STATUS="installé ($PHONE_SERIAL)"
    else
      echo "⚠️  Échec de l'installation phone."
      INSTALL_FAILED=1
      PHONE_STATUS="échec ($PHONE_SERIAL)"
    fi
  fi
fi

# --- Installation Pixel Watch ---
if [ "$TARGET_WATCH" = "1" ]; then
  if [ -z "$WATCH_SERIAL" ] && [ -n "${WATCH_IP:-}" ]; then
    echo "🔌 Tentative de connexion à la watch ($WATCH_IP:5555)…"
    adb connect "$WATCH_IP:5555" >/dev/null 2>&1 || true
    if adb -s "$WATCH_IP:5555" get-state >/dev/null 2>&1; then
      WATCH_SERIAL="$WATCH_IP:5555"
    fi
  fi

  if [ -z "$WATCH_SERIAL" ]; then
    echo "⌚ Watch non détectée. Adresse IP de la montre (vide pour sauter) :"
    read -r INPUT_IP
    if [ -n "$INPUT_IP" ]; then
      adb connect "$INPUT_IP:5555" >/dev/null 2>&1 || true
      if adb -s "$INPUT_IP:5555" get-state >/dev/null 2>&1; then
        WATCH_SERIAL="$INPUT_IP:5555"
        save_ip_to_config "$CONF_FILE" "WATCH_IP" "$INPUT_IP"
        echo "💾 IP sauvegardée dans $(basename "$CONF_FILE")."
      fi
    fi
  fi

  if [ -z "$WATCH_SERIAL" ]; then
    echo "⚠️  Watch injoignable — installation watch sautée."
    INSTALL_FAILED=1
    WATCH_STATUS="sautée (non détectée)"
  else
    echo "⌚ Installation sur la watch ($WATCH_SERIAL)…"
    if adb -s "$WATCH_SERIAL" install -r "$ARTIFACTS_DIR/$(basename "$WEAR_APK_PATH")"; then
      echo "✅ Watch installée."
      WATCH_STATUS="installée ($WATCH_SERIAL)"
    else
      echo "⚠️  Échec de l'installation watch."
      INSTALL_FAILED=1
      WATCH_STATUS="échec ($WATCH_SERIAL)"
    fi
  fi
fi

# --- Résumé ---
echo
echo "================================================================"
echo " Résumé du déploiement"
echo "   Variant       : $BUILD_VARIANT"
[ "$TARGET_PHONE" = "1" ] && echo "   Phone         : $PHONE_STATUS"
[ "$TARGET_WATCH" = "1" ] && echo "   Watch         : $WATCH_STATUS"
echo "================================================================"

exit "$INSTALL_FAILED"
