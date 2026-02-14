#!/usr/bin/env bash
set -euo pipefail

# Reset demo transactional data while keeping account/role master data.
# Usage:
#   DB_URL='jdbc:mysql://localhost:3306/cinema?...' DB_USERNAME= DB_PASSWORD= ./scripts/reset-dev-demo-data.sh

db_url="${DB_URL:-}"
db_user="${DB_USERNAME:-}"
db_pass="${DB_PASSWORD:-}"

if [[ -z "${db_url}" || -z "${db_user}" ]]; then
  echo "請先設定 DB_URL 與 DB_USERNAME（以及需要時 DB_PASSWORD）"
  exit 2
fi

db_name="$(echo "${db_url}" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*$#\1#')"
if [[ -z "${db_name}" || "${db_name}" == "${db_url}" ]]; then
  echo "無法從 DB_URL 解析資料庫名稱：${db_url}"
  exit 2
fi

mysql_cmd=(mysql -u"${db_user}" "${db_name}")
if [[ -n "${db_pass}" ]]; then
  mysql_cmd+=( -p"${db_pass}" )
fi

echo "重置資料庫 ${db_name} 的 demo 交易資料..."
"${mysql_cmd[@]}" <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE payment_idempotency;
TRUNCATE TABLE payment_transactions;
TRUNCATE TABLE member_order_items;
TRUNCATE TABLE member_tickets;
TRUNCATE TABLE member_orders;
TRUNCATE TABLE notifications;
TRUNCATE TABLE member_point_ledger;
TRUNCATE TABLE member_point_balance;
TRUNCATE TABLE member_point_redemptions;
TRUNCATE TABLE maintenance_requests;
DELETE FROM employee_todos WHERE todo_date >= CURRENT_DATE - INTERVAL 30 DAY;
SET FOREIGN_KEY_CHECKS = 1;
SQL

echo "完成：交易資料已清空（members/employee/roles/movie 設定保留）。"
