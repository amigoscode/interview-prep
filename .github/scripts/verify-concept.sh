#!/usr/bin/env bash
# Proves one concept project is in the state its README promises:
#   start/     the checker FAILS, with exactly <startPass> of <total> checks passing
#   solution/  the checker passes all <total> checks, and ./mvnw verify is green
# Usage: verify-concept.sh <project path> <startPass> <total>
set -uo pipefail

project="$1"; start_pass="$2"; total="$3"
failed=0

count() { grep -c -F "$1" "$2" || true; }

echo "== $project/start: expecting $start_pass of $total, and a failing exit code"
(cd "$project/start" && ./check) > start.log 2>&1
start_exit=$?
pass=$(count "[PASS]" start.log); fail=$(count "[FAIL]" start.log)
echo "   got $pass of $((pass + fail)), exit code $start_exit"
if [ "$pass" != "$start_pass" ] || [ "$((pass + fail))" != "$total" ] || [ "$start_exit" = "0" ]; then
  echo "   WRONG. Output of ./check in start/:"; cat start.log; failed=1
fi

echo "== $project/solution: expecting $total of $total, and exit code 0"
(cd "$project/solution" && ./check) > solution.log 2>&1
solution_exit=$?
pass=$(count "[PASS]" solution.log); fail=$(count "[FAIL]" solution.log)
echo "   got $pass of $((pass + fail)), exit code $solution_exit"
if [ "$pass" != "$total" ] || [ "$fail" != "0" ] || [ "$solution_exit" != "0" ]; then
  echo "   WRONG. Output of ./check in solution/:"; cat solution.log; failed=1
fi

echo "== $project/solution: ./mvnw verify"
if (cd "$project/solution" && ./mvnw -B -q verify) > verify.log 2>&1; then
  echo "   green"
else
  echo "   FAILED:"; tail -n 80 verify.log; failed=1
fi

rm -f start.log solution.log verify.log
exit $failed
