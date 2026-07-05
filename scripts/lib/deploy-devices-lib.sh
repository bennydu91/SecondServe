#!/usr/bin/env bash
# deploy-devices-lib.sh — fonctions pures réutilisées par scripts/deploy-devices.sh.
# Compatible bash 3.2 (pas de mapfile, pas de tableaux associatifs).

# Filtre la sortie de `adb devices` : ne garde que les serials à l'état "device".
filter_connected_serials() {
  awk 'NR>1 && $2=="device" {print $1}'
}

# Classe un appareil phone/watch à partir de la valeur de `ro.build.characteristics`.
classify_by_characteristics() {
  local chars="$1"
  if printf '%s' "$chars" | tr -d '\r' | grep -qi watch; then
    echo "watch"
  else
    echo "phone"
  fi
}

# Parse les arguments CLI du script principal.
# Définit : TARGET_PHONE, TARGET_WATCH, BUILD_VARIANT (globales, écrasées à chaque appel).
parse_deploy_args() {
  TARGET_PHONE=1
  TARGET_WATCH=1
  BUILD_VARIANT="staging"
  for arg in "$@"; do
    case "$arg" in
      --phone-only) TARGET_PHONE=1; TARGET_WATCH=0 ;;
      --watch-only) TARGET_PHONE=0; TARGET_WATCH=1 ;;
      --release) BUILD_VARIANT="release" ;;
      *)
        echo "Option inconnue : $arg" >&2
        return 1
        ;;
    esac
  done
  return 0
}
