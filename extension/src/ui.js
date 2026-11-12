// Star Guard — отрисовка: карточка, значок у кнопки Star, точки в списках.
// Общий модуль для content script, popup и preview. Только DOM API и textContent —
// данные из API никогда не попадают в innerHTML.
(function () {
  'use strict';

  const SVG_NS = 'http://www.w3.org/2000/svg';
  const Period = window.StarGuardPeriod;

  const LEVELS = {
    low: { label: 'низкий риск', color: 'var(--sg-success)' },
    medium: { label: 'средний риск', color: 'var(--sg-attention)' },
    high: { label: 'высокий риск', color: 'var(--sg-severe)' },
    critical: { label: 'критический риск', color: 'var(--sg-danger)' },
  };

  const CHECK_ORDER = ['burst', 'fresh_accounts', 'engagement', 'actor_overlap', 'circadian'];

  // Стили живут в Shadow DOM. CSS-переменные GitHub (Primer) наследуются сквозь shadow root,
  // поэтому светлая/тёмная тема и цвета совпадают с GitHub; fallback — для popup и preview.
  const STYLES = `
:host { all: initial; }
.sg {
  --sg-fg: var(--fgColor-default, var(--color-fg-default, #1f2328));
  --sg-muted: var(--fgColor-muted, var(--color-fg-muted, #59636e));
  --sg-border: var(--borderColor-default, var(--color-border-default, #d1d9e0));
  --sg-border-muted: var(--borderColor-muted, var(--color-border-muted, #d1d9e0b3));
  --sg-bg: var(--bgColor-default, var(--color-canvas-default, #ffffff));
  --sg-bg-muted: var(--bgColor-muted, var(--color-canvas-subtle, #f6f8fa));
  --sg-accent: var(--fgColor-accent, var(--color-accent-fg, #0969da));
  --sg-success: var(--fgColor-success, var(--color-success-fg, #1a7f37));
  --sg-attention: var(--fgColor-attention, var(--color-attention-fg, #9a6700));
  --sg-severe: var(--fgColor-severe, var(--color-severe-fg, #bc4c00));
  --sg-danger: var(--fgColor-danger, var(--color-danger-fg, #d1242f));
  --sg-neutral: var(--fgColor-muted, #818b98);
  font: 12px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif;
  color: var(--sg-fg);
  box-sizing: border-box;
}
.sg *, .sg *::before, .sg *::after { box-sizing: border-box; }
.sg a { color: var(--sg-accent); text-decoration: none; }
.sg a:hover { text-decoration: underline; }
.sg button {
  font: inherit; font-weight: 500; cursor: pointer; border-radius: 6px; padding: 3px 12px;
  border: 1px solid var(--sg-border); background: var(--sg-bg-muted); color: var(--sg-fg);
}
.sg button:hover { border-color: var(--sg-muted); }
.sg button.primary {
  background: var(--bgColor-success-emphasis, #1f883d); border-color: transparent; color: #fff;
}

/* карточка */
.card { padding: 16px 0; }
.card.floating {
  width: 320px; padding: 14px 16px; border: 1px solid var(--sg-border); border-radius: 12px;
  background: var(--sg-bg); box-shadow: 0 8px 24px rgba(31,35,40,.18);
  max-height: calc(100vh - 96px); overflow: auto;
}
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.head h2 { margin: 0; font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 6px; }
.head .window { color: var(--sg-muted); display: inline-flex; align-items: center; gap: 4px; }
.head select {
  font: inherit; color: var(--sg-muted); background: transparent; cursor: pointer;
  border: 1px solid transparent; border-radius: 6px; padding: 0 2px; margin: 0 -2px;
}
.head select:hover { border-color: var(--sg-border); background: var(--sg-bg-muted); }
.head select:focus-visible { outline: 2px solid var(--sg-accent); outline-offset: 1px; }
.head-right { display: inline-flex; align-items: center; gap: 8px; }
.logo { width: 16px; height: 16px; flex: none; }

.summary { display: flex; gap: 12px; align-items: center; }
.donut { flex: none; }
.donut .value { font-size: 18px; font-weight: 600; fill: var(--sg-fg); }
.donut .of { font-size: 9px; fill: var(--sg-muted); }
.verdict { font-size: 14px; font-weight: 600; line-height: 1.35; }
.meta { color: var(--sg-muted); margin-top: 2px; }
.pill {
  display: inline-block; padding: 0 7px; border-radius: 2em; font-weight: 500;
  border: 1px solid currentColor; line-height: 18px; font-size: 11px;
}

.section { margin-top: 16px; }
.section-title {
  display: flex; justify-content: space-between; align-items: baseline; gap: 8px;
  font-weight: 600; margin-bottom: 6px;
}
.section-title .aside { font-weight: 400; color: var(--sg-muted); }

.chart { display: block; width: 100%; height: auto; overflow: visible; }
.chart .bar-ok { fill: var(--sg-accent); opacity: .55; }
.chart .bar-bad { fill: var(--sg-danger); }
.chart .axis { fill: var(--sg-muted); font-size: 9px; }
.chart .grid { stroke: var(--sg-border-muted); stroke-width: 1; }
.chart .hit { fill: transparent; }
.chart .hit:hover { fill: var(--sg-fg); opacity: .06; }
.legend { display: flex; gap: 12px; color: var(--sg-muted); margin-top: 4px; flex-wrap: wrap; }
.legend i { display: inline-block; width: 8px; height: 8px; border-radius: 2px; margin-right: 4px; vertical-align: 0; }
.tooltip {
  position: fixed; z-index: 2147483647; pointer-events: none; padding: 4px 8px; border-radius: 6px;
  background: var(--bgColor-emphasis, #25292e); color: var(--fgColor-onEmphasis, #fff);
  font-size: 11px; white-space: nowrap; display: none;
}
.note { color: var(--sg-muted); font-size: 11px; margin-top: 4px; }

.checks { list-style: none; margin: 0; padding: 0; }
.check { padding: 7px 0; border-top: 1px solid var(--sg-border-muted); }
.check:first-child { border-top: 0; }
.check-row { display: flex; align-items: center; gap: 8px; }
.check-name { flex: 1; font-weight: 500; }
.check-tag {
  font-size: 10px; color: var(--sg-muted); border: 1px solid var(--sg-border);
  border-radius: 2em; padding: 0 6px; line-height: 16px;
}
.meter { flex: none; width: 64px; height: 6px; border-radius: 3px; background: var(--sg-border-muted); overflow: hidden; }
.meter > span { display: block; height: 100%; border-radius: 3px; }
.meter.off > span {
  background: repeating-linear-gradient(45deg, var(--sg-border), var(--sg-border) 3px, transparent 3px, transparent 6px) !important;
  width: 100% !important;
}
.check-score { flex: none; width: 28px; text-align: right; font-variant-numeric: tabular-nums; color: var(--sg-muted); }
.check-summary { color: var(--sg-muted); margin-top: 2px; }

details.evidence { margin-top: 12px; }
details.evidence summary { cursor: pointer; color: var(--sg-accent); list-style: none; }
details.evidence summary::-webkit-details-marker { display: none; }
details.evidence summary::before { content: '▸ '; }
details.evidence[open] summary::before { content: '▾ '; }
.evidence-list { margin: 6px 0 0; padding: 0; list-style: none; }
.evidence-list li { display: flex; justify-content: space-between; gap: 8px; padding: 2px 0; }
.evidence-list .num { color: var(--sg-muted); font-variant-numeric: tabular-nums; }

.footer { margin-top: 14px; color: var(--sg-muted); font-size: 11px; }
.warn { color: var(--sg-attention); }

.state { color: var(--sg-muted); }
.state p { margin: 0 0 10px; }
.tiles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 6px 0 4px; }
.tile { border: 1px solid var(--sg-border-muted); border-radius: 6px; padding: 5px 8px; }
.tile b { display: block; font-size: 14px; font-variant-numeric: tabular-nums; color: var(--sg-fg); }
.tile span { color: var(--sg-muted); font-size: 11px; line-height: 1.3; display: block; }
.progress { height: 6px; border-radius: 3px; background: var(--sg-border-muted); overflow: hidden; margin: 8px 0 4px; }
.progress > span { display: block; height: 100%; background: var(--sg-accent); transition: width .3s; }
.skeleton { background: var(--sg-border-muted); border-radius: 6px; animation: sg-pulse 1.2s ease-in-out infinite; }
@keyframes sg-pulse { 50% { opacity: .5; } }

/* значок у кнопки Star */
.badge {
  display: inline-flex; align-items: center; gap: 5px; height: 28px; padding: 0 10px;
  border: 1px solid var(--sg-border); border-radius: 6px; background: var(--sg-bg-muted);
  font-weight: 500; cursor: pointer; white-space: nowrap; color: var(--sg-fg);
}
.badge:hover { border-color: var(--sg-muted); }
.badge .dot { width: 8px; height: 8px; border-radius: 50%; flex: none; }
.badge .num { font-variant-numeric: tabular-nums; }

/* точка в списках */
.list-dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  margin-right: 4px; vertical-align: 0; cursor: help;
}
`;

  function h(tag, attrs, ...children) {
    const el = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
      if (value === undefined || value === null || value === false) continue;
      if (key === 'class') el.className = value;
      else if (key === 'style') el.setAttribute('style', value);
      else if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
      else el.setAttribute(key, value === true ? '' : value);
    }
    append(el, children);
    return el;
  }

  function s(tag, attrs, ...children) {
    const el = document.createElementNS(SVG_NS, tag);
    for (const [key, value] of Object.entries(attrs || {})) {
      if (value !== undefined && value !== null) el.setAttribute(key, value);
    }
    append(el, children);
    return el;
  }

  function append(el, children) {
    for (const child of children.flat()) {
      if (child === null || child === undefined || child === false) continue;
      el.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
  }

  function plural(n, one, few, many) {
    const m100 = Math.abs(n) % 100;
    const m10 = m100 % 10;
    if (m100 >= 11 && m100 <= 14) return many;
    if (m10 === 1) return one;
    if (m10 >= 2 && m10 <= 4) return few;
    return many;
  }

  function stars(n) {
    return n + ' ' + plural(n, 'звезда', 'звезды', 'звёзд');
  }

  function time(iso) {
    return new Date(iso).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
  }

  function day(iso) {
    return new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });
  }

  function windowLabel(hours) {
    if (!hours) return '';
    if (hours <= 48) return `${hours} ч`;
    const days = Math.max(1, Math.round(hours / 24));
    if (days <= 60) return `${days} ${plural(days, 'день', 'дня', 'дней')}`;
    if (days <= 730) {
      const months = Math.round(days / 30);
      return `${months} ${plural(months, 'месяц', 'месяца', 'месяцев')}`;
    }
    const years = Math.round(days / 365);
    return `${years} ${plural(years, 'год', 'года', 'лет')}`;
  }

  function periodFromWindow(hours) {
    if (!hours) return { kind: 'hours', hours: 6 };
    return hours <= 24 ? { kind: 'hours', hours } : { kind: 'days', days: Math.round(hours / 24) };
  }

  function periodLabel(period) {
    if (!period) return '';
    if (period.kind === 'all') return 'всё время';
    if (period.kind === 'days') {
      const base = windowLabel(period.days * 24);
      return period.to ? `${base} до ${day(period.to)}` : base;
    }
    return windowLabel(period.hours);
  }

  function scoreColor(score01) {
    if (score01 >= 0.8) return 'var(--sg-danger)';
    if (score01 >= 0.6) return 'var(--sg-severe)';
    if (score01 >= 0.3) return 'var(--sg-attention)';
    return 'var(--sg-success)';
  }

  function levelOf(report) {
    return LEVELS[report.score.level] || LEVELS.low;
  }

  function logo() {
    return s('svg', { class: 'logo', viewBox: '0 0 16 16', 'aria-hidden': 'true' },
      s('path', {
        d: 'M8 .8 2 3v4.6c0 3.6 2.5 6.6 6 7.6 3.5-1 6-4 6-7.6V3L8 .8Z',
        fill: 'none', stroke: 'currentColor', 'stroke-width': '1.4', 'stroke-linejoin': 'round',
      }),
      s('path', {
        d: 'm8 4.3 1.1 2.2 2.4.3-1.8 1.7.5 2.4L8 9.7l-2.2 1.2.5-2.4-1.8-1.7 2.4-.3L8 4.3Z',
        fill: 'currentColor',
      }));
  }

  function donut(report) {
    const value = report.score.insufficient_data ? null : report.score.score;
    const r = 26;
    const c = 2 * Math.PI * r;
    const level = levelOf(report);
    const filled = value === null ? 0 : Math.max(0.02, value / 100) * c;
    return s('svg', {
      class: 'donut', width: '68', height: '68', viewBox: '0 0 68 68', role: 'img',
      'aria-label': value === null ? 'Оценка недоступна' : `Оценка ${value} из 100`,
    },
      s('circle', { cx: 34, cy: 34, r, fill: 'none', stroke: 'var(--sg-border-muted)', 'stroke-width': 7 }),
      s('circle', {
        cx: 34, cy: 34, r, fill: 'none', stroke: level.color, 'stroke-width': 7,
        'stroke-linecap': 'round', 'stroke-dasharray': `${filled} ${c}`,
        transform: 'rotate(-90 34 34)',
      }),
      s('text', { x: 34, y: 37, 'text-anchor': 'middle', class: 'value' }, value === null ? '—' : String(value)),
      s('text', { x: 34, y: 48, 'text-anchor': 'middle', class: 'of' }, 'из 100'));
  }

  function timeline(report, period) {
    const tl = report.stars_timeline;
    if (!tl || !tl.points || tl.points.length === 0) return null;
    const points = tl.points;
    const W = 296, H = 92, top = 6, bottom = 16, left = 0;
    const plotH = H - top - bottom;
    const max = Math.max(1, ...points.map((p) => Math.max(p.stars, p.suspect_accounts || 0)));
    const step = (W - left) / points.length;
    const barW = Math.max(1, step - (step > 6 ? 2 : 1));
    const tooltip = h('div', { class: 'tooltip', role: 'tooltip' });
    const svg = s('svg', {
      class: 'chart', viewBox: `0 0 ${W} ${H}`, role: 'img',
      'aria-label': `Звёзды по времени: всего ${report.stats.stars}, подозрительных ${tl.suspicious_stars}`,
    });
    svg.append(s('line', { class: 'grid', x1: 0, x2: W, y1: top + plotH + 0.5, y2: top + plotH + 0.5 }));
    svg.append(s('line', { class: 'grid', x1: 0, x2: W, y1: top + 0.5, y2: top + 0.5, 'stroke-dasharray': '2 3' }));
    svg.append(s('text', { class: 'axis', x: W, y: top + 9, 'text-anchor': 'end' }, String(max)));

    points.forEach((p, i) => {
      const x = left + i * step;
      const total = (p.stars / max) * plotH;
      const bad = (p.suspicious / max) * plotH;
      const okH = Math.max(0, total - bad);
      if (okH > 0) svg.append(s('rect', { class: 'bar-ok', x, y: top + plotH - total + bad, width: barW, height: okH, rx: 1 }));
      if (bad > 0) svg.append(s('rect', { class: 'bar-bad', x, y: top + plotH - total, width: barW, height: bad, rx: 1 }));
      if (p.suspect_accounts > 0) svg.append(s('circle', {
        cx: x + barW / 2, cy: top + plotH - (p.suspect_accounts / max) * plotH,
        r: 2.5, fill: 'var(--sg-danger)', stroke: 'var(--sg-bg)', 'stroke-width': 1,
      }));
      const hit = s('rect', { class: 'hit', x, y: top, width: step, height: plotH });
      hit.addEventListener('mouseenter', (event) => {
        const end = new Date(new Date(p.at).getTime() + tl.bucket_minutes * 60000).toISOString();
        const span = tl.bucket_minutes >= 1440 ? `${day(p.at)}–${day(end)}`
          : tl.bucket_minutes >= 360 ? `${day(p.at)} ${time(p.at)}–${time(end)}`
            : `${time(p.at)}–${time(end)}`;
        tooltip.textContent = `${span}: ${stars(p.stars)}` +
          (p.suspicious ? `, подозрительных ${p.suspicious}` : '') +
          (p.suspect_accounts ? `, из пула ${p.suspect_accounts}` : '');
        tooltip.style.display = 'block';
        move(event);
      });
      hit.addEventListener('mousemove', move);
      hit.addEventListener('mouseleave', () => { tooltip.style.display = 'none'; });
      svg.append(hit);
    });

    const ticks = Math.min(4, points.length);
    for (let t = 0; t < ticks; t++) {
      const i = Math.round((t * (points.length - 1)) / Math.max(1, ticks - 1));
      const anchor = t === 0 ? 'start' : t === ticks - 1 ? 'end' : 'middle';
      const x = t === ticks - 1 ? W : left + i * step + step / 2;
      const label = tl.bucket_minutes >= 60 ? day(points[i].at) : time(points[i].at);
      svg.append(s('text', { class: 'axis', x, y: H - 3, 'text-anchor': anchor }, label));
    }

    function move(event) {
      tooltip.style.left = event.clientX + 12 + 'px';
      tooltip.style.top = event.clientY - 28 + 'px';
    }

    const share = report.stats.stars ? Math.round((tl.suspicious_stars / report.stats.stars) * 100) : 0;
    return h('div', { class: 'section' },
      h('div', { class: 'section-title' },
        h('span', {}, 'Звёзды за ' + (periodLabel(period) || windowLabel(report.window.hours))),
        h('span', { class: 'aside' }, `${report.stats.stars} · подозрительных ${tl.suspicious_stars} (${share}%)`)),
      svg,
      tooltip,
      h('div', { class: 'legend' },
        h('span', {}, h('i', { style: 'background: var(--sg-accent); opacity: .55' }), 'обычные'),
        h('span', {}, h('i', { style: 'background: var(--sg-danger)' }), 'подозрительные')),
      report.score.checks.some(c => c.id === 'suspect_accounts')
        ? h('div', { class: 'note' }, 'Красные точки — звёзды аккаунтов из подозрительного пула. Выборка неполная: последние 100 звёзд аккаунта. С оценкой всплесков не суммируются.')
        : null,
      tl.estimated
        ? h('div', { class: 'note' }, 'Подозрительные внутри часа распределены пропорционально: индекс хранит аккаунты по часам.')
        : null);
  }

  function checks(report) {
    const byId = Object.fromEntries(report.score.checks.map((c) => [c.id, c]));
    const ordered = CHECK_ORDER.map((id) => byId[id]).filter(Boolean)
      .concat(report.score.checks.filter((c) => !CHECK_ORDER.includes(c.id)));
    return h('div', { class: 'section' },
      h('div', { class: 'section-title' }, h('span', {}, 'Проверки')),
      h('ul', { class: 'checks' }, ordered.map((c) => {
        const mocked = c.details && c.details.mocked === true;
        const off = !c.active;
        const pct = Math.round(c.score * 100);
        return h('li', {
          class: 'check',
          title: `вес ${c.weight}, вклад ${Math.round(c.contribution * 100)}, уверенность ${Math.round(c.confidence * 100)}%`,
        },
          h('div', { class: 'check-row' },
            h('span', { class: 'check-name' }, c.title || c.id),
            mocked ? h('span', { class: 'check-tag' }, 'демо') : null,
            off && !mocked ? h('span', { class: 'check-tag' }, 'не участвует') : null,
            h('span', { class: 'meter' + (off ? ' off' : ''), 'aria-hidden': 'true' },
              h('span', { style: `width: ${Math.max(3, pct)}%; background: ${scoreColor(c.score)}` })),
            h('span', { class: 'check-score' }, off ? '—' : String(pct))),
          c.summary ? h('div', { class: 'check-summary' }, c.summary) : null);
      })));
  }

  function evidence(report) {
    const items = [];
    const overlap = report.score.checks.find((c) => c.id === 'actor_overlap');
    const tops = overlap && overlap.details && Array.isArray(overlap.details.top_overlaps)
      ? overlap.details.top_overlaps : [];
    if (tops.length > 0 && overlap.active) {
      items.push(h('div', { class: 'section-title' }, h('span', {}, 'Та же аудитория звёзд у')));
      items.push(h('ul', { class: 'evidence-list' }, tops.map((t) =>
        h('li', {},
          h('a', { href: 'https://github.com/' + encodeURI(t.repo), target: '_blank', rel: 'noopener' }, t.repo),
          h('span', { class: 'num' }, `${t.shared_actors} общих · ${Math.round(t.jaccard * 100)}%`)))));
    }
    const s2 = report.stats;
    items.push(h('div', { class: 'section-title', style: 'margin-top: 8px' }, h('span', {}, 'Статистика окна')));
    items.push(h('ul', { class: 'evidence-list' },
      row('Пик звёзд за 10 минут', s2.peak_stars_10m),
      row('Уникальных аккаунтов', s2.unique_actors),
      row('Форки / issues / PR', `${s2.forks} / ${s2.issues} / ${s2.pull_requests}`),
      row('Пуши', s2.pushes)));
    return h('details', { class: 'evidence' }, h('summary', {}, 'Подробнее'), ...items);

    function row(name, value) {
      return h('li', {}, h('span', {}, name), h('span', { class: 'num' }, String(value)));
    }
  }

  function periodPicker(period, actions) {
    const current = Period.presetId(period);
    const presets = Period.PRESETS.map((preset) =>
      h('option', { value: preset.id, selected: preset.id === current }, preset.short));
    if (!current) {
      // Период задан вручную в настройках — показываем его отдельной выбранной строкой.
      presets.unshift(h('option', { value: 'custom', selected: true }, periodLabel(period)));
    }
    presets.push(h('option', { value: 'custom-settings' }, 'свой период…'));
    const select = h('select', {
      title: 'Период анализа',
      'aria-label': 'Период анализа',
      onchange: (event) => {
        const value = event.target.value;
        if (value === 'custom-settings') {
          if (actions.onOpenOptions) actions.onOpenOptions();
          event.target.value = current || 'custom';
          return;
        }
        const next = Period.fromPreset(value);
        if (next && actions.onPeriodChange) actions.onPeriodChange(next);
      },
    }, presets);
    return h('span', { class: 'window' }, 'за ', select);
  }

  function header(period, floating, actions) {
    const onClose = actions && actions.onClose;
    const picker = period && actions && typeof actions.onPeriodChange === 'function'
      ? periodPicker(period, actions)
      : h('span', { class: 'window' }, period ? `за ${periodLabel(period)}` : '');
    return h('div', { class: 'head' },
      h('h2', {}, logo(), 'Star Guard'),
      h('span', { class: 'head-right' }, picker,
        floating && onClose ? h('button', { style: 'padding: 0 6px', title: 'Скрыть', onclick: onClose }, '×') : null));
  }

  function reportView(report, period) {
    const level = levelOf(report);
    const mockedCount = report.score.checks.filter((c) => c.details && c.details.mocked === true).length;
    const missing = report.archive && report.archive.missing_hours ? report.archive.missing_hours.length : 0;
    return [
      h('div', { class: 'summary' },
        donut(report),
        h('div', {},
          h('div', { class: 'verdict' }, report.score.verdict || ''),
          h('div', { class: 'meta' },
            h('span', { class: 'pill', style: `color: ${level.color}` }, level.label),
            ' · уверенность ' + Math.round(report.score.confidence * 100) + '%'))),
      timeline(report, period),
      checks(report),
      evidence(report),
      h('div', { class: 'footer' },
        sourceText(report),
        missing ? h('span', { class: 'warn' }, ` Пропущено часов: ${missing}.`) : null,
        mockedCount ? ` Проверок в демо-режиме: ${mockedCount}.` : null,
        ' Это оценка признаков, а не доказательство накрутки.'),
    ];
  }

  function sourceText(report) {
    const source = report.source || {};
    if (source.type === 'github_api') {
      const sampled = source.stars_sampled || report.stats.stars;
      const total = source.stars_total;
      return `История GitHub, ${rangeText(report)}: ${stars(sampled)}` +
        (total && total > sampled ? ` из ${total}` : '') + '. Пересечение аудиторий в этом режиме не считается.';
    }
    const where = source.type === 'gh_archive_index' ? 'индекс GH Archive' : 'GH Archive';
    return `Данные: ${where}, ${time(report.window.from)}–${time(report.window.to)}.`;
  }

  function rangeText(report) {
    return `${day(report.window.from)}–${day(report.window.to)}`;
  }

  function compact(n) {
    if (n === null || n === undefined) return '—';
    if (n >= 1e6) return (n / 1e6).toFixed(n >= 1e7 ? 0 : 1).replace('.', ',') + 'M';
    if (n >= 1e3) return (n / 1e3).toFixed(n >= 1e4 ? 0 : 1).replace('.', ',') + 'k';
    return String(n);
  }

  /** F5: базовая статистика GitHub, пока нет оценки (нет данных GH Archive). */
  function previewView(preview) {
    if (!preview) return null;
    const tile = (value, label) => h('div', { class: 'tile' }, h('b', {}, value), h('span', {}, label));
    return h('div', { class: 'section' },
      h('div', { class: 'section-title' },
        h('span', {}, 'Статистика GitHub'),
        h('span', { class: 'aside' }, 'без оценки')),
      h('div', { class: 'tiles' },
        tile(compact(preview.stars), 'звёзд'),
        tile(compact(preview.forks), 'форков'),
        tile(compact(preview.open_issues), 'открытых issues'),
        tile(compact(preview.contributors), 'контрибьюторов'),
        tile(preview.created_at ? new Date(preview.created_at).getFullYear() : '—', 'создан'),
        tile(preview.pushed_at ? day(preview.pushed_at) : '—', 'последний push')),
      h('div', { class: 'note' }, 'Нет данных GH Archive: это текущие счётчики GitHub, а не оценка накрутки.'),
      preview.archived ? h('div', { class: 'note warn' }, 'Репозиторий в архиве.') : null);
  }

  function stateView(state, actions) {
    const view = stateViewBody(state, actions);
    const preview = ['not_indexed', 'no_data', 'scanning', 'index_not_ready'].includes(state.kind)
      ? previewView(state.preview) : null;
    return preview ? view.concat([preview]) : view;
  }

  function stateViewBody(state, actions) {
    switch (state.kind) {
      case 'loading':
        return [
          h('div', { class: 'summary' },
            h('div', { class: 'skeleton', style: 'width: 68px; height: 68px; border-radius: 50%' }),
            h('div', { style: 'flex: 1' },
              h('div', { class: 'skeleton', style: 'height: 14px; width: 90%' }),
              h('div', { class: 'skeleton', style: 'height: 12px; width: 60%; margin-top: 8px' }))),
          h('div', { class: 'skeleton', style: 'height: 80px; margin-top: 16px' }),
        ];
      case 'not_indexed':
        return [h('div', { class: 'state' },
          h('p', {}, `Готовой оценки нет: репозиторий не входит в топ-1000 по звёздам за ${periodLabel(state.period)}.`),
          h('p', {}, 'Star Guard проверит его по истории звёзд GitHub за выбранный период — обычно это несколько секунд.'),
          h('button', { class: 'primary', onclick: actions.onScan }, 'Проверить'))];
      case 'scanning': {
        const pct = state.total ? Math.round((state.done / state.total) * 100) : 0;
        const github = state.source === 'github';
        return [h('div', { class: 'state' },
          h('p', {}, github ? 'Загружаем историю звёзд с GitHub…' : 'Проверяем по GH Archive…'),
          h('div', { class: 'progress' }, h('span', { style: `width: ${Math.max(4, pct)}%` })),
          h('div', { class: 'note' }, (github ? `Загружено страниц: ${state.done} из ${state.total}.` : `Обработано часов архива: ${state.done} из ${state.total}.`) +
            ' Можно уйти со страницы — результат сохранится.'))];
      }
      case 'no_data':
        return [h('div', { class: 'state' },
          h('p', {}, `За ${periodLabel(state.period)} у репозитория не было ни звёзд, ни других публичных событий — оценивать нечего.`),
          h('p', {}, 'Для небольших проектов это нормально. Накрутка видна по всплеску звёзд, поэтому стоит смотреть окно, где они были.'),
          state.period && state.period.kind === 'hours' && state.period.hours < 24 && actions.onScanDay
            ? h('button', { class: 'primary', onclick: actions.onScanDay }, 'Проверить за 24 ч')
            : null)];
      case 'gone':
        return [h('div', { class: 'state' },
          h('p', {}, 'Репозитория нет на GitHub: он удалён или скрыт.'),
          h('p', {}, 'Так часто заканчивают репозитории накрутки — GitHub удаляет их вместе с бот-аккаунтами.'))];
      case 'index_not_ready':
        return [h('div', { class: 'state' },
          h('p', {}, 'Сервер прогревает индекс популярных репозиториев. Обычно это пара минут.'),
          h('button', { onclick: actions.onRetry }, 'Повторить'))];
      case 'out_of_window':
        return [h('div', { class: 'state' },
          h('p', {}, `Период ${periodLabel(state.period)} не обслуживается индексом сервера.`),
          h('p', {}, 'Можно проверить репозиторий напрямую по GH Archive — это займёт 1–3 минуты.'),
          h('button', { class: 'primary', onclick: actions.onScan }, 'Проверить'))];
      case 'error':
      default:
        return [h('div', { class: 'state' },
          h('p', {}, state.code === 'API_UNREACHABLE'
            ? `Сервер Star Guard недоступен (${state.apiBase || 'адрес не задан'}).`
            : state.code === 'GITHUB_TOKEN_REQUIRED'
              ? 'Периоды длиннее 24 часов считаются по истории звёзд GitHub — на сервере не задан GITHUB_TOKEN.'
              : state.code === 'GITHUB_RATE_LIMITED'
                ? 'GitHub ограничил частоту запросов — попробуйте позже или выберите период покороче.'
                : state.code === 'GITHUB_HISTORY_NOT_READY'
                  ? 'GitHub ещё готовит историю звёзд этого репозитория — повторите через минуту.'
                  : `Не удалось получить оценку: ${state.message || state.code}.`),
          h('button', { onclick: actions.onRetry }, 'Повторить'), ' ',
          actions.onOpenOptions ? h('button', { onclick: actions.onOpenOptions }, 'Настройки') : null)];
    }
  }

  /**
   * Рисует карточку в shadow root.
   * state: { kind: 'report', data } | { kind: 'loading' | 'not_indexed' | 'scanning' | 'no_data' | 'index_not_ready' | 'error', ... }
   */
  function renderCard(shadowRoot, state, actions, options) {
    const opts = options || {};
    const hours = state.kind === 'report' ? state.data.window.hours : state.hours;
    const period = state.period || periodFromWindow(hours);
    const viewState = { ...state, period };
    const card = h('div', { class: 'sg card' + (opts.floating ? ' floating' : '') },
      header(period, opts.floating, actions),
      ...(state.kind === 'report' ? reportView(state.data, period) : stateView(viewState, actions)));
    shadowRoot.replaceChildren(h('style', {}, STYLES), card);
  }

  function renderBadge(shadowRoot, state, onClick) {
    let dotColor = 'var(--sg-neutral)';
    let text = '…';
    let title = 'Star Guard: загрузка';
    if (state.kind === 'report') {
      const report = state.data;
      const level = levelOf(report);
      dotColor = level.color;
      text = report.score.insufficient_data ? '—' : String(report.score.score);
      title = `Star Guard: ${report.score.verdict} (${text}/100)`;
    } else if (state.kind === 'not_indexed') {
      text = '?';
      title = 'Star Guard: нет готовой оценки — нажмите, чтобы проверить';
    } else if (state.kind === 'scanning') {
      text = `${state.done}/${state.total}`;
      title = 'Star Guard: идёт проверка';
    } else if (state.kind !== 'loading') {
      text = '—';
      title = 'Star Guard: оценка недоступна';
    }
    const badge = h('button', { class: 'sg badge', title, 'aria-label': title, onclick: onClick },
      h('span', { class: 'dot', style: `background: ${dotColor}` }),
      logo(),
      h('span', { class: 'num' }, text));
    shadowRoot.replaceChildren(h('style', {}, STYLES), badge);
  }

  /** Точка рядом со счётчиком звёзд в списках; null — ничего не рисовать. */
  function listDot(summary) {
    if (!summary || summary.status !== 'ok' || summary.score === null) return null;
    const level = LEVELS[summary.level] || LEVELS.low;
    const dot = document.createElement('span');
    dot.setAttribute('data-star-guard-dot', '');
    const shadow = dot.attachShadow({ mode: 'open' });
    const title = `Star Guard: ${summary.score}/100 — ${summary.verdict}`;
    shadow.replaceChildren(h('style', {}, STYLES),
      h('span', { class: 'sg list-dot', style: `background: ${level.color}`, title, 'aria-label': title }));
    return dot;
  }

  /** Список репозиториев индекса с оценкой — для popup на страницах без репозитория. */
  function renderTop(shadowRoot, state, onOpen) {
    let body;
    if (state.kind === 'loading') {
      body = [h('div', { class: 'skeleton', style: 'height: 120px' })];
    } else if (state.kind === 'error') {
      body = [h('div', { class: 'state' }, h('p', {}, state.message || 'Не удалось получить список.'))];
    } else {
      const rows = state.data.repos || [];
      body = [
        h('p', { class: 'state', style: 'margin: 0 0 8px' },
          `Популярные по звёздам за ${windowLabel(state.data.window.hours)}, только существующие на GitHub` +
          (state.data.skipped_deleted ? ` (удалённых пропущено: ${state.data.skipped_deleted})` : '') + '.'),
        h('ul', { class: 'evidence-list' }, rows.map((row) => {
          const level = LEVELS[row.level] || null;
          return h('li', {},
            h('span', {},
              h('span', { class: 'list-dot', style: `background: ${level ? level.color : 'var(--sg-neutral)'}`,
                title: row.verdict || '' }),
              h('a', { href: 'https://github.com/' + encodeURI(row.full_name), target: '_blank', rel: 'noopener',
                onclick: (event) => { if (onOpen) { event.preventDefault(); onOpen(row.full_name); } } }, row.full_name)),
            h('span', { class: 'num' }, `★ ${row.stars}` + (row.score === null || row.score === undefined ? '' : ` · ${row.score}`)));
        })),
        rows.length === 0 ? h('p', { class: 'state' }, 'Пока пусто: индекс ещё строится или фильтр слишком строгий.') : null,
      ];
    }
    shadowRoot.replaceChildren(h('style', {}, STYLES),
      h('div', { class: 'sg card' }, header(null, false), ...body));
  }

  window.StarGuardUI = { renderCard, renderBadge, listDot, renderTop, STYLES, LEVELS };
})();
