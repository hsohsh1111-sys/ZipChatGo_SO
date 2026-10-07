# EC2 Docker Compose 배포

이 구성은 기존 Render용 `Dockerfile`, `ai-server/Dockerfile`, `render.yaml`을 변경하지 않고 같은 저장소를 EC2에서 실행하기 위한 최소 구성이다.

## 구성

- `spring`: 루트 `Dockerfile` 사용, 호스트의 `8080` 포트 공개
- `ai-server`: `ai-server/Dockerfile` 사용, Compose 내부의 `8000` 포트만 사용
- Spring → FastAPI: `http://ai-server:8000`
- FastAPI → Spring: `http://spring:8080`

두 서비스는 같은 `INTERNAL_API_KEY`를 사용한다. 루트 `.env`는 Git에 커밋하지 않으며, Compose가 변수 치환용으로 읽는다. 각 컨테이너에는 `docker-compose.yml`에 명시한 변수만 전달된다.

## EC2 최초 배포

EC2에 Docker Engine, Docker Compose plugin, Git을 설치하고 보안 그룹에서 테스트에 필요한 TCP `8080`만 허용한다. FastAPI의 `8000` 포트는 외부에 열지 않는다.

```bash
git clone <repository-url>
cd ZipChatGo_Real
git switch integrate-JongBeom-develop
cp .env.example .env
```

루트 `.env`에 실제 운영 값을 입력한다. 특히 DB 접속정보, `OPENAI_API_KEY`, `LAW_VECTOR_STORE_ID`, `INTERNAL_API_KEY`, MOLIT/Naver/Supabase 설정은 현재 서비스에서 사용하는 값을 넣는다. `AI_SERVER_URL`과 `SPRING_SERVER_BASE_URL`은 로컬 기본값을 유지해도 된다. Compose가 컨테이너 내부 주소로 덮어쓴다.

```bash
docker compose up -d --build
docker compose ps
docker compose logs --tail=200 spring
docker compose logs --tail=200 ai-server
```

확인 주소:

```text
http://<EC2_PUBLIC_IP>:8080/health
```

## 업데이트와 운영 명령

```bash
git pull
docker compose up -d --build
docker compose ps
```

로그 확인과 종료:

```bash
docker compose logs -f spring
docker compose logs -f ai-server
docker compose down
```

## 현재 범위 밖

Nginx, HTTPS, 도메인, JVM 메모리 옵션과 GitHub Actions 자동 배포는 이 초기 구성에 포함하지 않는다. 향후 자동 배포는 GitHub Actions가 SSH로 EC2에 접속해 대상 브랜치를 `git pull`한 뒤 `docker compose up -d --build`를 실행하는 구조로 추가할 수 있다. EC2 접속 정보는 저장소가 아니라 `EC2_HOST`, `EC2_USER`, `EC2_SSH_PRIVATE_KEY` GitHub Actions Secrets로 관리한다.
