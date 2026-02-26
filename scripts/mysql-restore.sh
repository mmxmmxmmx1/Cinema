#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: CONFIRM_RESTORE=yes $0 <backup.sql|backup.sql.gz>"
  exit 2
fi

BACKUP_FILE="$1"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-cinema}"
DB_USERNAME="${DB_USERNAME:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
CONFIRM_RESTORE="${CONFIRM_RESTORE:-no}"

if [[ ! -f "${BACKUP_FILE}" ]]; then
  echo "FAIL: 找不到備份檔 ${BACKUP_FILE}"
  exit 2
fi

if [[ -z "${DB_USERNAME}" || -z "${DB_PASSWORD}" ]]; then
  echo "FAIL: DB_USERNAME / DB_PASSWORD 未設定"
  exit 2
fi

if [[ "${CONFIRM_RESTORE}" != "yes" ]]; then
  echo "FAIL: 還原是高風險操作，請加上 CONFIRM_RESTORE=yes"
  exit 2
fi

command -v mysql >/dev/null 2>&1 || {
  echo "FAIL: 找不到 mysql"
  exit 2
}

if [[ "${BACKUP_FILE}" == *.gz ]]; then
  command -v gzip >/dev/null 2>&1 || {
    echo "FAIL: 還原 .gz 檔案需要 gzip"
    exit 2
  }
fi

if [[ -f "${BACKUP_FILE}.sha256" ]]; then
  command -v sha256sum >/dev/null 2>&1 || {
    echo "FAIL: 找不到 sha256sum"
    exit 2
  }
  (
    cd "$(dirname "${BACKUP_FILE}")"
    sha256sum -c "$(basename "${BACKUP_FILE}").sha256"
  )
fi

echo "WARNING: 即將把 ${BACKUP_FILE} 還原到 ${DB_NAME} (${DB_HOST}:${DB_PORT})"
export MYSQL_PWD="${DB_PASSWORD}"

if [[ "${BACKUP_FILE}" == *.gz ]]; then
  gzip -dc "${BACKUP_FILE}" | mysql \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USERNAME}" \
    "${DB_NAME}"
else
  mysql \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USERNAME}" \
    "${DB_NAME}" < "${BACKUP_FILE}"
fi

unset MYSQL_PWD

echo "PASS: 還原完成"
