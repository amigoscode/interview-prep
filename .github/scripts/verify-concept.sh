#!/usr/bin/env bash
# Proves one concept project is in the state its README promises:
#   start/     the checker FAILS, with exactly <startPass> of <total> checks passing
#   solution/  the checker passes all <total> checks, and ./mvnw verify is green
# Usage: verify-concept.sh <project path> <startPass> <total>
set -uo pipefail

project="$1"; start_pass="$2"; total="$3"
failed=0

# Logs go to a private temp folder, so two runs from one checkout do not mix their output.
logs="$(mktemp -d)"
trap 'rm -rf "$logs"' EXIT

count() { grep -c -F "$1" "$2" || true; }

echo "== $project/start: expecting $start_pass of $total, and a failing exit code"
(cd "$project/start" && ./check) > "$logs/start.log" 2>&1
start_exit=$?
pass=$(count "[PASS]" "$logs/start.log"); fail=$(count "[FAIL]" "$logs/start.log")
echo "   got $pass of $((pass + fail)), exit code $start_exit"
if [ "$pass" != "$start_pass" ] || [ "$((pass + fail))" != "$total" ] || [ "$start_exit" = "0" ]; then
  echo "   WRONG. Output of ./check in start/:"; cat "$logs/start.log"; failed=1
fi

echo "== $project/solution: expecting $total of $total, and exit code 0"
(cd "$project/solution" && ./check) > "$logs/solution.log" 2>&1
solution_exit=$?
pass=$(count "[PASS]" "$logs/solution.log"); fail=$(count "[FAIL]" "$logs/solution.log")
echo "   got $pass of $((pass + fail)), exit code $solution_exit"
if [ "$pass" != "$total" ] || [ "$fail" != "0" ] || [ "$solution_exit" != "0" ]; then
  echo "   WRONG. Output of ./check in solution/:"; cat "$logs/solution.log"; failed=1
fi

echo "== $project/solution: ./mvnw verify"
if (cd "$project/solution" && ./mvnw -B -q verify) > "$logs/verify.log" 2>&1; then
  echo "   green"
else
  echo "   FAILED:"; tail -n 80 "$logs/verify.log"; failed=1
fi

exit $failed
