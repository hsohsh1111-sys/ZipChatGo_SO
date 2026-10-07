/* ==========================================================
   my-properties.js
   내 매물 목록(/my/properties) / 상세(/my/properties/{id}) 공용 스크립트
   - 목록 페이지: #mpGrid 가 있을 때
   - 상세 페이지: #mpDetail 이 있을 때
   ========================================================== */
(function () {
  // 수정 화면(register 수정 모드)이 준비되면 true 로 바꾸세요.
  var EDIT_READY = true;

  var STATUS = {
    PENDING: { label: '검토 중', cls: 'pending' },
    APPROVED: { label: '게시 중', cls: 'approved' },
    REJECTED: { label: '반려', cls: 'rejected' }
  };

  var DOC_TYPES = {
    OWNERSHIP: '등기부등본',
    BUILDING_REGISTER: '건축물대장',
    LAND_REGISTER: '토지대장',
    SEAL_CERTIFICATE: '인감증명서'
  };

  /* ---------- 공통 헬퍼 ---------- */
  function esc(v) {
    return String(v == null ? '' : v)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  // 만원 단위 숫자 -> "1억 2,000만원"
  function formatKoreanPrice(manwon) {
    var num = Number(manwon);
    if (!num || isNaN(num)) return '';
    var eok = Math.floor(num / 10000);
    var rest = num % 10000;
    if (eok > 0 && rest > 0) return eok + '억 ' + rest.toLocaleString('ko-KR') + '만원';
    if (eok > 0) return eok + '억원';
    return num.toLocaleString('ko-KR') + '만원';
  }

  function priceText(p) {
    if (p.dealType === '매매') {
      return p.price ? '매매 ' + formatKoreanPrice(p.price) : '가격 미입력';
    }
    if (p.dealType === '전세') {
      return p.deposit ? '전세 ' + formatKoreanPrice(p.deposit) : '보증금 미입력';
    }
    var dep = formatKoreanPrice(p.deposit) || '0만원';
    var mon = formatKoreanPrice(p.monthly) || '0만원';
    return '보증금 ' + dep + ' / 월세 ' + mon;
  }

  function formatPhone(v) {
    var d = String(v || '').replace(/[^0-9]/g, '');
    if (d.length === 11) return d.slice(0, 3) + '-' + d.slice(3, 7) + '-' + d.slice(7);
    if (d.length === 10) return d.slice(0, 3) + '-' + d.slice(3, 6) + '-' + d.slice(6);
    return v || '-';
  }

  function formatDate(iso) {
    return iso ? String(iso).slice(0, 10) : '-';
  }

  function statusBadge(status) {
    var s = STATUS[status] || { label: status || '-', cls: 'pending' };
    return '<span class="mp-badge ' + s.cls + '">' + esc(s.label) + '</span>';
  }

  function setState(html) {
    var el = document.getElementById('mpState');
    if (!el) return;
    el.innerHTML = html;
    el.style.display = html ? '' : 'none';
  }

  // 로그인 필요/게스트 -> 로그인 페이지로
  function goLogin() {
    location.href = '/login?redirect=' + encodeURIComponent(location.pathname);
  }

  async function api(url, options) {
    var res = await fetch(url, options);
    return res.json();
  }

  /* ==========================================================
     목록 페이지
     ========================================================== */
  async function initList() {
    var grid = document.getElementById('mpGrid');
    var empty = document.getElementById('mpEmpty');
    var summary = document.getElementById('mpSummary');
    var data;
    try {
      data = await api('/api/my/properties');
    } catch (e) {
      setState('서버와 통신하지 못했어요. 잠시 후 다시 시도해주세요.');
      return;
    }

    if (!data.success) {
      if (data.message === '로그인이 필요합니다.') { goLogin(); return; }
      setState(esc(data.message || '목록을 불러오지 못했어요.'));
      return;
    }

    var list = data.properties || [];
    setState('');
    summary.innerHTML = '총 <strong>' + list.length + '개</strong>의 매물이 있습니다.';

    if (!list.length) {
      empty.hidden = false;
      return;
    }

    grid.innerHTML = list.map(function (p) {
      var media = p.thumbnailUrl
        ? '<img src="' + esc(p.thumbnailUrl) + '" alt="매물 사진" loading="lazy">'
        : '<div class="mp-no-photo"><i class="ti ti-photo"></i></div>';
      var count = p.photoCount > 0 ? '<span class="mp-count">' + p.photoCount + '장</span>' : '';
      var addr = esc(p.address1) + (p.address2 ? ' ' + esc(p.address2) : '');
      var meta = [p.area ? esc(p.area) + '㎡' : '', formatDate(p.createdAt) + ' 등록']
        .filter(Boolean).join(' · ');

      return '<a class="favorite-card mp-card" href="/my/properties/' + esc(p.id) + '">' +
        '<div class="favorite-card-media">' + media + statusBadge(p.status) + count + '</div>' +
        '<div class="favorite-card-body">' +
          '<div class="favorite-card-type">' + esc(p.propertyType) + ' · ' + esc(p.dealType) + '</div>' +
          '<div class="favorite-card-title" title="' + addr + '">' + addr + '</div>' +
          '<div class="favorite-card-price">' + esc(priceText(p)) + '</div>' +
          '<div class="favorite-card-meta">' + meta + '</div>' +
        '</div></a>';
    }).join('');
  }

  /* ==========================================================
     상세 페이지
     ========================================================== */
  function parseJson(text) {
    try { return text ? JSON.parse(text) : null; } catch (e) { return null; }
  }

  function infoRow(label, value) {
    return '<div class="mp-info-row"><span>' + esc(label) + '</span><span>' +
      (value === '' || value == null ? '-' : value) + '</span></div>';
  }

  function chips(list) {
    return list && list.length
      ? '<div class="mp-chips">' + list.map(function (v) { return '<span>' + esc(v) + '</span>'; }).join('') + '</div>'
      : '<p class="mp-empty">선택한 항목이 없어요.</p>';
  }

  function renderDetail(p) {
    var transit = parseJson(p.transitInfo);
    var school = parseJson(p.schoolInfo);

    var notice = '';
    if (p.status === 'PENDING') {
      notice = '<div class="mp-notice">관리자 검토 중이에요. 승인되면 게시돼요.</div>';
    } else if (p.status === 'APPROVED') {
      notice = '<div class="mp-notice warn">현재 게시 중이에요. 내용을 수정하면 다시 검토를 받을 때까지 게시가 중단돼요.</div>';
    } else if (p.status === 'REJECTED') {
      notice = '<div class="mp-notice warn">검토에서 반려된 매물이에요. 내용을 수정하면 다시 검토를 받을 수 있어요.</div>';
    }

    var photos = p.photos || [];
    var gallery = photos.length
      ? '<div class="mp-gallery-main" id="mpMainPhoto"></div>' +
        '<div class="mp-gallery-thumbs" id="mpThumbs">' +
        photos.map(function (ph, i) {
          return '<button type="button" data-i="' + i + '" aria-label="사진 ' + (i + 1) + '"></button>';
        }).join('') + '</div>'
      : '<div class="mp-gallery-main"><i class="ti ti-photo"></i></div>';

    var transitRows = '';
    if (transit) {
      var buses = Array.from(new Set(transit.bus || []));
      transitRows += infoRow('가까운 지하철', esc(transit.subway || '-'));
      transitRows += infoRow('가까운 버스', esc(buses.length ? buses.join(', ') : '-'));
    }
    var schoolRows = '';
    if (school) {
      schoolRows += infoRow('초등학교', esc(school.elementary || '해당 없음'));
      schoolRows += infoRow('중학교', esc(school.middle || '해당 없음'));
    }

    var docs = p.documents || [];
    var docHtml = docs.length
      ? docs.map(function (d) {
          var label = DOC_TYPES[d.docType] || d.docType;
          var link = d.url
            ? '<a href="' + esc(d.url) + '" target="_blank" rel="noopener noreferrer">열기</a>'
            : '<span class="mp-empty">열람 불가</span>';
          return '<div class="mp-doc-row"><span>' + esc(label) + ' · ' + esc(d.originalName) + '</span>' + link + '</div>';
        }).join('')
      : '<p class="mp-empty">첨부한 서류가 없어요.</p>';

    var fee = p.maintenanceFee ? p.maintenanceFee + '만원' : '';
    if (p.etcFee) fee += (fee ? ' · ' : '') + esc(p.etcFee);

    var html =
      notice +
      '<section class="mp-section">' +
        '<div class="mp-title-row">' + statusBadge(p.status) + '<span class="mp-deal">' + esc(p.propertyType) + '</span></div>' +
        '<div class="mp-title-price">' + esc(priceText(p)) + '</div>' +
        '<div class="mp-addr">' + esc(p.address1) + (p.address2 ? ' ' + esc(p.address2) : '') + '</div>' +
      '</section>' +
      '<section class="mp-section"><h2>사진</h2>' + gallery + '</section>' +
      '<section class="mp-section"><h2>기본 정보</h2>' +
        infoRow('매물 유형', esc(p.propertyType)) +
        infoRow('거래 유형', esc(p.dealType)) +
        infoRow('전용면적', p.area ? esc(p.area) + '㎡' : '') +
        infoRow('층수', esc(p.floorInfo)) +
        infoRow('방 / 욕실', (p.rooms != null ? esc(p.rooms) : '-') + ' / ' + (p.baths != null ? esc(p.baths) : '-')) +
        infoRow('관리비', fee) +
        infoRow('등록일', esc(formatDate(p.createdAt))) +
      '</section>' +
      '<section class="mp-section"><h2>특징 · 옵션</h2>' +
        '<div class="mp-sub-title">특징</div>' + chips(p.tags) +
        '<div class="mp-sub-title">옵션</div>' + chips(p.options) +
        '<div class="mp-sub-title">관리비 포함 항목</div>' + chips(p.utilities) +
      '</section>' +
      (transitRows || schoolRows
        ? '<section class="mp-section"><h2>교통 · 학군</h2>' + transitRows + schoolRows + '</section>'
        : '') +
      '<section class="mp-section"><h2>집주인 정보</h2>' +
        infoRow('이름', esc(p.ownerName)) +
        infoRow('연락처', esc(formatPhone(p.ownerPhone))) +
      '</section>' +
      '<section class="mp-section"><h2>첨부 서류</h2>' + docHtml + '</section>' +
      '<div class="mp-actions">' +
        '<button type="button" class="mp-btn danger" id="mpDeleteBtn"><i class="ti ti-trash"></i> 삭제</button>' +
        '<button type="button" class="mp-btn primary" id="mpEditBtn"><i class="ti ti-pencil"></i> 수정</button>' +
      '</div>';

    var box = document.getElementById('mpDetail');
    box.innerHTML = html;
    box.style.display = '';

    // 사진 갤러리
    if (photos.length) {
      var main = document.getElementById('mpMainPhoto');
      var thumbs = Array.from(document.querySelectorAll('#mpThumbs button'));
      var show = function (i) {
        main.style.backgroundImage = photos[i].url ? 'url("' + photos[i].url + '")' : '';
        thumbs.forEach(function (b, idx) { b.classList.toggle('active', idx === i); });
      };
      thumbs.forEach(function (b, i) {
        b.style.backgroundImage = photos[i].url ? 'url("' + photos[i].url + '")' : '';
        b.addEventListener('click', function () { show(i); });
      });
      show(0);
    }

    document.getElementById('mpEditBtn').addEventListener('click', function () {
      if (!EDIT_READY) {
        alert('수정 화면은 준비 중이에요.');
        return;
      }
      if (p.status === 'APPROVED' &&
          !confirm('수정하면 다시 검토를 받을 때까지 게시가 중단돼요. 계속할까요?')) return;
      location.href = '/property/register?edit=' + encodeURIComponent(p.id);
    });

    var delBtn = document.getElementById('mpDeleteBtn');
    delBtn.addEventListener('click', async function () {
      if (!confirm('이 매물을 삭제할까요?\n사진과 서류도 함께 삭제되며 복구할 수 없어요.')) return;
      delBtn.disabled = true;
      try {
        var r = await api('/api/my/properties/' + encodeURIComponent(p.id), { method: 'DELETE' });
        if (r.success) {
          alert('매물이 삭제되었어요.');
          location.href = '/my/properties';
          return;
        }
        alert(r.message || '삭제에 실패했어요.');
      } catch (e) {
        alert('서버와 통신하지 못했어요. 잠시 후 다시 시도해주세요.');
      }
      delBtn.disabled = false;
    });
  }

  async function initDetail() {
    var id = location.pathname.split('/').filter(Boolean).pop();
    var data;
    try {
      data = await api('/api/my/properties/' + encodeURIComponent(id));
    } catch (e) {
      setState('서버와 통신하지 못했어요. 잠시 후 다시 시도해주세요.');
      return;
    }

    if (!data.success) {
      if (data.message === '로그인이 필요합니다.') { goLogin(); return; }
      setState(esc(data.message || '매물을 불러오지 못했어요.') +
        '<br><a href="/my/properties">내 매물 목록으로</a>');
      return;
    }

    setState('');
    renderDetail(data.property);
  }

  /* ---------- 진입 ---------- */
  document.addEventListener('DOMContentLoaded', function () {
    if (document.getElementById('mpGrid')) initList();
    else if (document.getElementById('mpDetail')) initDetail();
  });
})();
