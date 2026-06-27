#!/usr/bin/env bash
#
# capture-logs.sh — Capture le logcat filtré du téléphone ET de la Pixel Watch
# en parallèle, pour diagnostiquer le pont Watch <-> Phone de SecondServe.
#
# Usage :
#   ./capture-logs.sh            # auto-détecte les appareils et capture
#   ./capture-logs.sh -o DIR     # dossier de sortie (défaut: ./logs)
#
# Arrêt : Ctrl+C  (stoppe proprement les deux captures).
#
# Prérequis : adb dans le PATH, téléphone en USB, montre en débogage Wi-Fi.
# À lancer sur TON ordinateur local (pas sur le VPS).

set -euo pipefail

# --- Filtre des tags pertinents (Timber utilise le nom de classe comme tag) ---
FILTER='DataLayer|secondserve|SecondServe|ScoreViewModel|StartMatch|NewMatch|MatchViewModel|Coaching|BackgroundActivity|Background activity|Abort background'

OUT_DIR="./logs"
while getopts "o:h" opt; do
  case "$opt" in
    o) OUT_DIR="$OPTARG" ;;
    h) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) exit 1 ;;
  esac
done

# --- Vérifier adb ---
if ! command -v adb >/dev/null 2>&1; then
  echo "❌ adb introuvable dans le PATH."
  echo "   Mac    : export PATH=\$PATH:~/Library/Android/sdk/platform-tools"
  echo "   Windows: ajoute %LOCALAPPDATA%\\Android\\Sdk\\platform-tools au PATH"
  exit 1
fi

mkdir -p "$OUT_DIR"
TS="$(date +%Y%m%d-%H%M%S)"

# --- Lister les appareils connectés (serials en état 'device') ---
# (boucle portable : 'mapfile' n'existe pas dans le bash 3.2 de macOS)
SERIALS=()
while IFS= read -r line; do
  [ -n "$line" ] && SERIALS+=("$line")
done < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')

if [ "${#SERIALS[@]}" -eq 0 ]; then
  echo "❌ Aucun appareil 'device' détecté. Vérifie :"
  echo "   - téléphone branché en USB + débogage USB autorisé"
  echo "   - montre : adb pair / adb connect effectués"
  echo "   (lance 'adb devices' pour diagnostiquer)"
  exit 1
fi

# --- Classer chaque appareil : montre (ro.build.characteristics contient 'watch') ou téléphone ---
PHONE_SERIAL=""
WATCH_SERIAL=""
for s in "${SERIALS[@]}"; do
  chars="$(adb -s "$s" shell getprop ro.build.characteristics 2>/dev/null | tr -d '\r')"
  model="$(adb -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  if echo "$chars" | grep -qi watch; then
    WATCH_SERIAL="$s"
    echo "⌚ Montre détectée : $s ($model)"
  else
    PHONE_SERIAL="$s"
    echo "📱 Téléphone détecté : $s ($model)"
  fi
done

PIDS=()
cleanup() {
  trap '' INT TERM   # neutralise un éventuel double Ctrl+C pendant le nettoyage
  echo
  echo "⏹  Arrêt des captures…"
  # On tue les PID stockés (les 'tee') PUIS tous les enfants directs restants
  # (adb logcat, grep) : sinon 'adb logcat' tourne sans fin et bloque l'arrêt.
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  pkill -P $$ 2>/dev/null || true
  echo "✅ Logs enregistrés dans : $OUT_DIR/"
  [ -n "$PHONE_SERIAL" ] && echo "   - $OUT_DIR/log_phone-$TS.txt"
  [ -n "$WATCH_SERIAL" ] && echo "   - $OUT_DIR/log_watch-$TS.txt"
  echo
  echo "👉 Colle-moi ces fichiers (ou les lignes 'received path=...') pour l'analyse."
  exit 0
}
trap cleanup INT TERM

# --- Démarrer une capture filtrée pour un appareil ---
start_capture() {
  local serial="$1" label="$2" file="$3"
  echo "🧹 Buffer vidé ($label)"
  adb -s "$serial" logcat -c || true
  echo "🎥 Capture $label -> $file"
  # -v time : horodatage ; grep --line-buffered : flux temps réel ; sed : préfixe [LABEL]
  adb -s "$serial" logcat -v time 2>/dev/null \
    | grep --line-buffered -iE "$FILTER" \
    | sed -u "s/^/[$label] /" \
    | tee "$file" &
  PIDS+=("$!")
}

echo
if [ -n "$PHONE_SERIAL" ]; then
  start_capture "$PHONE_SERIAL" "PHONE" "$OUT_DIR/log_phone-$TS.txt"
fi
if [ -n "$WATCH_SERIAL" ]; then
  start_capture "$WATCH_SERIAL" "WATCH" "$OUT_DIR/log_watch-$TS.txt"
fi

if [ -z "$PHONE_SERIAL" ]; then
  echo "⚠️  Aucun téléphone détecté — seule la montre est capturée."
fi
if [ -z "$WATCH_SERIAL" ]; then
  echo "⚠️  Aucune montre détectée — seul le téléphone est capturé."
  echo "    (montre : Paramètres > Options développeur > Débogage Wi-Fi, puis adb pair/connect)"
fi

echo
echo "================================================================"
echo " Captures en cours. Reproduis maintenant les 3 tests :"
echo "   1. Téléphone : 'Nouveau match'  -> la montre s'ouvre-t-elle ?"
echo "   2. Montre    : démarrer un match -> le téléphone s'ouvre-t-il ?"
echo "   3. Montre    : marquer un jeu complet -> score/coaching sur tél. ?"
echo
echo " Puis appuie sur Ctrl+C pour arrêter et sauvegarder."
echo "================================================================"
echo

wait
