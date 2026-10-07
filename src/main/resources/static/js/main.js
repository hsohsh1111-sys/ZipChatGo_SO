/* 대화 시작 문장 빠른 입력 */
const PENDING_AI_QUESTION_KEY = "zipchatgo.pendingAiQuestion";
const mainAiInput = document.getElementById("mainAiQuestion");
const mainAiStart = document.getElementById("mainAiStart");

document.querySelectorAll(".quick-tags button").forEach(tag => {
  tag.addEventListener("click", () => {
    if (mainAiInput) {
      mainAiInput.value = tag.innerText;
      mainAiInput.focus();
    }
  });
});

function startAiConversation() {
  const question = mainAiInput?.value.trim() || "";
  if (!question) {
    mainAiInput?.focus();
    return;
  }

  sessionStorage.setItem(PENDING_AI_QUESTION_KEY, question);
  window.location.href = "/property/map";
}

mainAiStart?.addEventListener("click", startAiConversation);
mainAiInput?.addEventListener("keydown", event => {
  if (event.key === "Enter" && !event.isComposing) {
    event.preventDefault();
    startAiConversation();
  }
});

// 참고: 예전에는 여기서 로그인 여부를 localStorage로 체크해서
// 로그인 필요한 카드(feature-link) 클릭 시 강제로 로그인 페이지로 보냈지만,
// 지금은 서버 쪽 AuthInterceptor가 보호된 경로 접근을 알아서 처리하므로
// 이 파일에서 별도로 막을 필요가 없어졌어요. index.html의 th:href 기본 이동을 그대로 따릅니다.
