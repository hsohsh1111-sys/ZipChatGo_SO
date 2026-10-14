# 집찾GO (ZipChatGo)

집찾GO는 지도 기반 부동산 탐색과 AI 대화를 연결하는 웹서비스 프로젝트입니다. Spring Boot가 웹 화면과 서비스 API를 제공하고, FastAPI가 AI Agent·도구 호출·법률 검색 경로를 담당합니다.

## 프로젝트 문서

프로젝트를 처음 보신다면 [문서 허브](./docs/README.md)에서 아래 자료를 순서대로 확인해 주세요.

1. [프로젝트 플로 차트](./docs/project-flow.md)
2. [프로젝트 기획서](./docs/project-plan.md)
3. [요구사항 정의서](./docs/requirements.md)
4. [프로세스 정의서](./docs/process.md)
5. [시스템 아키텍처 설계서](./docs/architecture.md)
6. [화면 설계서](./docs/screen-design.md)

## 주요 기능

- 네이버 지도 기반 매물 검색 및 주변 POI 확인
- 부동산 시장 동향과 분석 화면
- 지도 페이지의 AI 채팅 및 제한된 UI Action
- 로그인, 관심매물, 매물 등록 및 고객지원 화면
- FastAPI Agent의 매물·역·POI 검색과 법률 검색 경로

각 기능의 현재 구현 범위와 제약은 [요구사항 정의서](./docs/requirements.md)를 참고하세요. 화면별 데이터 출처와 시연 데이터 여부는 해당 화면의 안내 및 코드 설정을 기준으로 확인해야 합니다.

## 기술 구성

| 영역 | 기술 |
| --- | --- |
| 웹 서버·도메인 API | Java, Spring Boot, Thymeleaf |
| 화면 | HTML, CSS, JavaScript, Naver Maps JavaScript API |
| AI 서버 | Python, FastAPI, OpenAI API |
| AI 검색 | Spring 서비스 API 도구, 법률 Vector Store Retriever |
| 배포 구성 | Docker, Render 설정 |

## 로컬 개발

개발 계획 기준으로 Spring Boot는 `http://localhost:8080`, FastAPI는 `http://localhost:8000`에서 별도 실행합니다. 브라우저는 Spring Boot에 요청하고, AI 대화는 Spring Boot를 거쳐 FastAPI로 전달됩니다.

환경변수와 실행 상세는 `.env.example`, `README_RENDER_DEPLOY.md`, `README_EC2_DEPLOY.md` 및 `ai-server/` 설정을 참고하세요. 비밀 키를 README나 소스 코드에 기록하지 마세요.

## 현재 개발 기준

상세 단계와 아키텍처 원칙은 [AI Agent 개발 계획](./docs/real_estate_ai_agent_development_plan_final.txt)에 정리되어 있습니다. 문서에 표시된 구현 상태는 저장소 확인 시점 기준이며, 코드가 변경되면 함께 갱신해야 합니다.
