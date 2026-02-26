#!/usr/bin/env bash
set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-cinema}"
DB_USERNAME="${DB_USERNAME:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
BACKUP_COMPRESS="${BACKUP_COMPRESS:-1}"
TS="$(date +%Y%m%d_%H%M%S)"
BASE_NAME="cinema_${DB_NAME}_${TS}"
OUT_FILE_SQL="${BACKUP_DIR}/${BASE_NAME}.sql"
OUT_FILE="${OUT_FILE_SQL}"
CHECKSUM_FILE=""

if [[ -z "${DB_USERNAME}" || -z "${DB_PASSWORD}" ]]; then
  echo "FAIL: DB_USERNAME / DB_PASSWORD 未設定"
  exit 2
fi

command -v mysqldump >/dev/null 2>&1 || {
  echo "FAIL: 找不到 mysqldump"
  exit 2
}
command -v sha256sum >/dev/null 2>&1 || {
  echo "FAIL: 找不到 sha256sum"
  exit 2
}
if [[ "${BACKUP_COMPRESS}" == "1" ]]; then
  command -v gzip >/dev/null 2>&1 || {
    echo "FAIL: 啟用壓縮時需要 gzip"
    exit 2
  }
fi

mkdir -p "${BACKUP_DIR}"
export MYSQL_PWD="${DB_PASSWORD}"

dump_cmd=(
  mysqldump
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME}" \
  --single-transaction \
  --quick \
  --no-tablespaces \
  --routines \
  --events \
  --triggers \
  --set-gtid-purged=OFF \
  "${DB_NAME}"
)

if [[ "${BACKUP_COMPRESS}" == "1" ]]; then
  OUT_FILE="${OUT_FILE_SQL}.gz"
  "${dump_cmd[@]}" | gzip -c > "${OUT_FILE}"
else
  "${dump_cmd[@]}" > "${OUT_FILE}"
fi

CHECKSUM_FILE="${OUT_FILE}.sha256"
sha256sum "${OUT_FILE}" > "${CHECKSUM_FILE}"
unset MYSQL_PWD

if [[ "${BACKUP_RETENTION_DAYS}" =~ ^[0-9]+$ ]] && [[ "${BACKUP_RETENTION_DAYS}" -gt 0 ]]; then
  find "${BACKUP_DIR}" -type f \( -name "cinema_*.sql" -o -name "cinema_*.sql.gz" -o -name "cinema_*.sql.sha256" -o -name "cinema_*.sql.gz.sha256" \) \
    -mtime +"${BACKUP_RETENTION_DAYS}" \
    -print \
    -delete
fi

echo "PASS: 備份完成 -> ${OUT_FILE}"
echo "PASS: 校驗檔完成 -> ${CHECKSUM_FILE}"
