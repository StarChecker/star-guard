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

  // ---------- граф активности: зелёная органика и розовый всплеск ----------
  const contrib = $('#contrib');
  let seed = 7;
  const rnd = () => (seed = (seed * 16807) % 2147483647) / 2147483647;
  for (let i = 0; i < 26 * 7; i++) {
    const col = Math.floor(i / 7);
    const cell = document.createElement('i');
    cell.style.setProperty('--c', i);
    if (col >= 19 && col <= 21 && rnd() > 0.25) cell.className = 'hot';
    else { const v = rnd(); if (v > 0.55) cell.className = 'l' + Math.min(4, 1 + Math.floor((v - 0.55) * 9)); }
    contrib.append(cell);
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

  // ---------- адрес сервера, зашитый в сборку ----------
  const api = (window.STAR_GUARD_CONFIG && window.STAR_GUARD_CONFIG.apiBase) || 'http://localhost:8080';
  $('#apiBase').textContent = api;
  if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(api)) { $('#localHint').hidden = true; $('#localCmd').hidden = true; }

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
