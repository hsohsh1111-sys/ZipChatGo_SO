# ZipChatGo 최종 프로젝트 발표자료 제작용 현황 보고서

> 조사 기준: 현재 체크아웃된 `integrate-JongBeom-develop` 브랜치의 HEAD `1eddec69e91c9bc218db3a38d05b3861a8eaada8`. 코드·설정·DB·외부 서비스를 변경하거나 시험을 재실행하지 않고, 저장소 코드와 `docs/evaluation`의 기록을 읽어 정리했다. 이 문서의 “구현”은 코드 경로가 존재한다는 뜻이다. 현재 운영 환경의 실시간 상태가 필요한 항목은 **확인 필요**로 표시했다.

## 1. Executive Summary

집찾GO는 **홈 → 지도 탐색과 AI 대화 → 부동산 분석 → 매물 등록·회원·고객센터**로 이어지는 부동산 웹서비스다. 지도에서는 매물과 주변 시설을 탐색한다. AI Agent는 현재 지도·선택 지역·선택 매물·관심매물·최근 대화 문맥을 받아 정해진 Backend Tool과 Frontend UI Action을 선택한다. 주요 구현 근거는 [`index.html`](../../src/main/resources/templates/index.html), [`map.js`](../../src/main/resources/static/js/map.js), [`ai-chat.js`](../../src/main/resources/static/js/ai-chat.js), [`openai_provider.py`](../../ai-server/app/providers/openai_provider.py)이다.

발표에서 특히 다음 네 가지를 구분해야 한다.

1. 시장 분석의 일부 화면은 국토교통부 API 집계값을 사용하지만, 일부 차트는 현재 집계값으로 만든 **합성 시계열**이며 예측·학군·교통 등의 화면은 **코드에 고정된 예시 데이터**를 사용한다. 근거: `MarketService.summary()`와 `sale.js.makeSeries()`, `ai-report.js`의 `aiMarketData`.
2. 지도 관심매물 ID는 회원 DB가 아닌 브라우저 `sessionStorage`의 `zipchatgo.favoritePropertyIds`에 저장된다. 근거: `favorite.js.loadFavoritePropertyIds()` / `saveFavoritePropertyIds()`.
3. 회원 등록 매물은 `member_property`에 `PENDING`으로 저장되며 관리자 승인 API가 있다. 지도는 별도 `properties` 테이블을 읽는다. 승인된 등록 매물을 지도 테이블로 자동 반영하는 코드는 **확인되지 않았다**. 근거: `MemberPropertyService.register()`, `AdminPropertyController.updateStatus()`, `MapDataService.PROPERTIES_SQL_TEMPLATE`.
4. 저장소에 평가 보고서와 배포 설정이 있지만, **현재 HEAD의 재실행 결과·현재 운영 Store ID·현재 서버 상태**는 코드 읽기만으로 확정할 수 없다.

## 2. 프로젝트 전체 개요

### 기준점과 구조

| 항목 | 조사 결과 |
|---|---|
| 현재 Git 브랜치 | `integrate-JongBeom-develop` |
| 현재 HEAD | `1eddec69e91c9bc218db3a38d05b3861a8eaada8` |
| 조사 시작 시 `git status --short --branch` | `## integrate-JongBeom-develop...origin/integrate-JongBeom-develop` |
| 조사 시작 시 tracked 변경사항 | 없음 |
| 주요 최상위 구조 | `src/` Spring Boot·Thymeleaf·정적 자원, `ai-server/` FastAPI·Agent, `docs/`, `.github/`, `gradle/`, `Dockerfile`, `docker-compose.yml`, `render.yaml` |

프로젝트의 기획 목표는 자연어 대화로 지도 상태, 사용자의 선택, 부동산 데이터와 웹사이트 기능을 이해하고 도구를 실행하는 AI Agent다. 이 목표는 현재 구현을 설명하는 자료와 구분해야 한다. 기획 근거는 [`AGENTS.md`](../../AGENTS.md)와 [`docs/real_estate_ai_agent_development_plan_final.txt`](../real_estate_ai_agent_development_plan_final.txt)이다.

현재 코드의 책임 분담은 다음과 같다.

- **Frontend:** Thymeleaf HTML, CSS, JavaScript. 지도 UI, 매물 목록·상세, 관심매물, 분석 화면, 등록 폼 및 AI Action 실행.
- **Spring Boot:** 회원·세션·OAuth, 지도 데이터 REST API, 시장 요약 API, 회원 등록 매물·문의 저장, FastAPI 호출. 코드: `src/main/java/com/onrender/zipchatgo/` 아래 `map`, `market`, `member`, `property`, `inquiry`, `ai` 패키지.
- **FastAPI:** OpenAI Responses API 호출, 도구 선택과 반복, App State·대화 문맥, 법률 Vector Store 검색, Pydantic Action 검증. 코드: `ai-server/app/main.py`, `providers/openai_provider.py`, `schemas.py`, `tools/`, `retrievers/`.
- **데이터:** 지도용 `properties`·`poi`, 회원용 `member`·`member_property` 계열과 `inquiry`, 정적 JSON·GeoJSON, Supabase 파일 저장소, 법률 OpenAI Vector Store. 서로 다른 출처의 자료를 하나의 DB에 모두 저장한다고 설명하면 안 된다.

서비스 전체 흐름은 `홈 → 로그인 또는 AI 질문 → 지도에서 매물·POI 탐색 → 관심매물 및 후속 질문 → 시장 분석 → 필요하면 매물 등록·고객센터`다. 다만 등록 매물과 지도 매물의 자동 통합은 **확인 필요**다.

## 3. 서비스 소개

### 홈과 메뉴

`GET /`는 `PageController.index()`가 `templates/index.html`을 렌더링한다. 첫 화면의 AI 질문창은 `main.js`가 입력 문구를 `sessionStorage`의 `zipchatgo.pendingAiQuestion`에 넣고 `/property/map`으로 이동시킨다. 홈의 기능 카드는 서비스 소개 `/about/service`, 지도 `/property/map`, 분석 `/market/trend`, 등록 `/property/register`로 연결된다. 상단에는 로그인·회원가입·고객센터·관심매물·로그아웃 링크가 있다. 근거: [`PageController.java`](../../src/main/java/com/onrender/zipchatgo/controller/PageController.java), [`index.html`](../../src/main/resources/templates/index.html), [`main.js`](../../src/main/resources/static/js/main.js).

| 진입점 | URL | Controller / 화면 |
|---|---|---|
| 홈 | `/` | `PageController.index()` → `index.html` |
| 지도·AI | `/property/map` | `MapPageController.map()` → `property/map.html` |
| 관심매물 | `/favorites`, `/favorite` | `FavoritePageController` → `favorite/favorite.html` |
| 분석 허브 | `/market/trend` | `PageController.marketTrend()` → `market/trend.html` |
| 분석 세부 | `/market/sale`, `/market/jeonse`, `/market/volume`, `/market/region`, `/market/region-flow`, `/market/rate`, `/market/school`, `/market/traffic`, `/market/cost`, `/market/ai-report` | `PageController`의 각 매핑 → `templates/market/` |
| 등록·내 매물 | `/property/register`, `/my/properties`, `/my/properties/{propertyId}` | `PageController` → `property/register.html`, `member/my-properties.html`, `member/my-property-detail.html` |
| 회원 | `/member/login`, `/member/signup`, `/account` | `PageController` → `member/auth.html`, `member/account.html` |
| 고객센터·안내 | `/support/contact`, `/support/guide`, `/support/live`, `/support/notice` 등 | `PageController` → `templates/support/` |

홈은 “AI와 이야기하며”라는 문구로 대화형 탐색을 강조한다. 분석 허브는 “숫자를 넘어 흐름을 읽는 부동산 분석”을 표방한다. 여러 템플릿에 로고·상단 메뉴·하단 링크가 있으며, `common.js`가 모바일 메뉴, 로그인 상태 표시와 로그아웃을 처리한다. 완전한 단일 공통 레이아웃 파일이라기보다 각 템플릿이 공통 CSS·JS를 사용하는 방식이다. 근거: `index.html`의 hero·feature link, `market/trend.html`, [`common.js`](../../src/main/resources/static/js/common.js), [`common.css`](../../src/main/resources/static/css/common.css).

처음 접속한 사용자는 홈의 AI 질문을 입력하거나 지도·분석·등록·서비스 소개로 이동할 수 있다. 지도와 등록 등 일부 페이지는 `WebConfig`의 `AuthInterceptor` 보호 대상이다. 모든 분석 세부 URL이 같은 인터셉터 목록에 들어간 것은 아니므로 “분석 전체가 로그인 필수”라고 일반화하면 안 된다. 근거: [`WebConfig.java`](../../src/main/java/com/onrender/zipchatgo/config/WebConfig.java).

## 4. 지도 탐색

지도 화면은 [`property/map.html`](../../src/main/resources/templates/property/map.html), [`map.js`](../../src/main/resources/static/js/map.js), [`map.css`](../../src/main/resources/static/css/map.css), Spring의 [`MapPageController.java`](../../src/main/java/com/onrender/zipchatgo/map/MapPageController.java), [`MapDataController.java`](../../src/main/java/com/onrender/zipchatgo/map/MapDataController.java), [`MapDataService.java`](../../src/main/java/com/onrender/zipchatgo/map/MapDataService.java)로 구성된다.

| 기능 | 실제 사용자 동작과 화면 | JS / Spring API / 데이터 |
|---|---|---|
| Naver 지도·초기 위치 | `/property/map`에 들어가 지도 탐색. 기본 중심은 `(37.40, 127.15)`. SDK 확대 10–18을 화면 단계 0–8로 변환하고 최초 SDK 확대는 11이다. | `map.html`의 Naver Maps SDK, `map.js`의 `INITIAL_CENTER`, `APP_MIN_ZOOM`, `APP_START_ZOOM`, `APP_MAX_ZOOM`, `new naver.maps.Map()` |
| 확대 단계별 매물·지역 | 낮은 단계에서 시군구·동, 높은 단계에서 개별 매물·목록. Supercluster로 군집 마커를 표시한다. 지도상 매물 마커 최대 700개, 목록 최대 200건, POI 마커 최대 500개 등의 표시 제한이 있다. | `map.js`의 `SIGUNGU_STAGE_MAX`, `DONG_STAGE_MAX`, `MAX_VISIBLE_MARKERS`, `MAX_LIST_ITEMS`; `map.html`의 Supercluster 스크립트 |
| 검색·필터 | 검색창에서 주소·단지명 등의 제안을 받고, 가격·면적·준공연수 필터를 적용한다. 목록과 마커를 클릭해 상세를 확인한다. | `map.html`의 `searchInput`, `propertyFilters`; `map.js`의 `PROPERTY_FILTER_DEFINITIONS`, `applyFilters()`; `GET /api/map/properties`와 `/api/map/properties/search` |
| 지도 범위 기반 조회 | Agent·API는 지도 bounds, 법정동 코드, 매물 유형, 최대 매매가, 정렬, 거래 조회 모드를 전달할 수 있다. UI의 면적·준공연수 필터와 Agent 검색 Tool 인자는 동일하지 않다. | `MapDataController.searchProperties()` → `MapDataService.searchProperties()` → JDBC `properties` 또는 fallback JSON |
| 매물 상세·가격 이력 | 선택 매물의 가격·면적·위치 및 주변 정보 확인. 월별 가격 이력은 동일 건물·주소 및 유사 평형의 거래를 묶는다. | `map.js`; `GET /api/map/properties/{propertyId}/price-history`; `MapDataService.PRICE_HISTORY_SQL` |
| 지역 선택 | 분당구 법정동 경계를 선택하고 지도에 표시한다. 선택 지역과 현재 지도 중심의 법정동은 AI App State에 포함된다. | `map.js.selectLegalDongByName()`, `getAiAppState()`; `static/data/bundang_legal_dong.geojson` |
| POI | 공공기관·교육·교통·의료·중개 대분류를 켜고 끈다. 세부 학교·병원·약국·버스정류장 등은 DB에 해당 하위 분류가 있을 때 검색할 수 있다. | `map.js.POI_CATEGORY_CONFIG`; `GET /api/map/pois`, `/api/map/pois/search` → JDBC `poi` 또는 `poi_database.json` |
| 역·버스 | 지도에서 교통 POI·버스 노선을 표시한다. AI 역명 검색은 별도 정적 역 데이터에서 이름을 찾는다. | `map.js`; `GET /api/map/transit/stations/search` → `static/data/transit_points_bundang.json` |
| 관심매물 | 하트로 매물을 추가·제거하고 `/favorites`에서 목록 카드, 지도 복귀를 사용한다. | `map.js`, [`favorite.js`](../../src/main/resources/static/js/favorite.js); 저장 위치는 `sessionStorage` |
| AI 지도 Action | 매물 검색 후 지도 이동·범위 맞춤·강조·상세 열기, 지역 선택, POI 카테고리·강조를 수행한다. | `map.js.executeAiMapActions()`; 허용 Action만 분기 처리 |

`MapDataController`의 주요 API는 `GET /api/map/properties`, `/properties/search`, `/properties/by-ids`, `/transit/stations/search`, `/properties/{id}/price-history`, `/pois`, `/pois/search`다. 매물 검색 `limit`는 1–20, POI 반경은 좌표가 있을 때 1–50,000m, POI 결과 `limit`도 1–20으로 검증한다. 거래 검색에는 `properties`, `transactions`, `selected_building_transactions` 모드가 있다. 근거: `MapDataController.searchProperties()` / `searchPois()`.

`MapDataService`는 `properties` 최근 12개월을 지도용으로 읽고 거래 조회에서는 최대 36개월 범위를 사용한다. DB 접근 오류 때 `static/data/properties.json`, `poi_database.json`으로 전환할 수 있다. 응답은 `X-Map-Data-Source` 헤더로 `tidb` 또는 `fallback-json`을 표시하므로 화면이 항상 DB 실자료라고 단정하면 안 된다. `properties` 원자료의 수집·적재 경로와 정확한 공공데이터셋 이름은 **확인 필요**다.

## 5. AI Agent / Multi-turn

### 실제 요청 경로

```text
브라우저 map.html + ai-chat.js
  POST /api/ai/chat {message, appState, history, recentContext}
    → Spring AiController.chat() / AiService.chat()
      POST FastAPI /agent/chat (INTERNAL_API_KEY)
        → OpenAIProvider.generate()
          → OpenAI Responses API ↔ 허용된 Tool handler
        ← ChatResponse {message, actions, recentContext, propertySearchMode}
    ← 브라우저 ai-chat.js
      → map.js.executeAiMapActions(actions)
```

근거: [`AiController.java`](../../src/main/java/com/onrender/zipchatgo/ai/AiController.java), [`AiService.java`](../../src/main/java/com/onrender/zipchatgo/ai/AiService.java), [`ai-server/app/main.py`](../../ai-server/app/main.py), [`ai-chat.js`](../../src/main/resources/static/js/ai-chat.js).

`ai-chat.js`는 `window.zipchatgoMapState`에서 App State를 수집한다. Pydantic `AppState`에는 현재 페이지, 지도 중심·범위·화면 확대 단계, 현재/선택 법정동, 선택 매물 ID·요약, 관심매물 ID, 키워드·유형·최대 가격 필터가 있다. `ChatRequest.message`는 최대 1,000자, history는 최대 8개 메시지다. FastAPI는 `INTERNAL_API_KEY`를 검사하고 매물·POI·역·인접 법정동·법률 검색 handler를 Provider에 전달한다. 근거: [`schemas.py`](../../ai-server/app/schemas.py), `main.py.agent_chat()`.

### 현재 등록된 Tool registry: 정확히 16개

다음은 `openai_provider.py`의 `AGENT_TOOLS`에 실제 등록된 함수 이름이다. 현재 요청·App State·handler 유무에 따라 `available_tools`에서 일부를 제외한다. 기획서에만 있는 분석 실행 Tool·앱 지식 RAG Tool을 여기에 포함하면 안 된다.

| 함수 이름 | 역할과 처리 위치 |
|---|---|
| `search_properties` | 조건·현재 지도 bounds·법정동으로 매물/거래 조회. FastAPI `PropertySearchTool` → Spring `/api/map/properties/search`. |
| `get_properties_by_ids` | 선택·관심 매물 ID 조회. `PropertySearchTool.get_by_ids()` → Spring `/api/map/properties/by-ids`. |
| `search_poi` | 시설 카테고리·하위 분류·지역·좌표·반경 검색. `PoiSearchTool` → Spring `/api/map/pois/search`; 결과 POI 지도 강조 Action도 구성. |
| `set_presented_properties` | 답변에 실제 표시한 매물 ID의 순서를 기록해 “그중 두 번째” 등 후속 문맥에 사용. |
| `search_real_estate_law` | 법률 Vector Store 검색. `RealEstateLawSearchTool` → Retriever. |
| `find_transit_station` | 이름에 ‘역’이 명시된 역 찾기. `TransitStationTool` → Spring `/api/map/transit/stations/search`. |
| `get_adjacent_legal_dongs` | 분당구 법정동 경계를 공유하는 인접 동 조회. `LegalDongAdjacencyTool`과 정적 경계 자료. |
| `move_map`, `zoom_map`, `fit_bounds` | 좌표 이동·상대 줌·검색 매물 범위 맞춤 Action. |
| `highlight_properties`, `open_property` | 검증된 매물 ID의 강조·상세 열기 Action. |
| `add_favorites`, `remove_favorites`, `clear_favorites` | 명시적으로 요청한 관심매물 변경 Action. 브라우저 `sessionStorage`를 수정하며 Spring DB favorite API는 사용하지 않는다. |
| `select_region` | 검증된 분당구 법정동 경계 선택 Action. |

근거: [`openai_provider.py`의 Tool schema·`AGENT_TOOLS`](../../ai-server/app/providers/openai_provider.py), [`tools/`](../../ai-server/app/tools), [`map.js.executeAiMapActions()`](../../src/main/resources/static/js/map.js).

Provider는 Responses API의 함수 호출을 받아 해당 handler를 실행하고 결과를 `function_call_output`으로 다음 모델 입력에 다시 넣는다. 호출 반복 한도는 4회다. Action은 Pydantic 구조로 파싱하고, 브라우저에서도 좌표·ID·Action 종류·POI 개수 등을 검사한 뒤 정해진 분기만 실행한다. 모델이 임의 JavaScript를 생성해 실행하는 방식이 아니다. 근거: `OpenAIProvider.generate()`의 `for iteration in range(4)`, `_parse_ui_action()`, `map.js.executeAiMapActions()`.

### 대화 연속성의 저장 위치와 한도

| 문맥 | 현재 구현 |
|---|---|
| 대화 원문 history | `ai-chat.js`의 브라우저 메모리 배열. 최근 8개 메시지, 총 8,000자 이내로 제한. FastAPI도 `_bounded_history()`로 한도를 유지한다. |
| `recent_property_ids` | 최근 화면에 제시한 순서의 매물 ID 최대 10개. “그중 첫 번째/두 번째”의 기준. |
| `last_referenced_property_id` | 최근 단일 참조·열어 본 매물 ID. “그 매물”의 기준 중 하나. |
| 최근 매물 요약 | `recent_properties` 최대 10개. 이름·가격·좌표 등을 제한적으로 유지. |
| 현재 App State | 지도 위치·범위, 선택 지역·매물, 관심매물 ID 및 일부 필터를 매 요청에 다시 보냄. |
| 새 대화·새로고침 | 새 대화 버튼은 history·recentContext를 비운다. 새로고침 후 대화 원문을 복원하는 DB·`localStorage` 코드는 확인되지 않음. |
| `sessionStorage` | 관심매물 ID, 지도 viewport, 홈에서 지도에 전달할 첫 질문 등에 사용. 대화 원문 저장용은 아님. |
| `localStorage` | 게스트 모드 표시 플래그 등에 사용. Agent 대화 원문 저장용은 아님. |
| 서버 DB / OpenAI | 대화 DB 저장 코드 확인 안 됨. OpenAI 요청은 `store=False`; `previous_response_id` 사용도 확인되지 않음. |

근거: `ai-chat.js`의 `HISTORY_MAX_MESSAGES`, `HISTORY_MAX_CHARACTERS`, `resetConversation()`, `sendMessage()`; `schemas.py.RecentContext`; `openai_provider.py`의 `_bounded_history()`와 Responses 요청 옵션; `favorite.js`·`map.js`의 저장 키.

## 6. 법률 RAG

운영 경로는 `search_real_estate_law → OpenAI Vector Store Retriever → 검색 결과를 Agent에 재주입 → 최종 조문·출처 검사`다. `LAW_VECTOR_STORE_ID`는 환경변수이므로 **현재 운영 Store ID는 저장소 코드만으로 확인 필요**다. 과거 평가 보고서에 기록된 ID는 그 평가 실행 시점의 값이다. 근거: [`main.py.get_law_retriever()`](../../ai-server/app/main.py), [`openai_vector_store_law_retriever.py`](../../ai-server/app/retrievers/openai_vector_store_law_retriever.py).

### 자료 구성과 검색

`law/national_law_api.py`는 국가법령정보센터 공동활용 Open API의 `target=eflaw` 경로를 다룬다. `law/documents.py`는 법령을 **조문별 Markdown 한 파일**로 생성하고 법령명·법령 종류·조문 번호·제목·시행일·공포일·공식 URL 등을 메타데이터로 기록한다. 수집과 Store 업로드는 사전 구축 작업이며 일반 채팅 요청마다 법령 API를 직접 호출하지 않는다. 근거: [`national_law_api.py`](../../ai-server/app/law/national_law_api.py), [`documents.py`](../../ai-server/app/law/documents.py), [`vector_store.py`](../../ai-server/app/law/vector_store.py).

`RealEstateLawSearchTool.search()`의 검색 흐름은 다음과 같다.

1. **법령명 탐지:** 사용자 질문의 정규 법령명을 우선하고, 없으면 모델 query·요청 인자에서 탐지한다. `law/search_routing.py`와 `law/canonical_catalog.py`가 관련 로직이다.
2. **법령명 + 조문번호 탐지:** 질문에 명시된 쌍을 우선한다. 정확한 쌍이 있으면 `law_name`·`article_number` 메타데이터를 모두 조건으로 지정해 검색한다.
3. **정제된 Query 기반 의미검색:** exact hit가 없을 때 모델이 만든 `arguments.query`를 그대로 의미검색에 사용한다. 사용자 질문 전체를 의미검색 query에 덧붙이지 않는다. 확인된 법령명이 있으면 `law_name` 필터를 적용한다.
4. **Semantic fallback:** 법령명 필터 의미검색도 0건이면 같은 query로 필터 없는 의미검색을 수행한다.

Retriever 기본값은 최대 5건, 최소 score `0.5`, 최고 결과 대비 상대 score 비율 `0.9`, `rewrite_query=False`다. 근거: [`real_estate_law.py`](../../ai-server/app/tools/real_estate_law.py), [`search_routing.py`](../../ai-server/app/law/search_routing.py), [`openai_vector_store_law_retriever.py`](../../ai-server/app/retrievers/openai_vector_store_law_retriever.py).

### 성공·부분 성공·실패 응답 정책

- 검색 근거가 충분하면 실제 반환된 조문·시행일을 사용해 설명한다.
- 근거가 일부만 있으면 검색에서 확인한 내용과 일반 법률 지식에 따른 설명을 구분하도록 Provider가 지시한다. 확인되지 않은 부분을 공식 검색으로 검증된 것처럼 표현하면 안 된다.
- 결과가 0건이면 공식 검색에서 직접 확인하지 못했다고 밝히고, 미확인 정확 조문·판례·시행일을 인용하지 않는다. 필요하면 계약 유형이나 상황을 구체화해 달라고 안내한다.
- `_ground_law_response()`는 모델이 직접 만든 법령 출처 링크를 제거하고, 인용 조문이 검색 결과 집합에 있는지·답변에 허용 법령명이 있는지 확인한다. 불일치하면 안전 문구로 대체하고, 확인된 결과의 공식 URL만 붙인다. **답변의 모든 법적 주장을 판례 수준까지 검증하는 완전한 보증은 아니다.**

근거: `openai_provider.py`의 `UI_ACTION_INSTRUCTIONS`, `search_real_estate_law` 호출 후 `post_tool_instruction`, `_ground_law_response()`.

**C2.3 구분:** `ai-server/app/evaluation/pair_aware_grounding_validator_c23.py` 및 관련 보고서는 평가용이다. 현재 `/agent/chat` 운영 응답의 hard gate로 연결된 코드는 확인되지 않는다. 운영에는 위의 `_ground_law_response()` 검사가 있다. 과거 A/B/C1 같은 내부 실험 명칭은 발표 본문에서 각각 “법령명 탐지”, “법령명 + 조문번호 탐지”, “정제된 Query 기반 의미검색”, “Semantic fallback”으로 설명하는 편이 정확하다. 근거: [`pair_aware_grounding_validator_c23.py`](../../ai-server/app/evaluation/pair_aware_grounding_validator_c23.py), [`production_law_agent_rag_fallback_policy_20260915.md`](../evaluation/production_law_agent_rag_fallback_policy_20260915.md).

## 7. 부동산 분석

공통 Backend는 `MarketApiController.summary()`의 `GET /api/market/summary?region={5자리코드}&month={YYYYMM}`이다. `MarketService.summary()`는 API 키가 있으면 국토교통부 **아파트 매매 실거래가**와 **아파트 전월세 실거래가** 엔드포인트를 대상 월·전월에 각각 호출한다. XML 항목에서 평균 매매금액, 평균 보증금, 전월 대비 변화율, 매매 건수, 최대 50건의 예시 거래를 만든다. API 키가 없으면 `source=demo`가 반환되고, 분석 허브 `market-api.js`는 API 조회 실패 시 `source=auto-demo`인 프런트 고정값도 표시한다. 분석 전용 Repository·DB 테이블은 확인되지 않았다. 근거: [`MarketApiController.java`](../../src/main/java/com/onrender/zipchatgo/market/MarketApiController.java), [`MarketService.java`](../../src/main/java/com/onrender/zipchatgo/market/MarketService.java), [`market-api.js`](../../src/main/resources/static/js/market-api.js).

아래 표의 “구현”은 화면·계산 코드가 존재한다는 의미다. 운영 API 키와 실제 데이터 수신 여부는 **확인 필요**다.

| 기능·URL | 사용자 입력 | 데이터 원천·Backend 처리 | 계산·Frontend 출력 | 현재 구현 상태·파일 |
|---|---|---|---|---|
| 분석 허브 `/market/trend` | 지역·화면 진입 | `/api/market/summary`; 실패 시 프런트 고정 preset | 매매·전세·거래량 카드, 최근 거래 목록, 하위 분석 링크. `source` 표시 | 구현. `templates/market/trend.html`, `static/js/market-api.js`, `static/css/market-detail.css`·관련 스타일. 실자료 여부는 source 확인 필요. |
| 매매 `/market/sale` | 기준·비교 지역, 면적, 기간, 거래표 면적 | 동일 실거래 요약 API | 평균 매매가·전월 변화율·지역 격차·평당 추정값·최근 거래표·SVG 선 차트. `makeSeries()`가 현재값과 한 달 변화율로 **합성 기간별 선**을 생성 | 구현. `market/sale.html`, `sale.js.renderDashboard()`·`renderChart()`·`makeSeries()`. 실측 월별 차트 아님. |
| 전세 `/market/jeonse` | 기준·비교 지역, 면적, 기간 | 동일 API의 보증금 요약과 예시 임대 거래 | 평균 보증금·변화율·매매 대비 비율·거래표·SVG 선 차트. 기간별 선은 합성 | 구현. `market/jeonse.html`, `jeonse.js`. 원천이 전월세 API이므로 집계 전체를 순수 전세 평균이라 단정하지 않음. |
| 거래량 `/market/volume` | 지역, 매매/전세, 면적, 기간 | 동일 API의 `tradeCount`와 임대 거래 표본 | 건수·변화율·일평균·활동점수·최근 거래표·SVG 막대. 기간별 막대는 합성 | 구현이나 **전세 건수 주의**: `volume.js`는 `rentCount`·`rentVolumeChangeRate`를 읽는데 실API `MarketService.buildSummary()`는 이를 만들지 않음. `market/volume.html`, `volume.js`. |
| 지역 비교 `/market/region` | 기준·비교 지역, 면적, 우선순위 | 동일 API의 매매·보증금·건수 | 전세가율, 유동성·안정성·가치의 JS 산식, 가중 점수, 비교표·SVG 지수 차트 | 구현. `market/region.html`, `region.js.buildMetrics()`·`score()`·`makeIndexSeries()`. 점수·지수 시계열은 자체 추정이며 공인 지수 아님. |
| 지역 관심 이동 `/market/region-flow` | 출발 지역, 주거유형, 연령, 기간 | JS `flowData` 고정값; 별도 API·Repository 없음 | SVG 이동 흐름, 순위·일별 추이·표. 조건에 따른 배율 계산 | 예시 데이터 화면. `market/region-flow.html`, `region-flow.js`. 실제 사용자 이동 로그 분석 아님. |
| 금리 `/market/rate` | 대출금액, 기간, 금리형태, 상환방식 | JS 고정 금리·상품값; 외부 금융 API 없음 | 월 납입액·총이자·금리 1%p 상승 영향·상품표·SVG | 참고용 계산 화면. `market/rate.html`, `rate.js.payment()`·`update()`. 실시간 은행 금리 아님. |
| 학군 `/market/school` | 지역, 학교급, 도보 범위, 면적 | JS `schoolData`·`schoolNames` 고정값 | 학교·학원 수, 교육 점수, 학교 카드·비교표 | 예시 데이터 화면. `market/school.html`, `school.js`. 등록 페이지의 별도 분당구 학군 GeoJSON 자동매칭과 구분. |
| 교통 `/market/traffic` | 지역, 목적지, 출발시간, 교통수단 | JS `trafficData`와 시간 배율; 경로 API 없음 | 추정 소요시간·환승·접근성 점수·경로 모형·표 | 예시 추정 화면. `market/traffic.html`, `traffic.js.getTime()`·`update()`. 실시간 도로/대중교통 경로 아님. |
| 비용 계산 `/market/cost` | 거래 유형·가격·월세·주택 수·면적·대출 예정액 등 | 브라우저의 고정 계산식; DB·외부 API 없음 | 중개보수 상한 추정, 취득 관련 세금 추정, 필요 현금 | 참고용 계산기. `market/cost.html`, `cost.js.calculateBrokerage()`·`calculateAcquisition()`·`calculateBudget()`. 현행 법정 확정 견적 아님. |
| AI 리포트 `/market/ai-report` | 지역, 주거유형, 면적, 기간 | JS `aiMarketData` 고정값; AI 서버 요청 없음 | 점수·신뢰도 표기·전망 SVG·전략 카드 | 예시 리포트 화면. `market/ai-report.html`, `ai-report.js`. 실시간 예측 모델 결과로 소개하면 안 됨. |

시연 추천은 **① `/market/trend`의 데이터 출처 표시, ② `/market/sale`의 두 지역 비교, ③ `/market/cost`의 입력 즉시 계산**이다. 첫 두 화면은 실제 API 응답이 들어오는지 발표 전에 확인해야 한다. `/market/sale`의 선 그래프는 관측 월별 추세가 아닌 합성 표시라고 짧게 설명한다. `/market/cost`는 외부 API 없이 작동해 시연 대체 화면으로 안정적이다.

## 8. 매물 등록

`GET /property/register`는 기본정보 → 상세정보 → 사진 → 확인·등록의 4단계 화면이다. 입력 항목은 매물·거래 유형, 주소·좌표, 전용면적, 층·방·욕실, 가격·보증금·월세, 관리비, 태그·옵션·포함 사용료, 소유자 이름·전화번호, 사진과 소유 관련 서류다. 근거: [`property/register.html`](../../src/main/resources/templates/property/register.html), [`register.js`](../../src/main/resources/static/js/register.js), [`PropertyRegisterRequest.java`](../../src/main/java/com/onrender/zipchatgo/property/PropertyRegisterRequest.java).

- **주소·좌표:** Kakao Maps의 `Geocoder.addressSearch()`와 검색 실패 시 `Places.keywordSearch()`를 사용하고, 선택 좌표를 저장한다. 주변 지하철·버스는 `transit_points_bundang.json`에서 직선거리로 계산한다. 분당구 학군은 `elementary_zones_bundang.json`, `middle_zones_bundang.json`, `high_zones_seongnam.json`의 경계로 자동 매칭한다. 실제 도보 경로나 실시간 배정 정보가 아니다. 근거: `register.js.searchAddress()`·`matchNearbyTransit()`·`matchSchoolZones()`.
- **브라우저 검증·미리보기:** 마지막 단계 완료, 소유자 확인 동의, 위치 좌표 확인 후 제출한다. 사진 선택은 최대 10장, `FileReader` 미리보기와 등록 카드 미리보기가 있다. 근거: `register.js`의 `PHOTO_MAX`, `listingForm` submit handler, `register.html`의 4단계 UI.
- **DB 등록:** `POST /api/properties`가 로그인 세션 `loginMemberId`를 확인한 뒤 `MemberPropertyService.register()`로 `member_property` 및 `property_attribute`를 저장하고 `PENDING`으로 시작한다. 회원 이름과 소유자 이름이 다르면 경고만 반환하며 등록 자체를 막지 않는다. 서버가 모든 폼 필드의 실소유 여부를 검증한다고 주장하면 안 된다. 근거: [`PropertyController.java`](../../src/main/java/com/onrender/zipchatgo/property/PropertyController.java), [`MemberPropertyService.java`](../../src/main/java/com/onrender/zipchatgo/property/MemberPropertyService.java), [`MemberProperty.java`](../../src/main/java/com/onrender/zipchatgo/property/MemberProperty.java).
- **사진·서류:** 등록 JSON 저장 후 `POST /api/properties/{propertyId}/photos`, `/documents`로 파일을 개별 업로드한다. `SupabaseStorageService`가 실제 파일을 저장하고 DB의 `property_photo`, `property_document`는 경로·파일명·종류·정렬 등을 기록한다. 열람용 URL은 제한 시간의 signed URL이다. 근거: [`PropertyPhotoController.java`](../../src/main/java/com/onrender/zipchatgo/property/PropertyPhotoController.java), [`PropertyDocumentController.java`](../../src/main/java/com/onrender/zipchatgo/property/PropertyDocumentController.java), [`SupabaseStorageService.java`](../../src/main/java/com/onrender/zipchatgo/property/SupabaseStorageService.java).
- **수정·삭제·내 매물:** `/my/properties`와 `/my/properties/{id}`에서 본인 매물 목록·상세를 조회한다. `PUT /api/my/properties/{id}`는 수정 후 `PENDING`으로 되돌린다. 매물·사진·서류 개별 삭제 API와 소유권 확인이 있다. 근거: [`MyPropertyController.java`](../../src/main/java/com/onrender/zipchatgo/property/MyPropertyController.java), [`my-properties.js`](../../src/main/resources/static/js/my-properties.js), `MemberPropertyService.update()`·`delete()`.
- **관리자 심사:** `GET /api/admin/properties`, `/pending`, `POST /{id}/approve`, `/reject`, `DELETE /{id}`가 있으며 `AdminInterceptor`가 `memberType=ADMIN`을 확인한다. 근거: [`AdminPropertyController.java`](../../src/main/java/com/onrender/zipchatgo/property/AdminPropertyController.java), [`AdminInterceptor.java`](../../src/main/java/com/onrender/zipchatgo/config/AdminInterceptor.java).

**지도 연결의 한계:** 승인 API는 `member_property.status`만 바꾼다. 지도 조회는 `properties`만 읽으므로 승인 매물이 지도에 자동 노출되는 동기화 경로는 **확인 필요**다. 화면의 “24시간 이내 게시” 문구도 코드에서 보장하는 작업 시간으로 발표하지 않는다.

## 9. 회원가입 / 로그인 / OAuth

| 기능 | 현재 구현과 근거 |
|---|---|
| 회원가입 | `POST /api/auth/signup`. 이메일 형식·중복, 비밀번호 8자 이상, 이름, 선택 전화번호 형식 검사. BCrypt 저장. `MemberService.signup()`, `AuthController.signup()`, `member/auth.html`, `auth.js`. |
| 일반 로그인·로그아웃 | `POST /api/auth/login`에서 BCrypt 비교 후 서버 `HttpSession.loginMemberId` 저장. `POST /api/auth/logout`은 세션 무효화. `AuthController.login()`·`logout()`. |
| 게스트 | `POST /api/auth/guest`로 게스트 세션을 만들고, 프런트는 `localStorage` 플래그도 사용. 게스트는 정회원 DB 매물 등록과 동일하지 않다. `AuthController.guestLogin()`, `auth.js`. |
| 현재 계정 | `GET /api/auth/check`, `/me`; `/account`에서 이름·이메일·가입 방식 표시. `AuthController`, `member/account.html`. |
| OAuth | Google·Naver·Kakao. `/oauth/start/{provider}` → Spring Security OAuth2 authorization → provider 사용자 정보 파싱·회원 연결/생성 → 세션 설정. `OAuthStartController`, `SecurityConfig`, `CustomOAuth2UserService`, `OAuth2LoginHandler`, `application.properties`. 현재 운영 callback 성공 여부는 코드만으로 **확인 필요**. |
| 탈퇴 | `POST /api/auth/withdraw`. 일반 계정은 비밀번호, 소셜 계정은 “탈퇴” 문구 확인. 회원 관련 매물·파일 메타·문의 삭제와 Storage 정리 코드가 있다. `MemberService.withdraw()`, `AuthController.withdraw()`, `member/account.html`. |
| 권한 | `AuthInterceptor`가 지정한 페이지 경로 보호, 각 등록·내 매물 API의 세션 검사, `AdminInterceptor`의 `ADMIN` 검사. Spring Security 설정 자체는 `anyRequest().permitAll()`이므로 단일 전역 인증 게이트라고 설명하면 안 된다. |

회원정보 수정, 비밀번호 찾기·재설정 API는 현재 코드에서 **확인되지 않았다**. `member/auth.html`의 관련 `href="#"`는 완성된 이동 경로가 아니다. 회원 DB 엔터티의 주요 필드는 이메일, BCrypt 비밀번호, 이름·전화번호, `member_type`, OAuth `provider`·`provider_id`다. 근거: [`Member.java`](../../src/main/java/com/onrender/zipchatgo/member/Member.java), [`MemberService.java`](../../src/main/java/com/onrender/zipchatgo/member/MemberService.java), [`AuthController.java`](../../src/main/java/com/onrender/zipchatgo/member/AuthController.java), [`SecurityConfig.java`](../../src/main/java/com/onrender/zipchatgo/config/SecurityConfig.java), [`WebConfig.java`](../../src/main/java/com/onrender/zipchatgo/config/WebConfig.java).

## 10. 고객센터

`/support/contact`는 정적 FAQ를 펼치고, 검색어·카테고리로 화면 내 필터링한다. 공지 예시 목록과 1:1 문의 폼도 있다. `contact.js`는 폼을 `POST /api/inquiries`에 전송한다. `InquiryService.create()`가 개인정보 동의, 이름·이메일·문의 유형·제목·내용 길이를 검증하고 `inquiry`에 `RECEIVED` 상태로 저장한다. 근거: [`support/contact.html`](../../src/main/resources/templates/support/contact.html), [`contact.js`](../../src/main/resources/static/js/contact.js), [`InquiryApiController.java`](../../src/main/java/com/onrender/zipchatgo/inquiry/InquiryApiController.java), [`InquiryService.java`](../../src/main/java/com/onrender/zipchatgo/inquiry/InquiryService.java).

FAQ와 공지는 DB 조회형 게시판이 아니라 템플릿 내용이다. 공지 “전체 보기” 등 일부 링크는 `#`이다. 문의 조회·수정·삭제, 관리자 답변·처리 게시판 API는 **미구현/확인되지 않음**이다. `/support/guide`는 정적 이용 안내다. `/support/live`가 쓰는 `GET /api/live/recommendations`의 세 추천 매물은 `LiveApiController`에 고정된 예시값이므로 실제 추천 엔진이나 매물 DB 결과로 소개하면 안 된다. 근거: [`support/guide.html`](../../src/main/resources/templates/support/guide.html), [`LiveApiController.java`](../../src/main/java/com/onrender/zipchatgo/support/LiveApiController.java).

## 11. 전체 시스템 Architecture

```text
브라우저: Thymeleaf HTML + CSS + JavaScript
  ├─ Naver Maps SDK                  지도 탐색·지오코딩
  ├─ Kakao Maps SDK                  매물 등록 주소·위치 확인
  └─ Spring Boot :8080
      ├─ JdbcTemplate → properties, poi         지도·매물·POI
      ├─ Spring Data JDBC → member,
      │    member_property, inquiry 등         회원·등록·문의
      ├─ 국토교통부 실거래가 API                 시장 요약
      ├─ Supabase Storage                     등록 사진·서류
      ├─ OAuth: Google·Naver·Kakao            소셜 로그인
      └─ FastAPI :8000 /agent/chat
          ├─ Spring 지도 API                    매물·POI·역 조회
          ├─ 분당구 법정동 정적 데이터           인접 동 조회
          ├─ OpenAI Responses API              대화·Tool Calling
          └─ OpenAI Vector Store               법률 조문 검색
```

Spring Boot는 사용자·매물 등록·지도 REST API의 데이터·비즈니스 로직을 담당한다. FastAPI는 AI 전용으로 Spring의 지도 API를 호출해 매물·POI·역을 조회한다. POI·역·지도 검색을 FastAPI가 DB에 직접 SQL로 조회한다고 설명하면 부정확하다. 시장 분석 API는 Spring에서 국토교통부 API를 직접 호출한다. 정적 분석 예시 페이지는 별도 Backend 계산을 거치지 않는다. 근거: [`main.py.agent_chat()`](../../ai-server/app/main.py), [`PropertySearchTool`](../../ai-server/app/tools/property_search.py), [`PoiSearchTool`](../../ai-server/app/tools/poi_search.py), [`MapDataService.java`](../../src/main/java/com/onrender/zipchatgo/map/MapDataService.java), [`MarketService.java`](../../src/main/java/com/onrender/zipchatgo/market/MarketService.java).

로컬·Compose 기준 일반 요청은 `Frontend → Spring :8080`, AI 요청은 `Frontend → Spring :8080 → FastAPI :8000 → OpenAI`다. Spring이 8000번 포트로 바뀌는 구조가 아니다. Compose에서는 Spring만 호스트 `8080:8080`으로 노출하고 FastAPI는 내부 `8000`으로 연결한다. 근거: [`docker-compose.yml`](../../docker-compose.yml), [`application.properties`](../../src/main/resources/application.properties).

외부 시스템 역할 요약: Naver Maps는 탐색 지도, Kakao Maps는 등록 주소 확인, 국토교통부 API는 시장 요약, 국가법령정보센터 API는 법률 corpus 수집, OpenAI API는 Agent 생성, OpenAI Vector Store는 법률 검색, OAuth 세 제공자는 로그인, Supabase Storage는 사진·서류 파일 저장이다. DB 접속은 MySQL JDBC 형식이지만 현재 실제 DB 제품·호스팅 위치는 **확인 필요**다.

## 12. DB / 데이터 구조

| 테이블·저장 위치 | 역할·주요 필드 | 사용하는 기능·코드 |
|---|---|---|
| `properties` | 지도·거래 성격의 매물. `id`, `title`, `building_name`, `property_type`, `sale_price`, `deposit`, `monthly_rent`, `exclusive_area`, `address`, `district`, `latitude`, `longitude`, `contract_date` 등 | 지도와 AI 매물 검색·가격 이력. `MapDataService.PROPERTIES_SQL_TEMPLATE`, `PRICE_HISTORY_SQL`. 지도용 최근 12개월, 거래 모드 최대 36개월 조건. |
| `poi` | 시설 ID·출처 유형·이름·대분류·소분류·주소·좌표·버스 노선 등 | 지도 POI와 AI POI 검색. `MapDataService.POIS_SQL`, `POIS_BY_CATEGORY_SQL`. |
| `member` | 이메일, BCrypt 비밀번호, 이름·전화번호, `member_type`, 중개사 정보, OAuth `provider`·`provider_id` | 회원가입·로그인·관리자 판단. [`Member.java`](../../src/main/java/com/onrender/zipchatgo/member/Member.java), `MemberRepository`. |
| `member_property` | 회원 ID, 매물·거래 유형, 주소·좌표, 면적·층·가격·보증금·월세, 소유자 연락처, `status`, 생성 시각 | 등록·내 매물·관리자 심사. [`MemberProperty.java`](../../src/main/java/com/onrender/zipchatgo/property/MemberProperty.java), `MemberPropertyRepository`. |
| `property_attribute` | 등록 매물 ID, 태그·옵션·사용료 구분과 값 | 등록 상세 속성. [`PropertyAttribute.java`](../../src/main/java/com/onrender/zipchatgo/property/PropertyAttribute.java). |
| `property_photo` | 등록 매물 ID, Storage 파일 경로·원본 이름·정렬 순서·업로드 시각 | 사진 업로드·목록·임시 열람 URL. [`PropertyPhoto.java`](../../src/main/java/com/onrender/zipchatgo/property/PropertyPhoto.java). |
| `property_document` | 등록 매물 ID, 서류 종류·Storage 경로·원본 이름·업로드 시각 | 소유 증명 서류 업로드·관리자 확인. [`PropertyDocument.java`](../../src/main/java/com/onrender/zipchatgo/property/PropertyDocument.java). |
| `inquiry` | 이름·이메일·문의 유형·제목·내용·상태·접수 시각, 선택적 `member_id` | 고객센터 문의 저장. [`Inquiry.java`](../../src/main/java/com/onrender/zipchatgo/inquiry/Inquiry.java). |
| 브라우저 `sessionStorage` | `zipchatgo.favoritePropertyIds`, 지도 viewport 등 | 관심매물 및 지도 세션 상태. **관심매물 DB 테이블이 아니다.** [`favorite.js`](../../src/main/resources/static/js/favorite.js), [`map.js`](../../src/main/resources/static/js/map.js). |
| 브라우저 메모리 | AI 대화 history·recentContext | 현재 페이지의 대화 연속성. `ai-chat.js`. 새로고침 후 복원 DB는 확인되지 않음. |
| 정적 JSON·GeoJSON | 매물·POI 대체 자료, 분당구 역·법정동·학군 경계 등 | DB 조회 오류 fallback, 역명·지역 경계·등록 자동 정보. `src/main/resources/static/data/`. |

`spring.datasource.url`은 MySQL JDBC 형식이며 MySQL 드라이버가 빌드 의존성에 있다. TiDB처럼 MySQL 호환 제품도 연결 가능하겠지만, **현재 운영 DB가 실제로 TiDB인지**는 환경·연결 상태 확인이 필요하다. 데이터 적재 수량·최근 갱신일·스키마 migration 실행 상태도 저장소만으로는 확인할 수 없다. 근거: [`application.properties`](../../src/main/resources/application.properties), [`build.gradle`](../../build.gradle).

**중요한 경계:** `member_property`는 회원이 제출한 매물, `properties`는 현재 지도 API가 읽는 매물이다. 등록 완료·관리자 승인만으로 지도 검색에 노출된다고 현재 코드가 입증하지 않는다. 시장 분석에는 별도 DB 테이블이 확인되지 않는다. 법률 자료는 서비스 DB의 `law` 테이블이 아니라 조문 문서와 OpenAI Vector Store로 처리된다.

## 13. 외부 API / 공공데이터

| API·데이터와 제공 주체 | 사용 기능·호출 위치 | 저장 방식·실시간성 | 확인 수준 |
|---|---|---|---|
| **Naver Maps JavaScript SDK** | 지도 렌더링·이동·지오코딩. `property/map.html`의 SDK 스크립트와 `map.js` | 브라우저에서 로드·호출; 지도 UI 상태는 브라우저가 관리 | 코드 구현 확인. 현재 서비스 키·콘솔 허용 URL의 실시간 유효성은 **확인 필요** |
| **Kakao Maps SDK** | 등록 주소 `Geocoder.addressSearch`, `Places.keywordSearch`, 지도 미리보기. `property/register.html`, `register.js` | 브라우저 직접 조회. 확인된 좌표와 일부 주변 정보 스냅샷은 `member_property`에 저장 | 코드 구현 확인. 현재 외부 콘솔 상태는 **확인 필요** |
| **국토교통부 아파트 매매 실거래가 API** `RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade` | Spring `MarketService.call()`이 법정동 앞 5자리·거래월로 조회 | `/api/market/summary` 요청 시 현재월·전월 호출·집계. 분석 DB 저장 코드 없음 | 엔드포인트와 호출 코드 확인. 실제 운영 키·응답은 **확인 필요** |
| **국토교통부 아파트 전월세 실거래가 API** `RTMSDataSvcAptRent/getRTMSDataSvcAptRent` | 동일 `MarketService.call()` | 요청 시 호출·보증금 평균 계산. 순수 전세만의 데이터라고 단정 불가 | 엔드포인트와 호출 코드 확인 |
| **국가법령정보센터 공동활용 Open API** | `ai-server/app/law/national_law_api.py`의 법령 corpus 수집, `target=eflaw` | 사전 수집하여 조문별 Markdown과 metadata 생성 후 Vector Store 업로드. 일반 채팅 때 직접 호출하지 않음 | 구축 코드 확인. 현재 Store에 업로드된 최신 목록은 **확인 필요** |
| **OpenAI Responses API** | `OpenAIProvider.generate()`가 대화·Tool Calling 수행 | 요청 시 호출, `store=False`. 대화 원문을 서버 DB에 저장하는 코드 없음 | 코드 확인. 현재 사용 모델·키는 환경 확인 필요 |
| **OpenAI Vector Store 검색 API** | `OpenAIVectorStoreLawRetriever.search()`·`search_exact()` | 요청 시 저장된 조문 검색; Store ID는 환경변수 | 코드 확인. 현재 운영 Store ID는 **확인 필요** |
| **Google·Naver·Kakao OAuth** | Spring Security OAuth2 설정 및 `CustomOAuth2UserService` | 로그인 시 제공자 인증·회원 정보 연결, DB `member`에 provider·ID 저장 | 구현 확인. 현재 callback 실환경 상태는 **확인 필요** |
| **Supabase Storage** | `SupabaseStorageService.upload()`·`createSignedUrl()`·`deleteObjects()` | 등록 사진·서류 파일 저장. 경로 등은 DB 테이블에 보관 | 코드 확인. 현재 bucket·계정 상태는 **확인 필요** |
| **저장소 정적 파일** | `properties.json`, `poi_database.json`, `transit_points_bundang.json`, `bundang_legal_dong.geojson`, 등록 학군 JSON | 애플리케이션 파일에 포함된 자료. 지도 DB 장애 fallback·역 조회·지역 경계·등록 자동 매칭에 사용 | 파일과 호출 코드 확인. 각 원자료의 정확한 공식 데이터셋·수집 시점은 **확인 필요** |

`MOLIT_MAP_SERVICE_KEY`라는 설정 이름은 `application.properties`와 Compose에 존재하지만 현재 `map` Java 패키지에서 그 키로 국토교통부 API를 직접 호출하는 경로는 확인되지 않았다. 지도 원자료가 국토교통부의 어떤 공식 데이터셋에서 언제 적재되었는지는 추측하지 않는다. 근거: [`application.properties`](../../src/main/resources/application.properties), [`docker-compose.yml`](../../docker-compose.yml), [`MapDataService.java`](../../src/main/java/com/onrender/zipchatgo/map/MapDataService.java).

## 14. 테스트 / 검증 결과

평가 보고서는 **기록된 실행 시점과 commit의 결과**다. 현재 HEAD `1eddec69...`의 통과 결과로 자동 승격하지 않는다. 이 문서 작업 중 Python·Spring·JavaScript·2024/2025 시험을 실행하지 않았다.

| 항목 | 저장소에서 확인한 내용 | 해석·범위 |
|---|---|---|
| Python 테스트 | `ai-server/tests/`에 테스트 파일 36개 존재 | 현재 HEAD의 pytest 재실행·통과 개수는 **확인 필요** |
| Spring 테스트 | `src/test/`에 `AiControllerTests`, `MapDataServiceTests`, 애플리케이션 테스트 등 3개 파일 | 현재 HEAD의 Gradle 테스트 결과는 **확인 필요** |
| JavaScript syntax | 현재 조사에서 `node --check`를 실행하지 않음 | 현재 HEAD의 문법 검사 결과는 **확인 필요** |
| Agent 통합 회귀 30문항 | `production_agent_integrated_regression_30_live_report.md`: 2026-09-16, commit `c8577f...`, 30/30 정상 완료, 실행 오류 0, Tool-loop 오류 0, Action schema 오류 0, 복합 질문 11/11, 평균 Tool 호출 2.10, 평균 지연 9.07초 | 당시 로컬 Spring과 production Provider 경로 평가. 실제 브라우저 지도 움직임은 범위 밖. 당시 POI·관심매물 write 범위도 현재와 다를 수 있으므로 현재 registry의 전 기능 검증으로 설명하면 안 됨 |
| Multi-turn smoke 6턴 | `production_agent_multiturn_smoke_6.csv`의 6행 모두 `Error`가 비어 있음. 저장 행의 모델은 `gpt-4o-mini` | 당시 6턴 기록. 현재 HEAD·현재 운영 모델의 성능 수치가 아님 |
| 법률 일반 사용자형 20문항 | `production_agent_law_store_v2_generalization_regression_20_report.md`: 법률 질문 18/18에서 법률 Tool 호출, 비법률 질문 0/2에서 법률 Tool 오호출. Tool-loop error 1건, 로컬 Spring 연결 오류 1건도 기록 | 라우팅 지표와 전체 답변 정오를 혼동하면 안 됨. 20/20 성공이라는 주장은 부정확 |
| 2024년 40문항 과거 비교 | `production_agent_passthrough_2024_40_gpt-4o-mini_vs_gpt-5.6-sol_report.md` 등의 보고서 존재 | 과거 모델·설정 비교 기록. 현재 운영 Agent 품질 또는 서비스 목표 수치로 사용하지 않음 |
| **2025 hold-out 40문항** | 현재 저장소에서 완료 결과와 `correct`, `wrong`, `unjudgeable`, `execution error`, 전체·조건부 정확도 표를 확인하지 못함. checkpoint 문서는 평가를 향후 동결 후 한 번 수행할 계획으로 설명 | **결과·모델·Vector Store·시험 조건 모두 확인 필요. 수치 발표 금지.** |

근거: [`production_agent_integrated_regression_30_live_report.md`](../evaluation/production_agent_integrated_regression_30_live_report.md), [`production_agent_multiturn_smoke_6.csv`](../evaluation/production_agent_multiturn_smoke_6.csv), [`production_agent_law_store_v2_generalization_regression_20_report.md`](../evaluation/production_agent_law_store_v2_generalization_regression_20_report.md), [`production_agent_law_rag_generalization_checkpoint.md`](../evaluation/production_agent_law_rag_generalization_checkpoint.md).

2025 hold-out을 발표에서 언급한다면 **“개발에 직접 사용하지 않은 문제로 일반화 성능을 확인하기 위한 평가 계획”**이라는 의미로만 사용할 수 있다. 실제 40문항 결과가 별도로 확인되기 전에는 점수·정답 개수·모델 이름을 쓰지 않는다.

## 15. 배포 구조

| 구분 | 저장소에서 확인되는 설정 | 현재 운영 여부 |
|---|---|---|
| Spring Docker 이미지 | 루트 `Dockerfile`: Java 21로 Gradle `bootJar`를 빌드하고 JRE 21의 비특권 사용자로 `8080` 실행 | 이미지 정의 존재. 현재 실행 상태는 **확인 필요** |
| FastAPI Docker 이미지 | `ai-server/Dockerfile`: AI 전용 `8000` 이미지 | 이미지 정의 존재. 현재 실행 상태는 **확인 필요** |
| Compose | `docker-compose.yml`: `spring`·`ai-server` 두 서비스. Spring `8080:8080` 호스트 공개, AI `8000` 내부 expose, Spring의 `AI_SERVER_URL=http://ai-server:8000` | 구성 존재. 현재 EC2 컨테이너 상태는 **확인 필요** |
| Render | `render.yaml`: 싱가포르의 Spring·AI 두 Docker web service, `/health`, 필요한 환경변수 | 배포 설정 존재. 현재 Render 사용 여부는 **확인 필요** |
| EC2 CI/CD | `.github/workflows/deploy-ec2.yml`: `integrate-JongBeom-develop` push 또는 수동 실행, EC2 self-hosted runner에서 fetch·fast-forward pull·Compose build/up | workflow 존재. 마지막 실행 성공·실제 동작은 **확인 필요** |
| 80/443·운영 URL | 현재 `docker-compose.yml` 자체에는 80/443 reverse proxy가 없고 `8080` 포트가 공개됨. HTTPS·DuckDNS·프록시는 EC2 외부 설정일 수 있음 | 현재 도메인·TLS·서버 상태는 저장소만으로 **확인 필요** |

환경변수 범주는 `DB_HOST`·`DB_PORT`·`DB_DATABASE`·DB 계정, `OPENAI_API_KEY`·`OPENAI_MODEL`·`LAW_VECTOR_STORE_ID`, `INTERNAL_API_KEY`, `AI_SERVER_URL`·`SPRING_SERVER_BASE_URL`, `MOLIT_SERVICE_KEY`, `NAVER_MAPS_CLIENT_KEY`, OAuth 세 제공자 자격증명, Supabase URL·키·bucket 등이다. **환경변수 이름만 설명하며 값은 문서에 넣지 않는다.** 근거: [`docker-compose.yml`](../../docker-compose.yml), [`render.yaml`](../../render.yaml), [`application.properties`](../../src/main/resources/application.properties), [`ai-server/app/config.py`](../../ai-server/app/config.py).

저장소가 증명하는 것은 배포 **설정의 존재**다. 과거 평가 때 로컬 Spring을 사용한 기록은 **과거 테스트**다. 현재 실제 운영 중인지, 어떤 이미지·commit·환경변수가 실행 중인지는 **운영 상태 확인**에 해당한다. 세 수준을 혼동하지 않는다.

## 16. 현재 제한사항

1. **시장 데이터 출처가 상황별로 다르다.** `/api/market/summary`는 키가 없으면 서버 demo를, 분석 허브는 API 실패 시 프런트 auto-demo를 표시할 수 있다. 시연 때 `source`를 반드시 확인한다. 근거: `MarketService.summary()`, `market-api.js.renderFallbackSummary()`.
2. **합성 차트·예시 분석 화면이 있다.** 매매·전세·거래량·지역 비교의 기간별 그래프는 실제 월별 원자료가 아니고, 지역 관심 이동·학군·교통·금리·AI 리포트는 고정 예시 자료를 바탕으로 한다. 근거: `sale.js.makeSeries()`, `volume.js.makeSeries()`, `region.js.makeIndexSeries()`, `region-flow.js.flowData`, `school.js.schoolData`, `traffic.js.trafficData`, `rate.js.products`, `ai-report.js.aiMarketData`.
3. **전세 거래량 데이터 필드 불일치가 있다.** `volume.js`는 `rentCount`·`rentVolumeChangeRate`를 쓰지만 실API `MarketService.buildSummary()`는 해당 필드를 생성하지 않는다. 전세 거래량 시연은 피하거나 실제 응답을 확인해야 한다.
4. **관심매물은 계정별 DB 보관이 아니다.** 같은 브라우저 세션의 `sessionStorage` ID에 의존하므로 장기 보존·다중 기기 동기화로 소개하면 안 된다. 근거: `favorite.js`의 `FAVORITE_PROPERTY_STORAGE_KEY`.
5. **등록 매물의 지도 자동 노출 경로는 확인되지 않는다.** 관리자 승인은 `member_property.status`를 바꾸지만 지도는 `properties`를 조회한다. 근거: `AdminPropertyController.updateStatus()`, `MapDataService.PROPERTIES_SQL_TEMPLATE`.
6. **Agent Tool 지원 범위는 한정되어 있다.** 지도 UI의 면적·준공연수 필터가 Agent의 `search_properties` 인자로 모두 제공되는 것은 아니다. 교통역 Tool은 명시된 ‘역’ 이름을 요구한다. 분당구 법정동 경계·인접 동은 정적 자료 범위이며, POI는 현재 DB·fallback 자료에 있는 세부 분류만 반환한다. 근거: `openai_provider.py`의 Tool schema·`UI_ACTION_INSTRUCTIONS`, `schemas.py.PropertyFilters`, `MapDataController`.
7. **법률 RAG는 완전한 법률 사실 보증이 아니다.** 조문 인용과 링크를 제한하지만 모든 주장·판례의 진위를 확인하는 hard gate는 아니다. 현재 Store ID와 최신 법령 수집일은 **확인 필요**다. 근거: `_ground_law_response()`, `main.py.get_law_retriever()`.
8. **회원·고객센터의 일부 기능이 없다.** 회원정보 수정·비밀번호 재설정, 문의 조회·답변·관리자 게시판, 동적 공지 관리 API는 확인되지 않는다. 근거: `AuthController`, `InquiryApiController`, `support/contact.html`.
9. **현재 실행 상태는 코드만으로 알 수 없다.** OAuth callback, 국토교통부 API 키, 지도 SDK 도메인 허용, Supabase, EC2 HTTPS, DB 원자료 갱신은 현장 점검이 필요하다. 이 문서에서는 구현과 실환경 검증을 분리한다.

## 17. PPT에 넣기 좋은 핵심 기능

| 우선순위 | 기능 | 발표 가치 | 시각적으로 보여줄 화면 | 이유 |
|---:|---|---|---|---|
| 1 | 자연어 매물 검색 → 지도 Action | 매우 높음 | `/property/map`의 AI 채팅·강조 마커 | 서비스의 대화형 통합 인터페이스를 한 장면에 표현 |
| 2 | 현재 지도·최근 매물을 이용한 후속 질문 | 매우 높음 | 선택 매물·후속 대화 | 단발성 질문 응답과 구별되는 실제 구현 |
| 3 | 지도 매물 군집·필터·상세 | 높음 | 지도 전체·상세 패널 | 부동산 탐색 서비스의 기반 |
| 4 | 법률 조문 검색·공식 출처 | 높음 | AI 답변·출처 링크 | Tool + RAG 구조 설명에 적합 |
| 5 | 매매 실거래 요약·지역 비교 | 높음 | `/market/sale` | 데이터 기반 분석 영역 대표. 차트의 합성 여부 명시 |
| 6 | 분석 허브·데이터 출처 | 높음 | `/market/trend` | 네 영역을 연결하는 분석 진입점 |
| 7 | 관심매물과 AI 문맥 | 중상 | 지도 하트·`/favorites` | 탐색 이후 저장·후속 질문 흐름 |
| 8 | 매물 등록 4단계·미리보기 | 중상 | `/property/register` | 공급자 기능과 시각적 완성도 |
| 9 | 일반·소셜 로그인 | 중간 | `/member/login` | 회원 기능과 등록의 선행 조건 |
| 10 | 내 매물 관리·승인 상태 | 중간 | `/my/properties`, `/admin/properties` | 등록 이후 심사 흐름. 지도 노출 자동화는 주장하지 않음 |
| 11 | 비용 계산기 | 중간 | `/market/cost` | 입력 결과가 즉시 보여 안정적인 시연 대안 |
| 12 | 고객센터 FAQ·문의 | 중간 | `/support/contact` | 서비스 운영 지원 기능 |

기능 근거의 시작점: [`property/map.html`](../../src/main/resources/templates/property/map.html), [`ai-chat.js`](../../src/main/resources/static/js/ai-chat.js), [`PageController.java`](../../src/main/java/com/onrender/zipchatgo/controller/PageController.java), [`property/register.html`](../../src/main/resources/templates/property/register.html), [`support/contact.html`](../../src/main/resources/templates/support/contact.html).

## 18. Live Demo 추천 시나리오

**총 약 5분 40초.** 로그인된 시연 계정, 결과가 있는 지도 지역, 시장 API `source`를 발표 전에 확인한다. 신규 매물 등록은 실제 DB 변경이므로 제출 버튼 대신 화면 단계와 미리보기까지만 보여주는 구성을 추천한다. 준비용 캡처는 정상 화면의 백업 자료로 사용한다.

| 시간 | 사용자가 클릭·입력할 내용 | 예상 화면 변화·발표 포인트 | 실패 위험·대체 화면 |
|---|---|---|---|
| 0:00–0:30 | 홈 `/`의 AI 입력창과 네 기능 카드 소개 | 탐색·분석·등록·지원이 연결된 서비스 진입점 | 홈 이미지 로딩 실패 → 카드 텍스트·사전 캡처 |
| 0:30–1:05 | `/property/map` 진입, 지도 확대·기존 마커 클릭 | 군집이 개별 마커로 바뀌고 상세 패널 표시 | Naver SDK·DB 지연 → 지도 캡처. DB fallback 여부는 별도 확인 |
| 1:05–2:15 | AI에 “판교역 8억 이하 아파트 보여줘” 입력 | 역 조회·매물 검색 후 지도 이동·마커 강조·설명. AI가 Tool 결과로 UI Action을 연결함 | 결과 0건 또는 외부 API 지연 → 사전 결과가 확인된 지역·가격 문구 사용, 성공 흐름 캡처 |
| 2:15–3:00 | 후보가 있으면 “그중 첫 번째 매물 알려줘” 입력, 하트 클릭 | 최근 표시 순서의 후속 문맥과 `sessionStorage` 관심매물 설명 | 0건이면 기존 마커 선택 후 “선택한 매물 알려줘”로 변경 |
| 3:00–3:40 | 임대차 법률의 일반적인 확인 질문 입력 | 검색 결과가 있으면 조문·공식 링크, 없으면 근거 부족 안내. 검색 기반 답변 정책 설명 | Vector Store·모델 지연 → 저장한 답변 캡처. 법률 정답 보장 시연으로 표현 금지 |
| 3:40–4:40 | `/market/trend`에서 `source` 확인 후 `/market/sale` 두 지역 비교 | 평균·가격 차이·거래 예시와 합성 차트 구분 | 실거래 API 오류 또는 demo → `/market/cost` 입력 계산으로 대체 |
| 4:40–5:40 | `/property/register`의 4단계·미리보기, `/support/contact` FAQ·문의 폼 간략 소개 | 사용자가 매물을 올리고 문의하는 서비스 흐름을 마무리 | Kakao SDK 지연 → 폼·미리보기 정적 부분과 고객센터 FAQ 표시 |

시연 중 페이지 이동을 줄이려면 계정 로그인은 시작 전에 완료해 둔다. 매물 검색 결과나 운영 API 상태가 달라질 수 있으므로 특정 단지 이름·가격·검색 건수를 대본에 고정하지 않는다. 등록 제출·회원 탈퇴·관리자 승인처럼 DB를 바꾸는 동작은 이 4–6분 데모 후보에서 제외한다.

## 19. 발표자료 제작용 최종 요약

### A. 프로젝트 한 문장 소개

집찾GO는 지도 매물 탐색, 현재 화면을 이해하는 AI Agent, 부동산 분석, 회원 매물 등록과 고객 지원을 하나로 묶은 부동산 웹서비스다.

### B. 해결하려는 문제

1. 매물·주변 시설·거래 정보가 여러 화면에 흩어져 탐색하기 어렵다.
2. 사용자가 지도 이동과 검색 조건 설정을 반복해야 한다.
3. 선택한 지역·매물에 대한 후속 질문의 문맥이 필요하다.
4. 매물 가격 외에도 지역 시장·비용·법률 정보를 함께 확인하고 싶다.

### C. 핵심 기능 4영역

1. **서비스 소개:** 홈의 AI 입력창과 서비스 소개·지도·분석·등록 카드가 전체 사용자 흐름의 출발점이다. `PageController`, `index.html`, `main.js`.
2. **지도 탐색 + AI Agent:** Naver 지도, 매물 군집·필터·상세, POI·역, App State·최근 문맥을 사용하는 16개 등록 도구와 검증된 UI Action이 핵심이다. `MapDataController`, `MapDataService`, `map.js`, `ai-chat.js`, FastAPI `OpenAIProvider`.
3. **부동산 분석:** 국토교통부 API를 사용하는 매매·전월세·거래량 요약과 지역 비교, 브라우저 비용 계산기가 있다. 예시 데이터 화면과 합성 차트는 실측 분석과 구분한다. `MarketApiController`, `MarketService`, `market-api.js`, `sale.js`, `cost.js` 등.
4. **등록 + 회원 + 고객센터:** 4단계 등록, Kakao 주소 확인, Supabase 파일 저장, 내 매물·관리자 승인, 일반·소셜 로그인, FAQ·1:1 문의가 있다. `PropertyController`, `MemberPropertyService`, `AuthController`, `InquiryApiController` 등.

### D. 핵심 기술

| 층 | 기술·역할 |
|---|---|
| Frontend | Thymeleaf HTML, CSS, JavaScript, Naver/Kakao Maps SDK, Supercluster |
| Backend | Spring Boot REST, Spring Data JDBC, JdbcTemplate, HttpSession, Spring Security OAuth2 |
| AI | FastAPI, Pydantic, OpenAI Responses Tool Calling, OpenAI Vector Store 법률 RAG |
| DB·Storage | MySQL 호환 JDBC DB, Supabase Storage, 브라우저 `sessionStorage` |
| 외부 API | 국토교통부 실거래가, Google·Naver·Kakao OAuth, 법령 corpus 수집용 국가법령정보센터 API |

### E. 차별점

1. Agent가 현재 지도 중심·범위·선택 지역·선택 매물·관심매물 ID를 요청마다 받는다.
2. 매물 조회 결과를 지도 이동·강조·상세 열기라는 허용된 UI Action으로 연결한다.
3. 최근 제시한 매물 순서와 단일 참조 ID를 보존해 “그중 두 번째”, “그 매물” 같은 후속 질문을 다룬다.
4. 법률 질문은 조문 검색 결과와 공식 출처를 연결하고, 근거 부족 시 이를 밝히도록 설계했다.
5. 지도 탐색 후 분석·등록·문의까지 같은 서비스의 페이지 흐름으로 이어진다.

### F. 검증 결과

저장된 **과거 평가** 기준으로 Agent 통합 30문항 중 30건 완료, 실행 오류 0건, Action schema 오류 0건이 기록됐다. Multi-turn CSV 6턴의 `Error` 기록은 0건이다. 법률 일반 사용자형 20문항 평가에서 법률 질문의 도구 호출은 18/18이었으나 실행·검증 한계도 기록되어 있다. **현재 HEAD의 재실행 결과와 2025 hold-out 40문항 결과는 확인 필요**다. 발표에서 시험 점수를 서비스의 목적처럼 설명하지 않는다.

### G. 시연 추천 기능

홈 → 지도 군집·매물 상세 → AI 매물 검색과 후속 질문 → 관심매물 → 법률 검색 → 매매 지역 비교 → 등록 미리보기·고객센터. 외부 API가 지연되면 비용 계산기와 준비한 캡처를 사용한다.

### H. 현재 제한사항

- 관심매물은 브라우저 세션 저장이며 계정별 DB 동기화가 아니다.
- 등록 매물의 관리자 승인과 지도 `properties` 데이터의 자동 연결은 **확인 필요**다.
- 분석 화면 일부는 demo값·합성 그래프·정적 예시 데이터다. 특히 AI 리포트를 운영 예측 모델의 실시간 결과로 소개하면 안 된다.
- Agent Tool과 POI·역·법정동 자료에는 명시된 지원 범위가 있다.
- 법률 RAG는 공식 조문 검색과 인용 검사를 제공하지만 모든 법적 주장에 대한 완전한 보증은 아니다.
- OAuth·지도·국토교통부 API·Vector Store·DB·EC2 HTTPS의 **현재 운영 상태**와 2025 hold-out 결과는 코드 읽기만으로 확정할 수 없다.
