// Лендинг: спрайты, граф активности, живое превью интерфейса расширения, подсказки для телефона.
(function () {
  'use strict';
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  // ---------- пиксельный совёнок (как в презентации): кадры рядом по горизонтали ----------
  const PAL = { k: '#15142b', b: '#7c3aed', d: '#5b21b6', l: '#e9ddff', w: '#ffffff', p: '#15142b', y: '#ffc400', o: '#ff9f1c', g: '#ffc400' };
  const OWL = [
    '...k........k...', '..kbk......kbk..', '..kbbkkkkkkbbk..', '.kbbbbbbbbbbbbk.', '.kbwwwwbbwwwwbk.', 'kbwwppwbbwppwwbk', 'kbwwppwyywppwwbk', '.kbwwwwyywwwwbk.',
    '.kbbbbbbbbbbbbk.', '.kbdllllllllldk.', '.kbllgllllgllbk.', '.kbllllllllllbk.', '..kbllgllgllbk..', '..kkbbbbbbbbkk..', '...kookkkkook...', '................'];
  const OWL_BLINK = OWL.map((r, i) => (i === 5 ? 'kbwkkkkbbkkkkwbk' : i === 6 ? 'kbwwwwwyywwwwwbk' : r));
  function sprite(frames, scale) {
    const h = frames[0].length, w = frames[0][0].length, c = document.createElement('canvas');
    c.width = w * frames.length * scale; c.height = h * scale;
    const g = c.getContext('2d');
    frames.forEach((f, fi) => f.forEach((row, y) => [...row].forEach((ch, x) => {
      if (ch === '.') return;
      g.fillStyle = PAL[ch]; g.fillRect((fi * w + x) * scale, y * scale, scale, scale);
    })));
    return c.toDataURL();
  }
  const owl = sprite([OWL, OWL_BLINK], 8);
  $$('[data-sprite]').forEach((el) => { el.style.backgroundImage = `url(${owl})`; });

  // ---------- живая сова: следит глазами за курсором, моргает, машет крыльями, ухает по клику ----------
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const owlCanvas = $('#owl');
  const owlBtn = $('#owlBtn');
  const owlState = { px: 0, py: 0, blink: false, wing: 0 };
  function drawOwl() {
    const g = owlCanvas.getContext('2d');
    const S = 7, OX = 2, OY = 1;
    g.clearRect(0, 0, owlCanvas.width, owlCanvas.height);
    const put = (x, y, ch) => { g.fillStyle = PAL[ch]; g.fillRect((x + OX) * S, (y + OY) * S, S, S); };
    // крылья: в взмахе — поднятые перья за контуром тела
    if (owlState.wing) {
      [[-1, 7, 'k'], [-2, 8, 'k'], [-1, 8, 'b'], [-2, 9, 'k'], [-1, 9, 'b'], [-1, 10, 'k'],
        [16, 7, 'k'], [17, 8, 'k'], [16, 8, 'b'], [17, 9, 'k'], [16, 9, 'b'], [16, 10, 'k']].forEach(([x, y, c]) => put(x, y, c));
    }
    OWL.forEach((row, y) => [...row].forEach((ch, x) => {
      if (ch === '.') return;
      const eye = y >= 4 && y <= 7 && ((x >= 2 && x <= 6) || (x >= 9 && x <= 13)) && (ch === 'w' || ch === 'p');
      put(x, y, eye ? 'w' : ch);
    }));
    if (owlState.blink) {
      [3, 4, 5, 6, 9, 10, 11, 12].forEach((x) => put(x, 5, 'k'));
      return;
    }
    const pupils = (cx) => {
      const x = cx + owlState.px, y = 5 + owlState.py;
      put(x, y, 'p'); put(x + 1, y, 'p'); put(x, y + 1, 'p'); put(x + 1, y + 1, 'p');
    };
    pupils(4); pupils(10);
  }
  if (owlCanvas) {
    drawOwl();
    window.addEventListener('pointermove', (e) => {
      const r = owlCanvas.getBoundingClientRect();
      const dx = e.clientX - (r.left + r.width / 2), dy = e.clientY - (r.top + r.height * 0.4);
      const px = Math.abs(dx) < 50 ? 0 : Math.sign(dx), py = Math.abs(dy) < 50 ? 0 : Math.sign(dy);
      if (px !== owlState.px || py !== owlState.py) { owlState.px = px; owlState.py = py; drawOwl(); }
    });
    const blink = () => {
      owlState.blink = true; drawOwl();
      setTimeout(() => { owlState.blink = false; drawOwl(); }, 130);
      setTimeout(blink, 2200 + Math.random() * 3200);
    };
    const flap = (times) => {
      let n = times * 2;
      const step = () => { owlState.wing = owlState.wing ? 0 : 1; drawOwl(); if (--n > 0) setTimeout(step, 110); else { owlState.wing = 0; drawOwl(); } };
      step();
    };
    if (!reduced) {
      setTimeout(blink, 1600);
      setInterval(() => { if (Math.random() < 0.5) flap(2); }, 5200);
    }
    owlBtn.addEventListener('mouseenter', () => flap(1));
    owlBtn.addEventListener('click', () => {
      flap(3);
      owlBtn.classList.remove('hoot'); void owlBtn.offsetWidth; owlBtn.classList.add('hoot');
      const bubble = $('#owlBubble');
      bubble.textContent = ['уху!', 'звёзды настоящие?', 'проверим!', 'ух-ух'][Math.floor(Math.random() * 4)];
      bubble.classList.add('on');
      clearTimeout(owlBtn._t); owlBtn._t = setTimeout(() => bubble.classList.remove('on'), 1400);
      const colors = ['#ffc400', '#ff2e88', '#7c3aed', '#2f6bff', '#00b86b'];
      for (let i = 0; i < 14; i++) {
        const p = document.createElement('i');
        p.className = 'pix';
        const ang = Math.random() * Math.PI * 2, dist = 60 + Math.random() * 90;
        p.style.cssText = `left:${50 + Math.random() * 40}px;top:${40 + Math.random() * 30}px;--c:${colors[i % colors.length]};` +
          `--dx:${Math.cos(ang) * dist}px;--dy:${Math.sin(ang) * dist - 40}px;animation-delay:${Math.random() * 0.12}s`;
        owlBtn.append(p);
        setTimeout(() => p.remove(), 1300);
      }
      burst(30 + Math.floor(Math.random() * 90));
    });
  }

  // ---------- граф активности: зелёная органика, розовый всплеск проявляется вместе с накруткой ----------
  const contrib = $('#contrib');
  let seed = 7;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  const hotCells = [];
  for (let i = 0; i < 26 * 7; i++) {
    const col = Math.floor(i / 7);
    const cell = document.createElement('i');
    cell.style.setProperty('--c', i);
    if (col >= 19 && col <= 21 && rnd() > 0.25) hotCells.push(cell);
    else { const v = rnd(); if (v > 0.55) cell.className = 'l' + Math.min(4, 1 + Math.floor((v - 0.55) * 9)); }
    contrib.append(cell);
  }

  // ---------- накрутка в прямом эфире: счётчик звёзд бежит, всплеск вспыхивает, бейдж поднимает тревогу ----------
  const starCount = $('#starCount');
  const starBtn = starCount && starCount.closest('.starbtn');
  const badge = $('#heroBadge');
  let stars = 2113;
  const fmt = (n) => String(n).replace(/\B(?=(\d{3})+(?!\d))/g, '\u2009');
  function burst(total) {
    let left = total;
    starBtn.classList.add('tick');
    const step = () => {
      const add = Math.min(left, 1 + Math.floor(Math.random() * Math.max(2, total / 12)));
      stars += add; left -= add;
      starCount.textContent = fmt(stars);
      const c = hotCells[Math.floor(Math.random() * hotCells.length)];
      if (c && c.classList.contains('hot')) { c.classList.remove('flash'); void c.offsetWidth; c.classList.add('flash'); }
      if (left > 0) setTimeout(step, 45 + Math.random() * 60); else starBtn.classList.remove('tick');
    };
    step();
    badge.classList.remove('bump'); void badge.offsetWidth; badge.classList.add('bump');
  }
  function intro() {
    hotCells.forEach((c, i) => setTimeout(() => c.classList.add('hot'), 700 + i * 120));
    setTimeout(() => burst(480), 700);
    const score = $('#badgeScore'), level = $('#badgeLevel');
    const from = performance.now() + 900, dur = 1800;
    const tick = (now) => {
      const k = Math.min(1, Math.max(0, (now - from) / dur));
      const v = Math.round(78 * (1 - Math.pow(1 - k, 3)));
      score.textContent = String(v);
      level.textContent = v < 30 ? 'low' : v < 60 ? 'medium' : 'high';
      if (k < 1) requestAnimationFrame(tick); else badge.classList.add('alarm');
    };
    requestAnimationFrame(tick);
    setInterval(() => { if (!document.hidden) burst(20 + Math.floor(Math.random() * 70)); }, 6500);
  }
  if (reduced) {
    hotCells.forEach((c) => c.classList.add('hot'));
    $('#badgeScore').textContent = '78'; $('#badgeLevel').textContent = 'high'; badge.classList.add('alarm');
  } else {
    intro();
  }

  // ---------- мерцающие пиксельные звёзды на фоне первого экрана ----------
  const sky = $('#sky');
  const skyColors = ['#ffc400', '#a77bff', '#2f6bff', '#ff6aa9', '#49e6a0'];
  for (let i = 0; i < 22; i++) {
    const st = document.createElement('i');
    st.style.cssText = `left:${Math.random() * 100}%;top:${Math.random() * 100}%;--c:${skyColors[i % skyColors.length]};` +
      `--d:${2.5 + Math.random() * 3}s;--delay:${-Math.random() * 5}s;scale:${0.6 + Math.random() * 0.8}`;
    sky.append(st);
  }

  // ---------- бегущая строка ----------
  const items = [['s', '★', '+412 звёзд за 2 часа'], ['p', '■', 'C4: 30 общих аккаунтов'], ['g', '●', 'органика: score 6'],
    ['s', '★', 'GH Archive · 6 часов'], ['p', '■', 'C2: 88% аккаунтов моложе 30 дней'], ['g', '●', 'explain по каждой проверке']];
  const line = items.map(([c, i, t]) => `<span class="it"><span class="${c}">${i}</span>${t}</span>`).join('');
  $('#ticker').innerHTML = line + line;

  // ---------- живое превью: настоящий ui.js расширения на примерах данных ----------
  const UI = window.StarGuardUI, THEME = window.StarGuardTheme, S = window.STAR_GUARD_SAMPLES;
  const frame = $('.gh-frame');
  if (UI && THEME && S) {
    for (const [k, v] of Object.entries(THEME.LIGHT)) frame.style.setProperty(k, v);
    const noop = () => {};
    const actions = { onScan: noop, onScanDay: noop, onRetry: noop, onOpenOptions: noop };
    const cardHost = $('#demoCard').attachShadow({ mode: 'open' });
    const badgeHost = $('#demoBadge').attachShadow({ mode: 'open' });
    const show = (key) => {
      const data = S[key];
      $('#demoRepo').textContent = data.repo.owner + ' / ' + data.repo.name;
      UI.renderCard(cardHost, { kind: 'report', data }, actions);
      UI.renderBadge(badgeHost, { kind: 'report', data }, () => $('#demoCard').scrollIntoView({ behavior: 'smooth', block: 'center' }));
      $$('.tabs [role=tab]').forEach((t) => t.setAttribute('aria-selected', String(t.dataset.sample === key)));
    };
    $$('.tabs [role=tab]').forEach((t) => t.addEventListener('click', () => show(t.dataset.sample)));
    show('farm');
  } else {
    $('#demo').hidden = true;
  }

  // ---------- адрес сервера для шага установки: server.json из репозитория, иначе запасной ----------
  const CFG = window.STAR_GUARD_CONFIG || { apiBase: 'http://localhost:8080', remoteConfigUrl: '' };
  const isLocal = (u) => /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(u);
  function showServer(api) {
    $('#apiBase').textContent = api;
    const local = isLocal(api);
    $('#localHint').hidden = !local;
    $('#localCmd').hidden = !local;
  }
  (async () => {
    let api = '';
    if (CFG.remoteConfigUrl) {
      try {
        const r = await fetch(CFG.remoteConfigUrl, { cache: 'no-store' });
        if (r.ok) { const u = new URL(String((await r.json()).apiBase || '')); if (/^https?:$/.test(u.protocol)) api = u.toString().replace(/\/+$/, ''); }
      } catch (ignored) { api = ''; }
    }
    showServer(api || CFG.apiBase);
  })();

  // ---------- тост и копирование ----------
  const toast = $('#toast');
  let toastTimer;
  function say(text) {
    toast.textContent = text; toast.classList.add('on');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.classList.remove('on'), 2200);
  }
  async function copy(text) {
    try { await navigator.clipboard.writeText(text); return true; } catch (e) {
      const ta = document.createElement('textarea'); ta.value = text; ta.setAttribute('readonly', ''); ta.style.position = 'fixed'; ta.style.opacity = '0';
      document.body.append(ta); ta.select();
      let ok = false; try { ok = document.execCommand('copy'); } catch (ignored) { ok = false; }
      ta.remove(); return ok;
    }
  }
  $$('[data-copy]').forEach((b) => b.addEventListener('click', async () => say(await copy(b.dataset.copy) ? 'Скопировано' : 'Не удалось скопировать')));

  // ---------- телефон: расширение не поставить, предлагаем отправить ссылку себе ----------
  const mobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent) || (navigator.maxTouchPoints > 1 && /Macintosh/.test(navigator.userAgent));
  if (mobile) {
    $('#mobileNote').hidden = false;
    $('.cta').hidden = true;
    const url = location.origin + location.pathname;
    const text = 'Star Guard — расширение для Chrome, открыть на компьютере';
    $('#pageUrl').textContent = url.replace(/^https?:\/\//, '').replace(/\/$/, '');
    $('#copyUrl').addEventListener('click', async () => say(await copy(url) ? 'Ссылка скопирована' : 'Не удалось скопировать'));
    $('#sendTg').href = 'https://t.me/share/url?url=' + encodeURIComponent(url) + '&text=' + encodeURIComponent(text);
    $('#sendMail').href = 'mailto:?subject=' + encodeURIComponent('Star Guard') + '&body=' + encodeURIComponent(text + '\n' + url);
    if (navigator.share) {
      const share = $('#shareBtn');
      share.hidden = false;
      $('#shareHint').hidden = false;
      share.addEventListener('click', () => navigator.share({ title: 'Star Guard', text, url }).catch(() => {}));
    }
  }

  // ---------- появление при прокрутке ----------
  const blocks = $$('.block > *');
  if ('IntersectionObserver' in window) {
    const io = new IntersectionObserver((entries) => entries.forEach((e) => { if (e.isIntersecting) { e.target.classList.add('on'); io.unobserve(e.target); } }), { rootMargin: '0px 0px -8% 0px' });
    blocks.forEach((b) => { b.classList.add('reveal'); io.observe(b); });
  }
})();
