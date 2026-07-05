#!/usr/bin/env bash
# Helper d'assertions minimal pour les tests bash du projet (pas de framework externe).

TESTS_RUN=0
TESTS_FAILED=0

assert_equal() {
  local actual="$1" expected="$2" label="${3:-assert_equal}"
  TESTS_RUN=$((TESTS_RUN + 1))
  if [ "$actual" != "$expected" ]; then
    TESTS_FAILED=$((TESTS_FAILED + 1))
    echo "❌ FAIL: $label"
    echo "   attendu : $expected"
    echo "   obtenu  : $actual"
  else
    echo "✅ PASS: $label"
  fi
}

assert_status() {
  local actual_status="$1" expected_status="$2" label="${3:-assert_status}"
  TESTS_RUN=$((TESTS_RUN + 1))
  if [ "$actual_status" != "$expected_status" ]; then
    TESTS_FAILED=$((TESTS_FAILED + 1))
    echo "❌ FAIL: $label"
    echo "   code attendu : $expected_status"
    echo "   code obtenu  : $actual_status"
  else
    echo "✅ PASS: $label"
  fi
}

test_summary() {
  echo
  echo "── $TESTS_RUN test(s), $TESTS_FAILED échec(s) ──"
  [ "$TESTS_FAILED" -eq 0 ]
}
