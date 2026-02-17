#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-}"
if [[ -z "${BASE_REF}" ]]; then
  echo "Usage: $0 <base-ref-or-commit>"
  exit 2
fi

if git rev-parse --verify -q "${BASE_REF}^{commit}" >/dev/null; then
  BASE_COMMIT="${BASE_REF}"
else
  git fetch --no-tags --depth=1 origin "${BASE_REF}"
  BASE_COMMIT="origin/${BASE_REF}"
fi

changed="$(git diff --name-status "${BASE_COMMIT}"...HEAD -- 'src/main/resources/db/migration/V*.sql' || true)"
if [[ -z "${changed}" ]]; then
  echo "No versioned migration changes detected."
  exit 0
fi

echo "Detected migration changes:"
echo "${changed}"

violations="$(echo "${changed}" | awk '$1 != "A" { print }')"
if [[ -n "${violations}" ]]; then
  echo
  echo "ERROR: Existing Flyway versioned migrations were modified/deleted."
  echo "Only add new files like V12__*.sql; do not edit older V*.sql after they are applied."
  echo "${violations}"
  exit 1
fi

added_files="$(echo "${changed}" | awk '$1 == "A" { print $2 }')"
if [[ -n "${added_files}" ]]; then
  asset_violations=""
  while IFS= read -r file; do
    [[ -z "${file}" ]] && continue
    if grep -Eiq 'poster_url|carousel_image_url' "${file}"; then
      asset_violations+="${file}"$'\n'
    fi
  done <<< "${added_files}"

  if [[ -n "${asset_violations}" ]]; then
    echo
    echo "ERROR: Detected image/content updates in versioned migrations."
    echo "Use repeatable migration file for movie assets:"
    echo "  src/main/resources/db/migration/R__movie_catalog_assets.sql"
    echo "Please move poster_url/carousel_image_url updates out of V*.sql files."
    echo "${asset_violations}"
    exit 1
  fi
fi

echo "Migration immutability check passed (only new V*.sql files were added)."
