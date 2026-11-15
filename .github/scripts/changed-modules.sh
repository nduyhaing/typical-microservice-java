#!/usr/bin/env bash
# Prints (as a compact JSON array) the Maven modules of the root pom that a commit range touches.
#   usage: changed-modules.sh <base-sha> <head-sha>
# Every module is selected when the base is unknown (new branch, first push, manual run) or when a shared input
# changed: the root pom, checkstyle rules, common-library (all services depend on it) or the backend CI files.
# The module list is read from the root pom, so new modules are picked up without editing the workflow. A module is
# also selected when one of its external inputs changed (extra_inputs below). Tests: changed-modules.test.sh.
set -euo pipefail

base="${1:-}"
head="${2:-HEAD}"
modules=$(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml)

# module names are plain directory names, so no JSON escaping is needed
to_json() {
  local out="" line
  while read -r line; do
    if [ -n "$line" ]; then out="${out}${out:+,}\"${line}\""; fi
  done
  echo "[${out}]"
}

if [ -z "$base" ] || [ "$base" = "0000000000000000000000000000000000000000" ] \
  || ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  printf '%s\n' $modules | to_json
  exit 0
fi

changed=$(git diff --name-only "$base" "$head")
shared='^(pom\.xml|checkstyle/|common-library/|\.github/workflows/(backend-ci|maven-module)\.yml|\.github/scripts/changed-modules\.sh)'
if printf '%s\n' "$changed" | grep -qE "$shared"; then
  printf '%s\n' $modules | to_json
  exit 0
fi

# Inputs a module reads from outside its own directory (its pom's <resources>/<testResources> with ../),
# as "<path regex>|<module>" (decisions.md 6-69). Keep in sync when a pom gains another ../ resource.
extra_inputs=(
  '^product/src/main/resources/db/|sampledata'   # product's changelogs + seed (sampledata/pom.xml)
  '^docker/postgres/sampledata|sampledata'       # the sampledata login's grants (sampledata ITs)
)

for m in $modules; do
  selected=false
  if printf '%s\n' "$changed" | grep -q "^${m}/"; then selected=true; fi
  for entry in "${extra_inputs[@]}"; do
    if [ "${entry##*|}" = "$m" ] && printf '%s\n' "$changed" | grep -qE "${entry%|*}"; then selected=true; fi
  done
  if "$selected"; then echo "$m"; fi
done | to_json
