# 집찾GO 요구사항 정의서

## 1. 상태 정의

| 상태 | 의미 |
| --- | --- |
| 구현 확인 | 현재 코드에 해당 화면·API·처리 경로가 존재함 |
| 부분 구현 | UI/일부 경로는 존재하나 데이터·운영 연동 또는 전체 흐름이 제한됨 |
| 계획 | 개발 기준 문서에는 있으나 현재 구현 근거를 확인하지 못함 |

## 2. 기능 요구사항

| ID | 요구사항 | 상태 | 확인 기준 / 관련 영역 |
| --- | --- | --- | --- |
| FR-01 | 사용자는 서비스 홈에서 주요 페이지로 이동할 수 있어야 한다. | 구현 확인 | `PageController`, Thymeleaf templates |
| FR-02 | 사용자는 지도에서 매물을 탐색하고 검색할 수 있어야 한다. | 구현 확인 | `/property/map`, `/api/map/properties/search`, `map.js` |
| FR-03 | 사용자는 매물 ID 묶음 조회, 상세 정보 및 가격 이력을 확인할 수 있어야 한다. | 구현 확인 | `MapDataController` 및 매물 API |
| FR-04 | 지도에서 교통역과 POI 정보를 검색할 수 있어야 한다. | 구현 확인 | `/api/map/transit/stations/search`, `/api/map/pois/search` |
| FR-05 | 사용자는 시장 지표와 분석 페이지를 볼 수 있어야 한다. | 부분 구현 | `/market/**`, `/api/market/**`; 일부 지표는 시연 데이터 안내 포함 |
| FR-06 | 사용자는 지도 화면에서 자연어로 AI에 질문할 수 있어야 한다. | 구현 확인 | `ai-chat.js`, `POST /api/ai/chat` |
| FR-07 | AI 요청에는 메시지, 제한된 대화 이력, App State, 최근 매물 문맥을 전달할 수 있어야 한다. | 구현 확인 | `ai-server/app/schemas.py`의 `ChatRequest` |
| FR-08 | FastAPI는 매물·POI·역 검색 도구와 법률 검색 경로를 제공해야 한다. | 구현 확인 | `ai-server/app/main.py`, `app/tools/`, `app/retrievers/` |
| FR-09 | AI의 지도·매물 조작은 정의된 Action 종류와 인자 검증 범위에서만 실행해야 한다. | 구현 확인 | `UiAction` 스키마, `ai-chat.js`, 지도 Action 실행기 |
| FR-10 | 사용자는 로그인·회원가입·OAuth 시작 및 계정 관련 화면을 이용할 수 있어야 한다. | 부분 구현 | `AuthController`, 회원 페이지; 공급자 설정·운영 상태는 환경 구성에 따름 |
| FR-11 | 사용자는 관심매물을 조회하거나 조작할 수 있어야 한다. | 부분 구현 | 관심매물 페이지/API 범위 확인 필요; AI Action 일부 존재 |
| FR-12 | 사용자는 매물을 등록하고 자신의 등록 매물을 관리할 수 있어야 한다. | 부분 구현 | 등록·내 매물 경로 및 API 존재; 외부 저장소·검수 흐름은 설정에 따름 |
| FR-13 | 사용자는 가이드, 문의, 공지 및 서비스 소개 콘텐츠를 확인할 수 있어야 한다. | 부분 구현 | `/support/**`, 문의 API; 일부 페이지는 정적 콘텐츠 |
| FR-14 | AI가 부동산 분석 화면의 조건을 선택하고 분석을 실행해야 한다. | 계획 | 개발 계획의 선택/실행 Tool; 현재 Agent 연결 여부 확인 필요 |
| FR-15 | 대화 로그·Tool trace·피드백을 평가 데이터로 축적해야 한다. | 계획 | 운영 로그/평가 수집 기능 확장 필요 |

## 3. AI UI Action 계약

현재 요청/응답 스키마에서 확인되는 Action은 `MOVE_MAP`, `ZOOM_MAP`, `FIT_BOUNDS`, `HIGHLIGHT_PROPERTIES`, `SET_POI_CATEGORY`, `HIGHLIGHT_POIS`, `CLEAR_POI_HIGHLIGHTS`, `OPEN_PROPERTY`, `ADD_FAVORITE`, `REMOVE_FAVORITE`, `SELECT_REGION`입니다. 이 목록은 개발 계획의 예시 목록과 다를 수 있으므로 구현 변경 때 스키마를 기준으로 갱신합니다.

Action 실행 시 타입과 인자 범위를 검증하며, 브라우저가 임의 코드를 해석해 실행하는 구조를 사용하지 않습니다.

## 4. 비기능 요구사항

| ID | 요구사항 | 기준 |
| --- | --- | --- |
| NFR-01 | 서비스 비밀값은 코드와 문서에 포함하지 않는다. | 환경 변수/배포 Secret 사용, `.env` 비추적 |
| NFR-02 | FastAPI 내부 요청은 내부 API Key 설정 시 인증을 검증한다. | `X-Internal-Api-Key` 검증 |
| NFR-03 | AI 입력·상태·응답은 스키마와 허용 목록으로 제한한다. | Pydantic 모델과 Spring 요청 DTO |
| NFR-04 | AI 서버 장애와 외부 모델 오류를 API 오류로 처리한다. | Spring/FastAPI 오류 응답 경로 |
| NFR-05 | 화면은 데스크톱과 모바일에서 주요 사용자 흐름을 제공해야 한다. | 지도/공통 CSS의 반응형 동작 확인 필요 |
| NFR-06 | 외부 API·시연 데이터의 출처와 한계를 화면에서 알린다. | 분석 화면 데이터 고지 등 |
| NFR-07 | 기능은 프론트·Spring·AI 서버의 역할 경계를 유지해야 한다. | Spring이 도메인 API, FastAPI가 AI 작업 담당 |

## 5. 범위 제외 및 제약

- Fine-tuning은 현재 개발 범위가 아니다.
- AI가 DB에 직접 SQL을 실행하거나 임의 DOM을 변경하지 않는다.
- 데이터 갱신 주기, 법률 자료 최신성, 외부 API 이용 가능성은 각 데이터 공급원과 배포 설정에 의존한다.
- 시장 예측이나 법률 응답은 의사결정을 돕는 참고 정보이며 보장·확정으로 표현하지 않는다.

