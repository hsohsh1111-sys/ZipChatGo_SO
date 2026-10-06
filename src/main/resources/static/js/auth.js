// ==================================================
// auth.js — 집찾GO 로그인/회원가입 공통 스크립트
// member/auth.html 에서 사용 (login/signup 통합)
// ==================================================

document.addEventListener('DOMContentLoaded', () => {

  /* ---------- 로그인 후 이동할 목적지 (?redirect=...) ----------
     이 프로젝트는 정적 HTML 파일이 아니라 Thymeleaf 컨트롤러 라우팅을 쓰므로,
     기본 목적지는 파일 경로(index.html)가 아니라 실제 라우팅 경로(/)여야 함. */
  const params = new URLSearchParams(window.location.search);
  const redirectTarget = params.get('redirect') || '/';

  function goToRedirectTarget() {
    window.location.href = redirectTarget;
  }

  // 소셜 로그인 실패 시 서버가 /login?oauthError=코드 로 보냄
  const oauthError = params.get('oauthError');
  if (oauthError) {
    const oauthMessages = {
      email_exists: '이미 같은 이메일로 가입된 계정이 있어서 가입할 수 없어요. 이메일과 비밀번호로 로그인해 주세요.',
      invalid_user: '소셜 계정 정보를 가져오지 못했어요. 잠시 후 다시 시도해주세요.',
      access_denied: '소셜 로그인이 취소됐어요.'
    };
    showToast(oauthMessages[oauthError] || '소셜 로그인에 실패했어요. 다시 시도해주세요.', 5000);

    // 새로고침 시 토스트 반복 방지 (oauthError만 제거)
    params.delete('oauthError');
    const qs = params.toString();
    history.replaceState(null, '', location.pathname + (qs ? '?' + qs : '') + location.hash);
  }

  // 게스트 모드 표시용 플래그
  // 실제 서버 인증은 HttpSession이 담당한다.
  function setGuestMode() {
    localStorage.setItem('jipchatgoGuestMode', 'true');
  }

  // 특정 페이지로 가기 위해 로그인이 필요해서 넘어온 경우, 안내 배너를 보여줘요.
  if (params.get('redirect')) {
    const banner = document.createElement('div');
    banner.className = 'redirect-notice';

    const isRegister = redirectTarget.includes('/property/register');

    banner.innerHTML = `<i class="ti ti-info-circle"></i> ${
      isRegister
        ? '매물을 등록하려면 먼저 로그인해주세요.'
        : '계속하려면 먼저 로그인해주세요.'
    }`;

    const tabs = document.querySelector('.auth-tabs');
    if (tabs) tabs.insertAdjacentElement('beforebegin', banner);
  }

  /* ---------- 헤더 스크롤 / 모바일 메뉴 ---------- */
  const header = document.getElementById('mainHeader');

  if (header) {
    window.addEventListener('scroll', () => {
      header.classList.toggle('scrolled', window.scrollY > 20);
    });
  }

  const menuBtn = document.getElementById('menuBtn');
  const mobileNav = document.getElementById('mobileNav');

  if (menuBtn && mobileNav) {
    menuBtn.addEventListener('click', () => {
      menuBtn.classList.toggle('active');
      mobileNav.classList.toggle('active');
    });
  }

  /* ---------- 탭 전환 ---------- */
  const tabs = document.querySelectorAll('.auth-tab');
  const forms = document.querySelectorAll('.auth-form');

  const visualCopy = {
    login: {
      eyebrow: '로그인',
      title: 'AI가 추천하는 내 집,<br>로그인하고 이어가세요',
      desc: '저장해 둔 조건과 관심 매물을 그대로 불러와 드려요.'
    },

    signup: {
      eyebrow: '회원가입',
      title: '몇 분이면 충분해요,<br>지금 시작하는 부동산 여정',
      desc: '일반회원과 공인중개사 모두 집찾GO에서 시작할 수 있어요.'
    }
  };

  function switchTab(name) {

    tabs.forEach(t => {
      t.classList.toggle('active', t.dataset.tab === name);
    });

    forms.forEach(f => {
      f.classList.toggle('active', f.dataset.form === name);
    });

    const eyebrowEl = document.querySelector('.visual-copy .eyebrow');
    const titleEl = document.querySelector('.visual-copy h2');
    const descEl = document.querySelector('.visual-copy p');

    const copy = visualCopy[name];

    if (copy && eyebrowEl && titleEl && descEl) {

      [eyebrowEl, titleEl, descEl].forEach(el => {
        el.style.opacity = 0;
      });

      setTimeout(() => {

        eyebrowEl.textContent = copy.eyebrow;
        titleEl.innerHTML = copy.title;
        descEl.textContent = copy.desc;

        [eyebrowEl, titleEl, descEl].forEach(el => {
          el.style.opacity = 1;
        });

      }, 150);
    }

    updateKeyring(name);

    history.replaceState(
      null,
      '',
      name === 'signup' ? '#signup' : '#login'
    );
  }

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      switchTab(tab.dataset.tab);
    });
  });

  document.querySelectorAll('[data-switch-to]').forEach(btn => {
    btn.addEventListener('click', () => {
      switchTab(btn.dataset.switchTo);
    });
  });

  /* ---------- URL 해시로 초기 탭 결정 ---------- */
  if (
    location.hash === '#signup' ||
    document.body.dataset.defaultTab === 'signup'
  ) {
    switchTab('signup');
  } else {
    switchTab('login');
  }

  /* ---------- 진행 단계 열쇠고리 ---------- */
  function updateKeyring(name) {

    const keys = document.querySelectorAll('.keyring .key');

    if (!keys.length) return;

    const filledCount =
      name === 'signup'
        ? getSignupProgress()
        : 1;

    keys.forEach((key, i) => {
      key.classList.toggle('filled', i < filledCount);
    });
  }

  function getSignupProgress() {

    const name = document.getElementById('suName');
    const email = document.getElementById('suEmail');
    const pw = document.getElementById('suPassword');

    let count = 0;

    if (name && name.value.trim()) count++;
    if (email && email.value.trim()) count++;
    if (pw && pw.value.trim()) count++;

    return count;
  }

  ['suName', 'suEmail', 'suPassword'].forEach(id => {

    const el = document.getElementById(id);

    if (el) {
      el.addEventListener('input', () => {
        updateKeyring('signup');
      });
    }
  });

  /* ---------- 비밀번호 보이기/숨기기 ---------- */
  document.querySelectorAll('.toggle-visibility').forEach(btn => {

    btn.addEventListener('click', () => {

      const input = document.getElementById(btn.dataset.target);

      if (!input) return;

      const isPw = input.type === 'password';

      input.type = isPw ? 'text' : 'password';

      btn.innerHTML = isPw
        ? '<i class="ti ti-eye-off"></i>'
        : '<i class="ti ti-eye"></i>';
    });
  });

  /* ---------- 회원 유형 선택 ---------- */
  const typeCards = document.querySelectorAll('.type-card');
  const brokerFields = document.getElementById('brokerFields');

  typeCards.forEach(card => {

    card.addEventListener('click', () => {

      typeCards.forEach(c => {
        c.classList.remove('selected');
      });

      card.classList.add('selected');

      card.querySelector('input').checked = true;

      if (brokerFields) {
        brokerFields.classList.toggle(
          'show',
          card.dataset.type === 'broker'
        );
      }
    });
  });

  /* ---------- 전체 동의 ---------- */
  const agreeAll = document.getElementById('agreeAll');
  const agreeItems = document.querySelectorAll('.agree-item');

  if (agreeAll) {

    agreeAll.addEventListener('change', () => {

      agreeItems.forEach(item => {
        item.checked = agreeAll.checked;
      });
    });

    agreeItems.forEach(item => {

      item.addEventListener('change', () => {

        agreeAll.checked =
          Array.from(agreeItems).every(i => i.checked);

      });
    });
  }

  /* ---------- 휴대폰번호 3칸 입력 ---------- */
  const phoneBoxes = ['suPhone1', 'suPhone2', 'suPhone3']
    .map(id => document.getElementById(id));

  if (phoneBoxes.every(Boolean)) {

    phoneBoxes.forEach((box, i) => {

      // 숫자만 허용 + 칸이 가득 차면 다음 칸으로 이동
      box.addEventListener('input', () => {
        box.value = box.value.replace(/[^0-9]/g, '');

        if (box.value.length >= box.maxLength && i < phoneBoxes.length - 1) {
          phoneBoxes[i + 1].focus();
        }
      });

      // 빈 칸에서 Backspace 누르면 이전 칸으로 이동
      box.addEventListener('keydown', (e) => {
        if (e.key === 'Backspace' && !box.value && i > 0) {
          e.preventDefault();
          const prev = phoneBoxes[i - 1];
          prev.focus();
          prev.value = prev.value.slice(0, -1);
        }
      });

      // 전체 번호를 붙여넣으면 칸에 나눠 채움 (01012345678 / 010-1234-5678)
      box.addEventListener('paste', (e) => {
        const digits = (e.clipboardData || window.clipboardData)
          .getData('text').replace(/[^0-9]/g, '').slice(0, 11);

        if (digits.length <= box.maxLength) return; // 짧으면 기본 동작

        e.preventDefault();
        phoneBoxes[0].value = digits.slice(0, 3);
        phoneBoxes[1].value = digits.slice(3, digits.length - 4);
        phoneBoxes[2].value = digits.slice(-4);
        phoneBoxes[2].focus();
      });
    });
  }

  /* ---------- 이메일 유효성 검사 ---------- */
  function validateEmail(value) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
  }

  /* ==================================================
     로그인
     ================================================== */

  const loginForm = document.getElementById('loginForm');

  if (loginForm) {

    loginForm.addEventListener('submit', async (e) => {

      e.preventDefault();

      const email = document.getElementById('loginEmail');
      const pw = document.getElementById('loginPassword');

      let ok = true;

      if (!validateEmail(email.value)) {
        email.classList.add('invalid');
        ok = false;
      } else {
        email.classList.remove('invalid');
      }

      if (pw.value.length < 4) {
        pw.classList.add('invalid');
        ok = false;
      } else {
        pw.classList.remove('invalid');
      }

      if (!ok) return;

      const submitBtn =
        loginForm.querySelector('button[type="submit"]');

      if (submitBtn) submitBtn.disabled = true;

      try {

        const res = await fetch('/api/auth/login', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            email: email.value,
            password: pw.value
          })
        });

        const data = await res.json();

        if (data.success) {

          // 일반회원 로그인 성공 시 게스트 플래그 제거
          localStorage.removeItem('jipchatgoGuestMode');

          showToast(`${data.name}님, 로그인 되었습니다.`);

          setTimeout(goToRedirectTarget, 700);

        } else {

          showToast(
            data.message || '로그인에 실패했어요.'
          );
        }

      } catch (err) {

        console.error(err);

        showToast(
          '서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.'
        );

      } finally {

        if (submitBtn) submitBtn.disabled = false;
      }
    });
  }

  /* ==================================================
     회원가입
     ================================================== */

  const signupForm = document.getElementById('signupForm');

  if (signupForm) {

    signupForm.addEventListener('submit', async (e) => {

      e.preventDefault();

      const suName = document.getElementById('suName');
      const suEmail = document.getElementById('suEmail');
      const phoneValue = phoneBoxes.map(b => b.value.trim()).join('');
      const pw = document.getElementById('suPassword');
      const pwCheck = document.getElementById('suPasswordCheck');

      const requiredAgree =
        document.querySelectorAll('.agree-item[required]');

      // 이름 / 이메일 형식 검사 (오류 문구 요소가 없어 토스트로 안내)
      if (!suName.value.trim()) {
        suName.classList.add('invalid');
        showToast('이름을 입력해주세요.');
        return;
      }
      suName.classList.remove('invalid');

      if (!validateEmail(suEmail.value.trim())) {
        suEmail.classList.add('invalid');
        showToast('올바른 이메일 형식을 입력해주세요.');
        return;
      }
      suEmail.classList.remove('invalid');

      // 휴대폰은 선택: 하나라도 입력했다면 형식 확인
      if (phoneValue && !/^01[0-9]{8,9}$/.test(phoneValue)) {
        showToast('휴대폰번호 형식을 확인해주세요.');
        phoneBoxes[0].focus();
        return;
      }

      let ok = true;

      if (pw.value.length < 8) {

        pw.classList.add('invalid');
        ok = false;

      } else {

        pw.classList.remove('invalid');
      }

      if (pwCheck.value !== pw.value || !pwCheck.value) {

        pwCheck.classList.add('invalid');
        ok = false;

      } else {

        pwCheck.classList.remove('invalid');
      }

      requiredAgree.forEach(chk => {

        if (!chk.checked) {
          ok = false;
        }
      });

      if (!ok) {

        if (
          !Array.from(requiredAgree).every(c => c.checked)
        ) {
          showToast('필수 약관에 동의해주세요.');
        }

        return;
      }

      // 공인중개사 가입은 아직 서버에서 지원하지 않음
      const selectedType =
        document.querySelector(
          'input[name="memberType"]:checked'
        );

      if (
        selectedType &&
        selectedType.value === 'broker'
      ) {

        showToast(
          '공인중개사 회원가입은 아직 준비 중이에요. 곧 지원할게요!'
        );

        return;
      }

      const submitBtn =
        signupForm.querySelector('button[type="submit"]');

      if (submitBtn) submitBtn.disabled = true;

      try {

        const res = await fetch('/api/auth/signup', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({

            email: suEmail.value.trim(),
            password: pw.value,
            name: suName.value.trim(),
            phone: phoneValue

          })
        });

        const data = await res.json();

        if (data.success) {

          // 회원가입 성공 후 게스트 플래그 제거
          localStorage.removeItem('jipchatgoGuestMode');

          showToast(
            '회원가입이 완료됐어요. 환영합니다!'
          );

          setTimeout(goToRedirectTarget, 700);

        } else {

          showToast(
            data.message || '회원가입에 실패했어요.'
          );
        }

      } catch (err) {

        console.error(err);

        showToast(
          '서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.'
        );

      } finally {

        if (submitBtn) submitBtn.disabled = false;
      }
    });
  }

  /* ==================================================
     소셜 로그인 (카카오/네이버/구글)
     ================================================== */

  document.querySelectorAll('.social-btn[data-provider]').forEach(btn => {

    btn.addEventListener('click', () => {

      // 공인중개사 소셜 가입은 지원하지 않음 (회원가입 폼에서만 해당)
      const selectedType =
        document.querySelector('input[name="memberType"]:checked');

      if (
        btn.closest('#signupForm') &&
        selectedType &&
        selectedType.value === 'broker'
      ) {
        showToast('공인중개사는 소셜 가입을 지원하지 않아요.');
        return;
      }

      // 게스트 플래그 정리 (소셜 로그인은 서버 리다이렉트로 끝나므로 미리 제거)
      localStorage.removeItem('jipchatgoGuestMode');

      // "다른 계정으로 로그인" 체크 시 계정 선택/재로그인 요청
      const switchBox = btn.closest('form')?.querySelector('.switch-account');
      const switchParam = (switchBox && switchBox.checked) ? '&switchAccount=1' : '';

      window.location.href =
        '/oauth/start/' + btn.dataset.provider +
        '?redirect=' + encodeURIComponent(redirectTarget) + switchParam;
    });
  });

  /* ==================================================
     게스트 체험 로그인
     
     기존:
       localStorage만 저장

     변경:
       1. Spring 서버에 게스트 로그인 요청
       2. 서버 HttpSession 생성
       3. localStorage에도 기존 플래그 유지
     ================================================== */

  document.querySelectorAll('.guest-btn').forEach(btn => {

    btn.addEventListener('click', async () => {

      // 중복 클릭 방지
      btn.disabled = true;

      try {

        const res = await fetch('/api/auth/guest', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          }
        });

        const data = await res.json();

        if (data.success) {

          // 기존 프론트의 게스트 모드 플래그도 유지
          setGuestMode();

          showToast(
            '게스트 모드로 접속했어요'
          );

          setTimeout(
            goToRedirectTarget,
            900
          );

        } else {

          showToast(
            data.message || '게스트 로그인에 실패했어요.'
          );

          btn.disabled = false;
        }

      } catch (err) {

        console.error(err);

        showToast(
          '서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.'
        );

        btn.disabled = false;
      }
    });
  });

  /* ---------- 토스트 ---------- */
  function showToast(message, duration = 2400) {

    let toast = document.querySelector('.toast');

    if (!toast) {

      toast = document.createElement('div');
      toast.className = 'toast';

      document.body.appendChild(toast);
    }

    toast.textContent = message;

    toast.classList.add('show');

    clearTimeout(showToast._t);

    showToast._t = setTimeout(() => {
      toast.classList.remove('show');
    }, duration);
  }

  /* ---------- Back to Top ---------- */
  const backToTop = document.getElementById('backToTop');

  if (backToTop) {

    window.addEventListener('scroll', () => {

      backToTop.classList.toggle(
        'show',
        window.scrollY > 400
      );
    });

    backToTop.addEventListener('click', () => {

      window.scrollTo({
        top: 0,
        behavior: 'smooth'
      });
    });
  }

});