#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <backup.sql>"
  exit 2
fi

BACKUP_FILE="$1"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-cinema}"
DB_USERNAME="${DB_USERNAME:-}"
DB_PASSWORD="${DB_PASSWORD:-}"

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "FAIL: 找不到備份檔 ${BACKUP_FILE}"
  exit 2
fi

if [[ -z "${DB_USERNAME}" || -z "${DB_PASSWORD}" ]]; then
  echo "FAIL: DB_USERNAME / DB_PASSWORD 未設定"
  exit 2
fi

echo "WARNING: 即將把 ${BACKUP_FILE} 還原到 ${DB_NAME} (${DB_HOST}:${DB_PORT})"

mysql \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --password="${DB_PASSWORD}" \
  "${DB_NAME}" < "${BACKUP_FILE}"

echo "PASS: 還原完成"
