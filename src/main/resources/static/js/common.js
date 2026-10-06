/* ==========================
   common.js
   공통 헤더 / 모바일 메뉴 / 로그인 상태 처리
========================== */

document.addEventListener("DOMContentLoaded", () => {
  initHeaderScroll();
  initMobileMenu();
  updateLoginMenu();
});

/* ==========================
   스크롤 헤더 효과
========================== */

function initHeaderScroll() {
  const header = document.getElementById("mainHeader");

  if (!header) return;

  window.addEventListener("scroll", () => {
    header.classList.toggle("scrolled", window.scrollY > 20);
  });
}

/* ==========================
   모바일 메뉴
========================== */

function initMobileMenu() {
  const menuBtn = document.getElementById("menuBtn");
  const mobileNav = document.getElementById("mobileNav");

  if (!menuBtn || !mobileNav) return;

  menuBtn.addEventListener("click", () => {
    const isOpen = mobileNav.classList.toggle("active");
    menuBtn.classList.toggle("active", isOpen);
    document.body.classList.toggle("nav-open", isOpen);
    menuBtn.setAttribute("aria-expanded", String(isOpen));
    menuBtn.setAttribute("aria-label", isOpen ? "메뉴 닫기" : "메뉴 열기");
  });

  document.querySelectorAll(".top-nav a").forEach(link => {
    link.addEventListener("click", () => {
      menuBtn.classList.remove("active");
      mobileNav.classList.remove("active");
      document.body.classList.remove("nav-open");
      menuBtn.setAttribute("aria-expanded", "false");
      menuBtn.setAttribute("aria-label", "메뉴 열기");
    });
  });
}

/* ==========================
   로그인 상태 메뉴 변경
   - 실제 로그인: 서버 세션(/api/auth/check) 기준
   - 게스트 체험: 서버와 무관한 별도 플래그(jipchatgoGuestMode) 기준
     (네트워크가 불안정한 시연 상황에서도 항상 로그인된 것처럼 보이게 하기 위함)
========================== */

async function updateLoginMenu() {
  const logoutButtons = document.querySelectorAll(".logout-btn");

  // 로그아웃 버튼 리스너는 서버 응답을 기다리지 않고 먼저 등록
  logoutButtons.forEach(btn => {
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      handleLogout();
    });
  });

  const isGuest = localStorage.getItem("jipchatgoGuestMode") === "true";

  if (isGuest) {
    // 게스트 모드는 서버에 물어보지 않고 무조건 로그인된 것으로 처리
    document.body.classList.add("login-active");
  } else {
    try {
      const res = await fetch("/api/auth/check");
      const data = await res.json();
      document.body.classList.toggle("login-active", !!data.loggedIn);
      if (data.loggedIn && !data.guest) addAccountMenu();
    } catch (err) {
      // 네트워크 오류 등으로 확인 자체가 안 되면 로그아웃 상태로 취급
      document.body.classList.remove("login-active");
    }
  }
}

async function handleLogout() {
  localStorage.removeItem("jipchatgoGuestMode");

  // 게스트도 서버 세션(guest=true)이 있으므로 항상 서버 로그아웃 호출
  try {
    await fetch("/api/auth/logout", { method: "POST" });
  } catch (err) {
    // 서버 로그아웃 요청이 실패해도, 클라이언트 쪽 상태는 로그아웃으로 처리하고 진행
  }

  document.body.classList.remove("login-active");
  alert("로그아웃 되었습니다.");
  location.href = "/";
}

/* 회원(게스트 제외)에게만 "계정 설정" 메뉴를 로그아웃 버튼 앞에 추가 */
function addAccountMenu() {
  document.querySelectorAll(".logout-btn").forEach(btn => {
    const prev = btn.previousElementSibling;
    if (prev && prev.classList.contains("account-menu")) return;

    const link = document.createElement("a");
    link.className = "user-menu account-menu";
    link.href = "/account";
    link.innerHTML = '<i class="ti ti-user-cog"></i> 계정 설정';
    btn.parentNode.insertBefore(link, btn);
  });
}
