# 집찾GO 프로젝트 플로 차트

이 플로 차트는 현재 구현을 기준으로 일반 웹 요청과 AI 대화 요청을 분리해 보여줍니다. 점선 경로는 개발 계획에 포함되어 있지만 현재 구현이 확인되지 않은 기능입니다.

## 서비스 전체 흐름

```mermaid
flowchart TD
    U[사용자] --> WEB[Spring Boot 웹 화면]
    WEB --> MAP[지도에서 매물 탐색]
    WEB --> MARKET[시장 동향 및 분석]
    WEB --> MEMBER[회원·관심매물·매물 등록]
    WEB --> SUPPORT[서비스·고객지원]
    MAP --> API[Spring REST API]
    API --> DATA[서비스 데이터 및 저장소]
    MAP --> CHAT[지도 화면 AI 채팅]
    CHAT --> SPRINGAI[Spring POST /api/ai/chat]
    SPRINGAI --> FAST[FastAPI POST /agent/chat]
    FAST --> LLM[OpenAI 모델]
    FAST --> TOOLS[제한된 AI 도구]
    TOOLS --> SPRINGAPI[Spring 지도·매물 API]
    SPRINGAPI --> DATA
    FAST --> RESULT[답변 + 검증된 UI Action]
    RESULT --> SPRINGAI
    SPRINGAI --> CHAT
    CHAT --> EXEC[Frontend 허용 Action 실행]
    EXEC --> MAP
    FAST -. 법률 질문 .-> RAG[법률 검색 / Vector Store]
```

## AI 매물 탐색 시나리오

```mermaid
sequenceDiagram
    actor User as 사용자
    participant UI as 지도 화면·ai-chat.js
    participant Spring as Spring Boot
    participant AI as FastAPI Agent
    participant LLM as OpenAI
    participant Data as Spring 지도 API·데이터

    User->>UI: 자연어로 매물 조건 질문
    UI->>Spring: POST /api/ai/chat (질문, App State, 대화 문맥)
    Spring->>AI: POST /agent/chat
    AI->>LLM: 질문과 구조화된 상태 전달
    LLM->>AI: 도구 선택 또는 답변 생성
    opt 매물·POI·역 검색 필요
        AI->>Spring: 내부 도구 HTTP 요청
        Spring->>Data: 조회
        Data-->>Spring: 검색 결과
        Spring-->>AI: 도구 결과
        AI->>LLM: 도구 결과 전달 및 응답 생성
    end
    AI-->>Spring: message, actions, recent_context
    Spring-->>UI: AI 응답
    UI->>UI: 허용 목록과 스키마에 맞는 Action만 실행
    UI-->>User: 답변 표시 및 지도 갱신
```

## 사용자 여정

```text
홈 → 지도 열기 → 위치·조건 검색 → 매물/주변 POI 확인 → 상세 확인
                                         ├→ 관심매물 저장
                                         ├→ AI에게 질문 → 지도 Action 반영
                                         └→ 시장 동향·분석 화면으로 이동
```

관심매물 데이터 영속성, 분석 페이지를 조작하는 AI Tool, 페이지 간 대화 문맥 공유 등은 별도 구현 확인이 필요한 범위입니다. 이를 현재 완성된 흐름으로 간주하지 않습니다.

