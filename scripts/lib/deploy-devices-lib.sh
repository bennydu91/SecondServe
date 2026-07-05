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

# Vérifie que les variables requises de deploy-devices.conf sont bien définies.
validate_config() {
  local missing=() var
  for var in VPS_HOST VPS_USER VPS_SSH_PORT VPS_REPO_PATH; do
    if [ -z "${!var:-}" ]; then
      missing+=("$var")
    fi
  done
  if [ "${#missing[@]}" -gt 0 ]; then
    echo "Config incomplète — variable(s) manquante(s) : ${missing[*]}" >&2
    echo "Voir scripts/deploy-devices.conf.example" >&2
    return 1
  fi
  return 0
}

# Met à jour (ou ajoute) la ligne ${var_name}= dans le fichier de config donné.
# Portable macOS/Linux : passe par un fichier temporaire plutôt que 'sed -i'
# (l'option -i de sed diffère entre BSD/macOS et GNU/Linux).
save_ip_to_config() {
  local conf_file="$1" var_name="$2" ip="$3" tmp
  tmp="$(mktemp)"
  if [ -f "$conf_file" ] && grep -q "^${var_name}=" "$conf_file"; then
    awk -v var_name="$var_name" -v ip="$ip" '{ if ($0 ~ ("^" var_name "=")) print var_name "=" ip; else print }' "$conf_file" > "$tmp"
  else
    { [ -f "$conf_file" ] && cat "$conf_file"; printf '%s=%s\n' "$var_name" "$ip"; } > "$tmp"
  fi
  mv "$tmp" "$conf_file"
}

# Complète une adresse ADB WiFi avec le port par défaut (5555) si elle n'en
# contient pas déjà un. Le "débogage sans fil" (Android 11+, seul mode
# disponible sur la Pixel Watch, sans port USB data) assigne un port
# aléatoire affiché à l'écran — contrairement à `adb tcpip 5555` qui fixe
# toujours le port à 5555.
format_adb_address() {
  local value="$1"
  case "$value" in
    *:*) echo "$value" ;;
    *) echo "$value:5555" ;;
  esac
}
