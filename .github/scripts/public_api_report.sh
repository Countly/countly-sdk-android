#!/usr/bin/env bash
# Writes the report of a pull request's public API changes to the job summary, and to the step output "report", which
# the workflow's comment job posts on the pull request. The comment lists at most 200 lines per section.
#   $1 a file with one removed or changed declaration per line
#   $2 a file with one declaration per line that the pull request adds to the public API
set -euo pipefail
breaking=$1
added=$2
marker='<!-- countly-public-api -->'

# Prints a section titled $1 and explained by $2 with the lines of file $3, at most $4 of them when $4 is not 0.
section() {
  local count
  count=$(grep -c . "$3" || true)
  [ "$count" -gt 0 ] || return 0
  printf '**%s: %s.** %s\n\n```\n' "$1" "$count" "$2"
  if [ "$4" -gt 0 ]; then
    head -n "$4" "$3"
  else
    cat "$3"
  fi
  printf '```\n'
  if [ "$4" -gt 0 ] && [ "$count" -gt "$4" ]; then
    printf '\n%s more in the job summary.\n' "$((count - $4))"
  fi
  printf '\n'
}

# Prints the whole report, with at most $1 lines per section when $1 is not 0.
report() {
  printf '%s\n### Public API changes\n\n' "$marker"
  if ! grep -q . "$breaking" && ! grep -q . "$added"; then
    printf 'No public API change.\n'
    return
  fi
  section 'Removed or changed' 'Each needs a CHANGELOG entry that marks it as a breaking change.' "$breaking" "$1"
  section 'Added' 'Check that each one is meant to be public.' "$added" "$1"
}

report 0 >> "$GITHUB_STEP_SUMMARY"
delimiter="EOF_$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
{
  echo "report<<$delimiter"
  report 200
  echo "$delimiter"
} >> "$GITHUB_OUTPUT"
