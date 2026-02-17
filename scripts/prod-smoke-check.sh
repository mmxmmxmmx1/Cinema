#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${APP_BASE_URL:-http://localhost:8080}"

echo "[1/3] 檢查健康端點: ${BASE_URL}/api/health"
health="$(curl -fsS "${BASE_URL}/api/health")"
echo "${health}" | grep -q '"status":"UP"' || {
  echo "FAIL: health.status 不是 UP"
  exit 1
}
echo "${health}" | grep -q '"db":"UP"' || {
  echo "FAIL: health.db 不是 UP"
  exit 1
}

echo "[2/3] 檢查電影清單: ${BASE_URL}/api/movies"
movies="$(curl -fsS "${BASE_URL}/api/movies")"
movie_count="$(echo "${movies}" | grep -Eo '"id":"mv-[0-9]+"' | wc -l | tr -d ' ')"
if [[ "${movie_count}" -lt 10 ]]; then
  echo "FAIL: 電影數量小於 10 (實際: ${movie_count})"
  exit 1
fi

echo "[3/3] 檢查輪播圖片欄位"
echo "${movies}" | grep -q '"carouselImageUrl"' || {
  echo "FAIL: 電影資料缺少 carouselImageUrl"
  exit 1
}

echo "PASS: production smoke check 通過"
