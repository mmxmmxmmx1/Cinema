#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-cinema}"
DB_USERNAME="${DB_USERNAME:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
TS="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="${BACKUP_DIR}/cinema_${TS}.sql"

if [[ -z "${DB_USERNAME}" || -z "${DB_PASSWORD}" ]]; then
  echo "FAIL: DB_USERNAME / DB_PASSWORD 未設定"
  exit 2
fi

mkdir -p "${BACKUP_DIR}"

mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --password="${DB_PASSWORD}" \
  --single-transaction \
  --set-gtid-purged=OFF \
  "${DB_NAME}" > "${OUT_FILE}"

echo "PASS: 備份完成 -> ${OUT_FILE}"
