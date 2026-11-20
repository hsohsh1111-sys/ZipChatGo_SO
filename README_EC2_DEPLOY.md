# EC2 배포 및 운영

## 현재 구성

- Spring Boot: Docker Compose 서비스 `spring`, 내부 포트 8080
- FastAPI: Docker Compose 서비스 `ai-server`, 내부 포트 8000 (외부 미공개)
- DB: 기존 TiDB Cloud 환경변수 연결
- 공개 진입점: Nginx → Spring Boot
- CI/CD: `main`에 push되면 빌드 후 SSH로 EC2에서 최신 커밋을 배포

Render 설정과 기존 애플리케이션 코드는 유지합니다. EC2에 별도의 clone을 `/opt/zipchatgo`에 준비하고 그 폴더의 `.env`에 운영 환경변수를 저장합니다. `.env`, SSH 키, DuckDNS 토큰은 저장소에 두지 않습니다.

## GitHub Actions 설정

저장소 Settings → Secrets and variables → Actions에 다음 Repository Secrets를 추가합니다.

| Secret | 값 |
| --- | --- |
| `EC2_HOST` | EC2의 고정 퍼블릭 IP 또는 SSH 접속 호스트 |
| `EC2_USER` | 인스턴스 OS의 SSH 사용자 (Ubuntu 이미지라면 보통 `ubuntu`) |
| `EC2_SSH_PRIVATE_KEY` | EC2 접속용 PEM 개인 키 전체 내용 |
| `EC2_SSH_PORT` | SSH 포트, 기본값 `22` |
| `EC2_APP_DIR` | 서버 저장소 경로, 기본값 `/opt/zipchatgo` |

EC2에서 저장소를 해당 경로에 clone하고 `main` 브랜치를 체크아웃합니다. 저장소가 private이면 서버에서 `git fetch`할 수 있는 읽기 전용 deploy key 또는 GitHub App 인증을 별도로 설정합니다. GitHub Actions SSH 키와 서버의 GitHub clone 인증은 서로 다른 인증입니다.

EC2 폴더의 `.env`는 `.env.example`을 기준으로 실제 값을 채웁니다. TiDB 값은 기존 운영 계정의 값을 그대로 사용하고 DB 이름은 실제 스키마명으로 확인합니다. `INTERNAL_API_KEY`에는 길고 임의인 동일한 값을, AI 기능을 쓰는 경우 `OPENAI_API_KEY` 및 필요한 API 키를 설정합니다. OAuth 공급자 콘솔에는 HTTPS 도메인의 콜백 주소도 등록해야 합니다. 값은 CI 로그나 문서에 붙여 넣지 마세요.

## EC2 최초 준비 (Ubuntu 기준)

1. AWS 보안 그룹에서 TCP 22는 관리자 접속 IP로 제한하고 TCP 80/443은 공개합니다. TCP 8000은 열지 않습니다. Nginx를 준비하기 전 임시로 8080을 공개했다면 Nginx 확인 후 규칙을 제거합니다.
2. Docker Engine과 Compose plugin, Git, curl, Nginx, Certbot 및 Nginx 플러그인을 설치합니다. 실제 설치 명령은 인스턴스 Ubuntu 버전에 맞춰 Docker 공식 저장소를 사용합니다.
3. 저장소를 `/opt/zipchatgo`에 clone하고 `.env`를 만듭니다. `chmod 600 .env`로 권한을 제한합니다.
4. Spring과 AI 서버를 먼저 올리고 상태를 확인합니다.

Docker daemon은 부팅 시 자동으로 시작하도록 설정합니다.

```bash
sudo systemctl enable --now docker
sudo systemctl enable --now nginx
```

```bash
cd /opt/zipchatgo
docker compose config --quiet
docker compose up -d --build
docker compose ps
curl --fail http://127.0.0.1:8080/health
docker compose logs --tail=200 spring ai-server
```

Docker Compose의 `restart: unless-stopped`가 재부팅 후 컨테이너를 다시 올립니다. Compose 포트 설정은 Spring 8080만 호스트에 게시하고 FastAPI 8000은 내부 네트워크에서만 접근하도록 되어 있습니다.

## DuckDNS와 IP 선택

운영 안정성을 우선하면 EC2에 Elastic IP를 할당해 연결하는 것을 권장합니다. 인스턴스 Stop/Start 뒤에도 IP와 DNS가 유지되어 HTTPS 인증서와 DNS 전파 문제를 줄입니다. AWS 공개 IPv4는 현재 주소당 `$0.005/시간` (730시간 기준 약 `$3.65/월`) 과금되며, Elastic IP로 바꾸면 기존 자동 할당 IPv4 대신 사용하므로 IPv4 하나의 요금이 적용됩니다. [AWS VPC 공개 IPv4 가격](https://aws.amazon.com/vpc/pricing/)

추가 고정 IP 비용을 피하려면 동적 퍼블릭 IP와 DuckDNS 자동 갱신을 사용할 수 있습니다. Stop/Start 후 새 IP를 확인하고 타이머가 DNS를 갱신하기까지 접속 공백이 생길 수 있습니다. 현재 EC2 계정의 Free Tier 또는 크레딧 적용 여부는 AWS Billing에서 확인하세요.

DuckDNS 갱신기를 설치하고 비밀 토큰을 서버에만 저장합니다.

```bash
sudo install -d -m 700 /etc/zipchatgo
sudo install -m 600 /dev/null /etc/zipchatgo/duckdns.env
sudoedit /etc/zipchatgo/duckdns.env
# 파일 내용: DUCKDNS_TOKEN=<DuckDNS 계정 토큰>
sudo install -m 750 deploy/duckdns/update-ip.sh /usr/local/sbin/zipchatgo-duckdns-update
sudo install -m 644 deploy/duckdns/zipchatgo-duckdns.service /etc/systemd/system/
sudo install -m 644 deploy/duckdns/zipchatgo-duckdns.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now zipchatgo-duckdns.timer
sudo systemctl start zipchatgo-duckdns.service
sudo journalctl -u zipchatgo-duckdns.service -n 50 --no-pager
```

스크립트는 EC2 Instance Metadata Service v2를 사용합니다. EC2 메타데이터 옵션에서 IMDSv2가 활성화되어 있고 인스턴스가 DuckDNS HTTPS로 나갈 수 있어야 합니다.

## 자동 배포

`.github/workflows/deploy.yml`은 `main` push에서 Spring JAR 빌드와 Compose 설정 검증을 수행한 다음 EC2에 SSH 접속합니다. 서버에서 fast-forward로 커밋을 갱신하고 이미지를 빌드/재기동한 뒤 `/health`를 최대 3분 확인합니다. 실패 시 Actions에 배포 단계 결과가 표시되며 EC2 로그는 다음으로 확인합니다.

```bash
cd /opt/zipchatgo
docker compose ps
docker compose logs --tail=200 spring ai-server
```

운영 배포를 위한 GitHub Secrets 등록 전에는 SSH 배포 단계가 성공하지 않습니다. `workflow_dispatch`는 빌드/설정 검증만 실행합니다.

## Nginx와 HTTPS

Nginx 사이트 설정은 [`deploy/nginx/zipchatgoso.conf`](deploy/nginx/zipchatgoso.conf)를 사용합니다. DNS A 레코드가 현재 EC2 퍼블릭 IP를 가리키고 보안 그룹의 80/443이 열려 있는지 확인한 다음, Certbot으로 인증서를 발급합니다.

```bash
sudo install -m 644 deploy/nginx/zipchatgoso.conf /etc/nginx/sites-available/zipchatgoso
sudo ln -s /etc/nginx/sites-available/zipchatgoso /etc/nginx/sites-enabled/zipchatgoso
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d zipchatgoso.duckdns.org --redirect
sudo certbot renew --dry-run
```

Certbot이 발급한 인증서 경로는 `/etc/letsencrypt/live/zipchatgoso.duckdns.org/`입니다. Certbot의 systemd timer가 자동 갱신을 담당합니다. `sudo systemctl list-timers 'certbot*'` 및 `sudo certbot certificates`로 확인합니다.

## DuckDNS 주소 갱신

DuckDNS 토큰은 EC2의 `/etc/zipchatgo/duckdns.env`에 저장합니다. 위 설치 절차의 systemd timer가 EC2 Instance Metadata v2에서 현재 공인 IPv4를 읽어 5분마다 DuckDNS에 갱신하고 로그를 systemd journal에 남깁니다.

DuckDNS가 현재 계정/도메인 토큰을 받아들이는지 및 DNS 전파는 실제 인스턴스에서 별도로 확인해야 합니다. EC2 Stop/Start와 DNS 갱신까지 자동 검증하려면 실제 인스턴스를 중지/시작해야 하며 이 저장소 변경만으로 수행되지는 않습니다.

## 운영 점검

```bash
docker compose ps
docker compose logs --tail=200 spring ai-server
curl --fail https://zipchatgoso.duckdns.org/health
sudo nginx -t
sudo certbot certificates
sudo certbot renew --dry-run
sudo journalctl -u zipchatgo-duckdns.service -n 50 --no-pager
```

서비스 로그인 및 회원가입, TiDB 연결, OAuth 콜백은 운영 계정과 브라우저 세션으로 확인해야 합니다. 데이터베이스 초기화/마이그레이션은 이 배포 절차에서 실행하지 않습니다.
