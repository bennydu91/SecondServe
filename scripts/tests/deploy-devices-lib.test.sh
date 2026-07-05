#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"

source ./test_helpers.sh
source ../lib/deploy-devices-lib.sh

# --- filter_connected_serials ---
result="$(printf 'List of devices attached\nemulator-5554\tdevice\n192.168.1.5:5555\toffline\nABCD1234\tdevice\n\n' | filter_connected_serials)"
expected="$(printf 'emulator-5554\nABCD1234')"
assert_equal "$result" "$expected" "filter_connected_serials garde uniquement l'état 'device'"

result_empty="$(printf 'List of devices attached\n\n' | filter_connected_serials)"
assert_equal "$result_empty" "" "filter_connected_serials renvoie vide sans appareil connecté"

# --- classify_by_characteristics ---
assert_equal "$(classify_by_characteristics 'nosdcard,watch')" "watch" "classify_by_characteristics détecte une montre"
assert_equal "$(classify_by_characteristics 'nosdcard')" "phone" "classify_by_characteristics détecte un téléphone par défaut"
assert_equal "$(classify_by_characteristics $'nosdcard,watch\r')" "watch" "classify_by_characteristics tolère un retour chariot final"

# --- parse_deploy_args ---
parse_deploy_args
assert_equal "$TARGET_PHONE" "1" "parse_deploy_args sans argument cible le phone"
assert_equal "$TARGET_WATCH" "1" "parse_deploy_args sans argument cible la watch"
assert_equal "$BUILD_VARIANT" "staging" "parse_deploy_args sans argument utilise staging"

parse_deploy_args --phone-only
assert_equal "$TARGET_PHONE" "1" "parse_deploy_args --phone-only garde le phone"
assert_equal "$TARGET_WATCH" "0" "parse_deploy_args --phone-only exclut la watch"

parse_deploy_args --watch-only
assert_equal "$TARGET_PHONE" "0" "parse_deploy_args --watch-only exclut le phone"
assert_equal "$TARGET_WATCH" "1" "parse_deploy_args --watch-only garde la watch"

parse_deploy_args --release
assert_equal "$BUILD_VARIANT" "release" "parse_deploy_args --release bascule le variant"

parse_deploy_args --inconnu >/dev/null 2>&1
assert_status "$?" "1" "parse_deploy_args rejette une option inconnue"

# --- validate_config ---
(
  VPS_HOST="1.2.3.4" VPS_USER="root" VPS_SSH_PORT="22" VPS_REPO_PATH="/root/SecondServe"
  validate_config
)
assert_status "$?" "0" "validate_config accepte une config complète"

(
  VPS_HOST="" VPS_USER="root" VPS_SSH_PORT="22" VPS_REPO_PATH="/root/SecondServe"
  validate_config >/dev/null 2>&1
)
assert_status "$?" "1" "validate_config rejette une config incomplète"

# --- save_ip_to_config ---
TMP_CONF="$(mktemp)"
printf 'VPS_HOST=1.2.3.4\nWATCH_IP=10.0.0.1\n' > "$TMP_CONF"
save_ip_to_config "$TMP_CONF" "WATCH_IP" "10.0.0.99"
result="$(grep '^WATCH_IP=' "$TMP_CONF")"
assert_equal "$result" "WATCH_IP=10.0.0.99" "save_ip_to_config met à jour une ligne WATCH_IP existante"

TMP_CONF2="$(mktemp)"
printf 'VPS_HOST=1.2.3.4\n' > "$TMP_CONF2"
save_ip_to_config "$TMP_CONF2" "WATCH_IP" "10.0.0.42"
result2="$(grep '^WATCH_IP=' "$TMP_CONF2")"
assert_equal "$result2" "WATCH_IP=10.0.0.42" "save_ip_to_config ajoute la ligne WATCH_IP si absente"

TMP_CONF3="$(mktemp)"
printf 'VPS_HOST=1.2.3.4\nPHONE_IP=10.0.0.5\nWATCH_IP=10.0.0.1\n' > "$TMP_CONF3"
save_ip_to_config "$TMP_CONF3" "PHONE_IP" "10.0.0.77"
result3="$(grep '^PHONE_IP=' "$TMP_CONF3")"
assert_equal "$result3" "PHONE_IP=10.0.0.77" "save_ip_to_config met à jour PHONE_IP sans toucher WATCH_IP"
result3b="$(grep '^WATCH_IP=' "$TMP_CONF3")"
assert_equal "$result3b" "WATCH_IP=10.0.0.1" "save_ip_to_config ne modifie pas les autres variables du fichier"

rm -f "$TMP_CONF" "$TMP_CONF2" "$TMP_CONF3"

# --- format_adb_address ---
assert_equal "$(format_adb_address "192.168.1.5")" "192.168.1.5:5555" "format_adb_address ajoute le port par défaut 5555 si absent"
assert_equal "$(format_adb_address "192.168.1.5:42107")" "192.168.1.5:42107" "format_adb_address garde le port déjà présent (débogage sans fil, port aléatoire)"

test_summary
exit $?
