# 집찾GO 프로젝트 문서

집찾GO(ZipChatGo)는 Spring Boot 기반 부동산 지도 서비스와 FastAPI 기반 AI 상담 기능을 함께 개발하는 프로젝트입니다. 이 문서는 GitHub에서 현재 프로젝트를 빠르게 파악할 수 있도록 기획부터 화면, 시스템 구조까지 연결한 문서 허브입니다.

## 문서 바로가기

| 순서 | 문서 | 확인할 내용 |
| --- | --- | --- |
| 1 | [프로젝트 플로 차트](./project-flow.md) | 사용자 여정과 일반 요청·AI 요청 흐름 |
| 2 | [프로젝트 기획서](./project-plan.md) | 문제, 목표, 범위, 원칙과 개발 단계 |
| 3 | [요구사항 정의서](./requirements.md) | 기능·비기능 요구사항 및 구현 상태 |
| 4 | [프로세스 정의서](./process.md) | 주요 시나리오의 단계별 처리와 예외 흐름 |
| 5 | [시스템 아키텍처 설계서](./architecture.md) | Frontend, Spring Boot, FastAPI, 외부 서비스 책임과 연결 |
| 6 | [화면 설계서](./screen-design.md) | 현재 화면 목록, 기능, 이동 관계와 간략 와이어프레임 |

## 현재 구현 기준

문서의 상태 표기는 저장소의 코드와 설정 파일을 확인해 작성했습니다.

- **구현 확인**: 현재 저장소에 동작 코드 또는 화면이 확인됨
- **부분 구현**: 일부 경로·기능만 구현되었거나 데이터가 시연용임
- **계획**: 개발 계획에는 있으나 현재 코드에서 구현을 확인하지 못함

상세 개발 원칙과 순서는 [AI 에이전트 개발 계획](./real_estate_ai_agent_development_plan_final.txt)을 따릅니다. 구현 상태는 이후 코드 변경에 맞춰 갱신해야 합니다.

## 주요 코드 위치

- 지도 화면: `src/main/resources/templates/property/map.html`
- 지도 동작: `src/main/resources/static/js/map.js`
- 지도 AI 채팅 UI: `src/main/resources/static/js/ai-chat.js`
- Spring AI API: `src/main/java/com/onrender/zipchatgo/ai/AiController.java`
- 지도 API: `src/main/java/com/onrender/zipchatgo/map/MapDataController.java`
- AI 서버 진입점: `ai-server/app/main.py`
- AI 요청·응답 모델 및 제한된 UI Action: `ai-server/app/schemas.py`

