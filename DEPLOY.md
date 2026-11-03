# 배포 운영 문서

AWS Lightsail 인스턴스 한 대에 Nginx(HTTPS), Spring Boot(jar, systemd), PostgreSQL을 모두 올린다. Docker 이미지, 레지스트리, RDS는 쓰지 않는다.
담당은 백엔드 A, 백업은 백엔드 C. 백업 담당은 "1. 평소에 하는 일"을 한 번 같이 해 본다.

```
main 머지 → GitHub Actions: 빌드·테스트 → jar를 scp로 서버에 복사 → deploy.sh: 교체·재시작·헬스체크 (실패하면 이전 jar로 복구)
```

## 1. 평소에 하는 일

| 하고 싶은 것 | 방법 |
| --- | --- |
| 배포 | `main`에 머지하면 자동 |
| 같은 버전 다시 배포 | Actions → 마지막 main 실행 → Re-run jobs |
| 배포 잠시 막기 (시연 전 동결) | Settings → Secrets and variables → Actions → Variables에서 `DEPLOY_ENABLED`를 `false`로 |
| 서버 접속 | Lightsail 콘솔의 브라우저 SSH, 또는 `ssh -i {키} ubuntu@{도메인}` |
| 서버 로그 보기 | `sudo journalctl -u batong -f` |
| 재시작 | `sudo systemctl restart batong` |
| 상태 확인 | `sudo systemctl status batong`, `curl localhost:8080/actuator/health` |
| 직전 버전으로 되돌리기 | `cp /opt/batong/app-prev.jar /opt/batong/app.jar && sudo systemctl restart batong` |
| 환경변수 바꾸기 | `/opt/batong/.env` 수정 후 재시작 |
| DB 접속 | `psql -h localhost -U batong batong` |
| DB 백업 파일 만들기 | `pg_dump -h localhost -U batong -Fc batong > ~/batong-$(date +%m%d-%H%M).dump` |
| 메모리 확인 | `free -h` |

서버 로그는 Actions 로그에 출력하지 않는다. 공개 레포라 Actions 로그는 누구나 볼 수 있다.

## 2. 처음 한 번 세팅

### 2-1. AWS 계정 확인
- 콘솔 오른쪽 위 계정 메뉴나 Billing → **Free Tier / Credits**에서 남은 기간과 크레딧을 본다.
- 2025-07-15 이후 만든 계정은 Free Plan이다. 가입 후 6개월이 지나거나 크레딧을 다 쓰면 끝나고, 90일 안에 유료 플랜으로 바꾸지 않으면 계정과 리소스가 삭제된다.
- Billing → Budgets에서 **월 10달러 예산 알림**을 만든다.

### 2-2. 인스턴스 만들기 (Lightsail 콘솔)
1. Create instance
   - 위치: **Seoul (ap-northeast-2)**
   - 플랫폼: Linux/Unix, 블루프린트: **OS Only → Ubuntu 24.04 LTS**
   - 플랜: **$12 (2GB RAM, 2 vCPU, 60GB SSD)**. 플랜 선택 화면에 무료 기간 표시가 있으면 적용된다
   - 이름: `batong-server`
2. Networking 탭
   - **Static IP 만들기 → 이 인스턴스에 붙이기**. 붙어 있는 동안은 추가 요금이 없다. 떼어 둔 채로 두면 요금이 나온다
   - IPv4 방화벽: SSH(22), HTTP(80)은 기본으로 있다. **HTTPS(443) 추가**. 그 외 포트는 열지 않는다(5432 포함)
3. Snapshots 탭: **Automatic snapshots 켜기**. 매일 한 번, 최근 7개가 보관된다

### 2-3. 기본 설정 (브라우저 SSH에서)

```bash
# 메모리 여유 (2GB 서버라 swap 2GB 추가)
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 시간대, Java, PostgreSQL, Nginx, certbot
sudo timedatectl set-timezone Asia/Seoul
sudo apt update && sudo apt install -y openjdk-17-jre-headless postgresql nginx certbot python3-certbot-nginx

# 앱 폴더
sudo mkdir -p /opt/batong/incoming && sudo chown -R ubuntu:ubuntu /opt/batong
```

Ubuntu 24.04의 기본 `postgresql` 패키지가 PostgreSQL 16이다.

### 2-4. DB 만들기

```bash
DB_PASS=$(openssl rand -base64 24 | tr -d '/+=')
echo "DB 비밀번호: $DB_PASS"      # 아래 .env에 넣을 값. 화면에서 복사한 뒤 다른 곳에 남기지 않는다
sudo -u postgres psql -c "CREATE USER batong WITH PASSWORD '$DB_PASS';"
sudo -u postgres psql -c "CREATE DATABASE batong OWNER batong;"
```

- PostgreSQL은 기본 설정에서 이 서버 안(localhost)의 접속만 받는다. 설정 파일의 `listen_addresses`를 바꾸지 않는다
- 방화벽에서 5432를 열지 않는다. 로컬 PC에서 서버 DB를 보고 싶으면 SSH 터널을 쓴다: `ssh -L 15432:localhost:5432 ubuntu@{도메인}` 후 `localhost:15432`로 접속

### 2-5. 앱 설정

```bash
# 레포의 deploy/ 파일을 서버로 (로컬에서)
scp -i {Lightsail 기본 키} deploy/deploy.sh deploy/batong.service deploy/nginx-batong.conf ubuntu@{고정 IP}:/tmp/

# 서버에서
mv /tmp/deploy.sh /opt/batong/deploy.sh && chmod +x /opt/batong/deploy.sh
sudo mv /tmp/batong.service /etc/systemd/system/batong.service
sudo systemctl daemon-reload && sudo systemctl enable batong

nano /opt/batong/.env      # .env.example 기준으로 서버 값 채우기
chmod 600 /opt/batong/.env
```

Lightsail 기본 키는 Lightsail 콘솔 → Account → SSH keys에서 내려받는다.

서버 `.env`에 꼭 넣을 것

| 키 | 값 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` |
| `DB_URL` | `jdbc:postgresql://localhost:5432/batong` |
| `DB_USERNAME` / `DB_PASSWORD` | `batong` / 2-4에서 만든 비밀번호 |
| `JWT_SECRET` | `openssl rand -base64 48` 결과 |
| `ADMIN_API_KEY` | `openssl rand -base64 32` 결과 |
| `CORS_ALLOWED_ORIGINS` | 프론트 Vercel 도메인 |
| `SHARE_BASE_URL` | 프론트 Vercel 도메인 |
| `AI_MODE` 외 LLM·STT·공공데이터 키 | 정해지는 대로 |

### 2-6. 도메인과 HTTPS
1. 도메인을 정한다. 없으면 DuckDNS 같은 무료 DNS로 `batong.duckdns.org`를 만든다
2. 도메인의 A 레코드를 고정 IP로
3. Nginx 설정

```bash
sudo mv /tmp/nginx-batong.conf /etc/nginx/conf.d/batong.conf
sudo nano /etc/nginx/conf.d/batong.conf   # server_name을 실제 도메인으로
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d {도메인}            # HTTPS 자동 설정, 자동 갱신 포함
```

### 2-7. GitHub 연결
1. 배포 전용 SSH 키를 만든다(로컬에서): `ssh-keygen -t ed25519 -f batong-deploy -N ""`
2. `batong-deploy.pub` 내용을 서버의 `~/.ssh/authorized_keys`에 한 줄 추가
3. 레포 Settings → Secrets and variables → Actions
   - Secrets: `SERVER_HOST`(도메인 또는 고정 IP), `SERVER_USER`(`ubuntu`), `SERVER_SSH_KEY`(`batong-deploy` 개인키 전체)
   - Variables: `DEPLOY_ENABLED` = `true`
4. 로컬의 `batong-deploy` 개인키는 Secrets에 넣은 뒤 지운다. 커밋하지 않는다

`DEPLOY_ENABLED`가 `true`가 아니면 CI는 빌드·테스트만 하고 배포는 건너뛴다. 서버를 만들기 전에도 CI가 실패하지 않게 하기 위해서다.

### 2-8. 첫 배포 확인
- main에 아무 PR이나 머지 → Actions의 deploy 단계가 초록인지
- `https://{도메인}/actuator/health` → `{"status":"UP"}`
- `https://{도메인}/swagger-ui/index.html`

## 3. 문제가 생겼을 때

| 증상 | 확인할 것 |
| --- | --- |
| deploy 단계에서 SSH 실패 | Secrets 값, 방화벽 22번, 공개키가 `authorized_keys`에 있는지 |
| 헬스체크 실패로 되돌려짐 | 서버에서 `sudo journalctl -u batong -n 200`. 대부분 `.env` 값 누락이나 Flyway 오류 |
| `Connection refused` (DB) | `sudo systemctl status postgresql`, `.env`의 `DB_URL`·비밀번호 |
| Flyway 오류로 안 뜸 | 로그의 마이그레이션 파일 이름 확인 → 그 PR 작성자와 같이 본다. 시연 전이면 DB를 지우고 다시 만들어도 된다: 앱 정지 → `sudo -u postgres psql -c "DROP DATABASE batong;"` → `sudo -u postgres psql -c "CREATE DATABASE batong OWNER batong;"` → 앱 시작 |
| 502 Bad Gateway | 앱이 죽었거나 기동 중. `systemctl status batong` |
| 프론트에서 CORS 에러 | `.env`의 `CORS_ALLOWED_ORIGINS`에 프론트 도메인이 있는지 |
| 서버가 느리거나 멈춤 | `free -h`로 메모리 확인, swap이 켜져 있는지. 계속 부족하면 스냅샷으로 $24(4GB) 플랜 인스턴스를 만들어 옮기고 고정 IP를 옮겨 붙인다 |
| 인증서 만료 | `sudo certbot renew --dry-run`. 보통 자동 갱신된다 |
| 서버가 완전히 망가짐 | 최근 자동 스냅샷으로 새 인스턴스 생성 → 고정 IP 옮겨 붙이기. 하루치 데이터만 잃는다 |

## 4. 시연 전 동결 (D13)
1. 마지막 머지 후 `DEPLOY_ENABLED`를 `false`로
2. 시드 데이터 적용 확인, 시연 시나리오 한 바퀴
3. **수동 스냅샷**(Lightsail → Snapshots → Create snapshot)과 `pg_dump` 한 번
4. `AI_MODE`를 바꿔야 하면 `.env` 수정 후 재시작 (mock 전환은 1분)
5. 시연이 끝날 때까지 서버 설정을 바꾸지 않는다

## 5. 대회 후 정리
요금이 계속 나가지 않게 인스턴스, 스냅샷, 고정 IP를 삭제한다. 포트폴리오용으로 남길 거면 인스턴스만 유지하고 예산 알림을 그대로 둔다.
