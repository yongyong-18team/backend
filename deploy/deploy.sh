#!/usr/bin/env bash
# 서버의 /opt/batong/deploy.sh 로 복사해 두고 GitHub Actions가 SSH로 실행한다.
# 새 jar로 바꾸고 재시작 → 헬스체크 실패하면 이전 jar로 되돌린다.
set -euo pipefail

APP_DIR=/opt/batong
NEW_JAR="$APP_DIR/incoming/batong-backend-0.0.1-SNAPSHOT.jar"
HEALTH_URL=http://localhost:8080/actuator/health

if [[ ! -f "$NEW_JAR" ]]; then
  echo "새 jar가 없습니다: $NEW_JAR"
  exit 1
fi

if [[ -f "$APP_DIR/app.jar" ]]; then
  cp "$APP_DIR/app.jar" "$APP_DIR/app-prev.jar"
fi
mv "$NEW_JAR" "$APP_DIR/app.jar"
sudo systemctl restart batong

for _ in $(seq 1 30); do
  if curl -fs "$HEALTH_URL" | grep -q '"status":"UP"'; then
    echo "배포 성공"
    exit 0
  fi
  sleep 3
done

echo "헬스체크 실패. 이전 버전으로 되돌립니다. 원인은 서버에서 확인: sudo journalctl -u batong -n 200"
if [[ -f "$APP_DIR/app-prev.jar" ]]; then
  cp "$APP_DIR/app-prev.jar" "$APP_DIR/app.jar"
  sudo systemctl restart batong
fi
# 공개 레포의 Actions 로그는 누구나 볼 수 있으므로 서버 로그를 여기서 출력하지 않는다
exit 1
