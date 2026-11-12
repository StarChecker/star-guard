// Пасхалка: на странице самого Star Guard (StarChecker/star-guard) вместо оценки экран
// затягивает спиральная галактика из звёзд. Курсор раздвигает звёзды, клик — ударная волна,
// Esc или × — звёзды разлетаются и страница возвращается.
(function () {
  'use strict';

  const HOME = 'starchecker/star-guard';
  const PALETTE = ['#ffffff', '#fff3c4', '#ffd65a', '#ffb347', '#9ecbff', '#6fa8ff', '#c4a5ff', '#a77bff', '#ff8cc6', '#7df0c8'];
  const WEIGHTS = [5, 3, 4, 1, 3, 2, 3, 2, 2, 1];
  const ARMS = 2;
  const TWO_PI = Math.PI * 2;

  let active = null; // { host, stop }

  function isHome(repo) {
    return !!repo && repo.toLowerCase() === HOME;
  }

  // ---------- спрайты звёзд: пятиконечная звезда со свечением, по одной на цвет ----------

  const SPRITE = 64;
  let sprites = null;
  let nebulae = null;
  function buildSprites() {
    if (sprites) return sprites;
    sprites = PALETTE.map((color) => {
      const c = document.createElement('canvas');
      c.width = c.height = SPRITE;
      const g = c.getContext('2d');
      const m = SPRITE / 2;
      const glow = g.createRadialGradient(m, m, 0, m, m, m);
      glow.addColorStop(0, color);
      glow.addColorStop(0.18, hexA(color, 0.55));
      glow.addColorStop(0.45, hexA(color, 0.12));
      glow.addColorStop(1, hexA(color, 0));
      g.fillStyle = glow;
      g.fillRect(0, 0, SPRITE, SPRITE);
      // сама звезда
      g.beginPath();
      for (let i = 0; i < 10; i++) {
        const r = i % 2 === 0 ? SPRITE * 0.3 : SPRITE * 0.12;
        const a = -Math.PI / 2 + (i * Math.PI) / 5;
        g.lineTo(m + Math.cos(a) * r, m + Math.sin(a) * r);
      }
      g.closePath();
      g.fillStyle = color;
      g.shadowColor = color;
      g.shadowBlur = 12;
      g.fill();
      const coreGlow = g.createRadialGradient(m, m, 0, m, m, SPRITE * 0.14);
      coreGlow.addColorStop(0, 'rgba(255,255,255,.95)');
      coreGlow.addColorStop(1, 'rgba(255,255,255,0)');
      g.shadowBlur = 0;
      g.fillStyle = coreGlow;
      g.fillRect(0, 0, SPRITE, SPRITE);
      return c;
    });
    // туманности: мягкие цветные облака вдоль рукавов
    nebulae = ['#7c3aed', '#2f6bff', '#ff2e88', '#a77bff', '#00b8a9'].map((color) => {
      const c = document.createElement('canvas');
      c.width = c.height = SPRITE;
      const g = c.getContext('2d');
      const m = SPRITE / 2;
      const glow = g.createRadialGradient(m, m, 0, m, m, m);
      glow.addColorStop(0, hexA(color, 0.9));
      glow.addColorStop(0.4, hexA(color, 0.35));
      glow.addColorStop(1, hexA(color, 0));
      g.fillStyle = glow;
      g.fillRect(0, 0, SPRITE, SPRITE);
      return c;
    });
    return sprites;
  }

  function hexA(hex, alpha) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`;
  }

  function pickColor() {
    const total = WEIGHTS.reduce((a, b) => a + b, 0);
    let x = Math.random() * total;
    for (let i = 0; i < WEIGHTS.length; i++) {
      x -= WEIGHTS[i];
      if (x <= 0) return i;
    }
    return 0;
  }

  function gauss() {
    return (Math.random() + Math.random() + Math.random() - 1.5) / 1.5;
  }

  // ---------- галактика ----------

  function start() {
    if (active) return;
    const reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    const host = document.createElement('div');
    host.setAttribute('data-star-guard', 'easter');
    Object.assign(host.style, { position: 'fixed', inset: '0', zIndex: '2147483647' });
    const shadow = host.attachShadow({ mode: 'open' });
    shadow.innerHTML = `
      <style>
        :host { all: initial; }
        .wrap { position: fixed; inset: 0; cursor: crosshair; opacity: 0; transition: opacity .8s cubic-bezier(.2,.7,.2,1);
          background: radial-gradient(120% 120% at 50% 50%, #0d0a2e 0%, #06051a 55%, #020210 100%); }
        .wrap.on { opacity: 1; }
        .wrap.off { opacity: 0; transition-duration: .9s; }
        canvas { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
        .cap { position: absolute; left: 50%; bottom: 28px; transform: translateX(-50%); text-align: center; pointer-events: none;
          font: 500 13px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: rgba(236, 232, 255, .72);
          letter-spacing: .02em; opacity: 0; transition: opacity 1.2s 2.4s; text-shadow: 0 0 12px rgba(167, 123, 255, .6); }
        .wrap.on .cap { opacity: 1; }
        .cap b { color: #fff; font-weight: 600; }
        .cap small { display: block; color: rgba(236, 232, 255, .45); font-size: 11px; }
        .x { position: absolute; top: 16px; right: 16px; width: 36px; height: 36px; border-radius: 50%; cursor: pointer;
          border: 1px solid rgba(255, 255, 255, .25); background: rgba(255, 255, 255, .06); color: #fff; font: 20px/1 sans-serif;
          display: grid; place-items: center; backdrop-filter: blur(6px); transition: background .2s, transform .2s; }
        .x:hover { background: rgba(255, 255, 255, .16); transform: rotate(90deg); }
      </style>
      <div class="wrap">
        <canvas></canvas>
        <div class="cap"><b>★ Star Guard</b> не проверяет сам себя — здесь все звёзды настоящие<small>Esc — вернуться на страницу</small></div>
        <button class="x" type="button" aria-label="Закрыть" title="Закрыть (Esc)">×</button>
      </div>`;
    document.documentElement.append(host);
    const wrap = shadow.querySelector('.wrap');
    const canvas = shadow.querySelector('canvas');
    const ctx = canvas.getContext('2d');
    const spr = buildSprites();

    const prevOverflow = document.documentElement.style.overflow;
    document.documentElement.style.overflow = 'hidden';

    let W = 0, H = 0, cx = 0, cy = 0, maxR = 0, dpr = 1;
    let stars = [];
    const pointer = { x: -1e4, y: -1e4, on: false };
    const waves = [];   // ударные волны от кликов
    const meteors = []; // падающие звёзды
    let t0 = performance.now();
    let last = t0;
    let leaving = 0;   // момент начала ухода
    let raf = 0;

    function resize() {
      dpr = Math.min(window.devicePixelRatio || 1, 1.5);
      W = window.innerWidth;
      H = window.innerHeight;
      canvas.width = Math.round(W * dpr);
      canvas.height = Math.round(H * dpr);
      cx = W / 2;
      cy = H / 2;
      maxR = Math.hypot(W, H) / 2 * 1.08;
      const count = Math.round(Math.min(3800, Math.max(1600, (W * H) / 440)));
      if (stars.length !== count) stars = makeStars(count);
      if (reduced && wrap) frame(performance.now());
    }

    function makeStars(count) {
      const list = [];
      for (let i = 0; i < count; i++) {
        const nebula = Math.random() < 0.05;                     // облака газа вдоль рукавов
        const dust = !nebula && Math.random() < 0.2;             // фон между рукавами
        const u = Math.random();
        const r = dust ? Math.sqrt(u) * maxR : Math.pow(u, 0.75) * maxR;
        const arm = i % ARMS;
        const twist = 4.4 * Math.log(1 + r / 28);                 // логарифмическая спираль
        const spread = dust ? Math.random() * TWO_PI : gauss() * (0.24 + 0.6 * (1 - r / maxR)) * (nebula ? 0.95 : 0.8);
        const big = Math.random() < 0.035;
        list.push({
          r,
          a: arm * (TWO_PI / ARMS) + twist + spread,
          w: 0.34 / Math.pow(0.22 + r / maxR, 0.95),              // внутренние кружатся быстрее
          nebula,
          size: nebula ? 50 + Math.random() * 90 : big ? 10 + Math.random() * 14 : 1.6 + Math.pow(Math.random(), 2.2) * 6 + (1 - r / maxR) * 2.5,
          color: nebula ? Math.floor(Math.random() * 5) : pickColor(),
          alpha: nebula ? 0.05 + Math.random() * 0.07 : 0.5 + Math.random() * 0.5,
          tw: 1 + Math.random() * 3.5,
          ph: Math.random() * TWO_PI,
          spin: big ? (Math.random() - 0.5) * 1.6 : 0,
          rot: Math.random() * TWO_PI,
          delay: Math.random() * 0.9 + (r / maxR) * 0.5,          // влетают от краёв к центру волной
          from: maxR * (1.3 + Math.random() * 0.8),
          ox: 0, oy: 0,
        });
      }
      return list;
    }

    function easeOutCubic(x) { return 1 - Math.pow(1 - x, 3); }
    function easeInCubic(x) { return x * x * x; }

    function frame(now) {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      const t = (now - t0) / 1000;
      const out = leaving ? Math.min(1, (now - leaving) / 1100) : 0;

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.globalCompositeOperation = 'source-over';
      ctx.clearRect(0, 0, W, H);

      // ядро галактики
      const pulse = 0.85 + 0.15 * Math.sin(t * 1.7);
      const coreR = Math.min(W, H) * 0.32 * pulse * (1 + out * 2);
      const core = ctx.createRadialGradient(cx, cy, 0, cx, cy, coreR);
      core.addColorStop(0, `rgba(255, 244, 214, ${0.55 * (1 - out)})`);
      core.addColorStop(0.15, `rgba(255, 170, 220, ${0.32 * (1 - out)})`);
      core.addColorStop(0.45, `rgba(124, 58, 237, ${0.16 * (1 - out)})`);
      core.addColorStop(1, 'rgba(47, 107, 255, 0)');
      ctx.fillStyle = core;
      ctx.fillRect(0, 0, W, H);

      ctx.globalCompositeOperation = 'lighter';
      const swirl = Math.max(0, 1 - t / 2.2);           // в начале всё закручивается сильнее
      const pushR = 150, pushR2 = pushR * pushR;

      for (let i = 0; i < stars.length; i++) {
        const s = stars[i];
        s.a += s.w * dt * (1 + 5 * swirl * swirl);
        if (s.spin) s.rot += s.spin * dt;

        // влёт: по спирали снаружи к своему радиусу
        let k = Math.min(1, Math.max(0, (t - s.delay) / 1.6));
        let r = s.from + (s.r - s.from) * easeOutCubic(k);
        let extraA = (1 - easeOutCubic(k)) * 2.4;
        // уход: разлетаются наружу
        if (out) {
          const o = easeInCubic(out);
          r = r + o * maxR * (1.2 + s.r / maxR);
          extraA -= o * 1.2;
        }
        const ang = s.a + extraA;
        let x = cx + Math.cos(ang) * r;
        let y = cy + Math.sin(ang) * r * 0.92;

        // курсор раздвигает звёзды
        if (pointer.on) {
          const dx = x + s.ox - pointer.x, dy = y + s.oy - pointer.y;
          const d2 = dx * dx + dy * dy;
          if (d2 < pushR2 && d2 > 1) {
            const d = Math.sqrt(d2);
            const f = (1 - d / pushR) * 9;
            s.ox += (dx / d) * f;
            s.oy += (dy / d) * f;
          }
        }
        // ударные волны
        for (let j = 0; j < waves.length; j++) {
          const wv = waves[j];
          const dx = x + s.ox - wv.x, dy = y + s.oy - wv.y;
          const d = Math.sqrt(dx * dx + dy * dy) || 1;
          const band = Math.abs(d - wv.r);
          if (band < 60) {
            const f = (1 - band / 60) * 26 * (1 - wv.age);
            s.ox += (dx / d) * f;
            s.oy += (dy / d) * f;
          }
        }
        s.ox *= 0.93;
        s.oy *= 0.93;
        x += s.ox;
        y += s.oy;

        if (x < -40 || y < -40 || x > W + 40 || y > H + 40) continue;
        const appear = Math.min(1, k * 3);
        const twinkle = s.nebula ? 1 : 0.55 + 0.45 * Math.sin(t * s.tw + s.ph);
        const alpha = s.alpha * twinkle * appear * (1 - out);
        if (alpha <= 0.01) continue;
        ctx.globalAlpha = alpha;
        const size = s.nebula ? s.size * 2 : s.size * (1 + 0.25 * twinkle) * 2.4;
        if (s.nebula) {
          ctx.drawImage(nebulae[s.color], x - size / 2, y - size / 2, size, size);
        } else if (s.spin) {
          ctx.setTransform(dpr, 0, 0, dpr, dpr * x, dpr * y);
          ctx.rotate(s.rot);
          ctx.drawImage(spr[s.color], -size / 2, -size / 2, size, size);
          ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        } else {
          ctx.drawImage(spr[s.color], x - size / 2, y - size / 2, size, size);
        }
      }

      // падающие звёзды
      if (!out && t > 1.5 && Math.random() < dt * 0.6) {
        const fromLeft = Math.random() < 0.5;
        meteors.push({ x: fromLeft ? -50 : W + 50, y: Math.random() * H * 0.6, vx: (fromLeft ? 1 : -1) * (700 + Math.random() * 500), vy: 180 + Math.random() * 220, life: 1 });
      }
      for (let i = meteors.length - 1; i >= 0; i--) {
        const m = meteors[i];
        m.x += m.vx * dt; m.y += m.vy * dt; m.life -= dt * 0.7;
        if (m.life <= 0 || m.x < -200 || m.x > W + 200 || m.y > H + 200) { meteors.splice(i, 1); continue; }
        const len = 0.12;
        const grad = ctx.createLinearGradient(m.x, m.y, m.x - m.vx * len, m.y - m.vy * len);
        grad.addColorStop(0, `rgba(255, 255, 255, ${0.9 * m.life})`);
        grad.addColorStop(1, 'rgba(167, 123, 255, 0)');
        ctx.globalAlpha = 1;
        ctx.strokeStyle = grad;
        ctx.lineWidth = 2;
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(m.x, m.y);
        ctx.lineTo(m.x - m.vx * len, m.y - m.vy * len);
        ctx.stroke();
        ctx.drawImage(spr[0], m.x - 9, m.y - 9, 18, 18);
      }

      // кольца ударных волн
      for (let i = waves.length - 1; i >= 0; i--) {
        const wv = waves[i];
        wv.r += 900 * dt;
        wv.age += dt * 1.1;
        if (wv.age >= 1) { waves.splice(i, 1); continue; }
        ctx.globalAlpha = (1 - wv.age) * 0.5;
        ctx.strokeStyle = '#c4a5ff';
        ctx.lineWidth = 2 + 6 * (1 - wv.age);
        ctx.beginPath();
        ctx.arc(wv.x, wv.y, wv.r, 0, TWO_PI);
        ctx.stroke();
      }
      ctx.globalAlpha = 1;

      if (out >= 1) { teardown(); return; }
      if (!reduced) raf = requestAnimationFrame(frame);
    }

    function onMove(event) { pointer.x = event.clientX; pointer.y = event.clientY; pointer.on = true; }
    function onLeave() { pointer.on = false; }
    function onClick(event) {
      if (event.target !== canvas) return;
      waves.push({ x: event.clientX, y: event.clientY, r: 0, age: 0 });
      if (reduced) frame(performance.now());
    }
    function onKey(event) { if (event.key === 'Escape') leave(); }
    function onWheel(event) { event.preventDefault(); }

    function leave() {
      if (leaving) return;
      leaving = performance.now();
      wrap.classList.add('off');
      if (reduced) setTimeout(teardown, 900);
    }

    function teardown() {
      cancelAnimationFrame(raf);
      window.removeEventListener('resize', resize);
      window.removeEventListener('keydown', onKey, true);
      host.remove();
      document.documentElement.style.overflow = prevOverflow;
      active = null;
    }

    resize();
    window.addEventListener('resize', resize);
    window.addEventListener('keydown', onKey, true);
    wrap.addEventListener('pointermove', onMove);
    wrap.addEventListener('pointerleave', onLeave);
    wrap.addEventListener('click', onClick);
    wrap.addEventListener('wheel', onWheel, { passive: false });
    shadow.querySelector('.x').addEventListener('click', leave);

    requestAnimationFrame(() => wrap.classList.add('on'));
    if (reduced) {
      t0 = performance.now() - 5000; // сразу готовая галактика, без движения
      frame(performance.now());
    } else {
      raf = requestAnimationFrame(frame);
    }

    active = { host, stop: teardown, leave };
  }

  function stop() {
    if (active) active.stop();
  }

  window.StarGuardEaster = { isHome, start, stop, get active() { return !!active; } };
})();
