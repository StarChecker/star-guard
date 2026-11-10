// Рендер всех состояний для дизайн-ревью.
(function () {
  'use strict';
  const UI = window.StarGuardUI;
  const THEME = window.StarGuardTheme;
  const S = window.STAR_GUARD_SAMPLES;
  const noop = () => {};
  const actions = { onScan: noop, onRetry: noop, onOpenOptions: noop };

  function el(tag, attrs, text) {
    const node = document.createElement(tag);
    Object.assign(node, attrs || {});
    if (text) node.textContent = text;
    return node;
  }

  function card(parent, state, options) {
    const slot = el('div', { className: 'slot box' });
    const host = el('div');
    host.style.display = 'block';
    slot.append(host);
    parent.append(slot);
    UI.renderCard(host.attachShadow({ mode: 'open' }), state, actions, options);
  }

  function badge(parent, state) {
    const host = el('span');
    host.style.display = 'inline-flex';
    parent.append(host);
    UI.renderBadge(host.attachShadow({ mode: 'open' }), state, noop);
  }

  function listRow(parent, repo) {
    const row = el('div', { className: 'list-row' });
    const name = el('a', { href: '#' }, repo);
    const meta = el('div');
    const stars = el('span', { className: 'stars' }, '★ ' + ((S.summaries[repo] && S.summaries[repo].stars) || 12));
    const dot = UI.listDot(S.summaries[repo]);
    if (dot) stars.prepend(dot);
    meta.append(stars, document.createTextNode('Обновлён сегодня'));
    row.append(name, meta);
    parent.append(row);
  }

  document.querySelectorAll('.col').forEach((col) => {
    const vars = col.dataset.theme === 'dark' ? THEME.DARK : THEME.LIGHT;
    for (const [key, value] of Object.entries(vars)) col.style.setProperty(key, value);
    col.style.colorScheme = col.dataset.theme;

    col.append(el('h2', {}, col.dataset.theme === 'dark' ? 'Тёмная тема' : 'Светлая тема'));
    col.append(el('h2', {}, 'Значок у кнопки Star'));
    const head = el('div', { className: 'repo-head' });
    for (const state of [{ kind: 'report', data: S.farm }, { kind: 'report', data: S.healthy },
      { kind: 'not_indexed', hours: 6 }, { kind: 'scanning', done: 2, total: 6 }, { kind: 'loading' }]) {
      const wrap = el('span', { className: 'repo-head' });
      wrap.append(el('span', { className: 'gh-btn' }, '☆ Star 1.2k'));
      badge(wrap, state);
      head.append(wrap);
    }
    col.append(head);

    col.append(el('h2', {}, 'Карточка в сайдбаре'));
    const grid = el('div', { className: 'grid' });
    card(grid, { kind: 'report', data: S.farm });
    card(grid, { kind: 'report', data: S.healthy });
    col.append(grid);

    col.append(el('h2', {}, 'Остальные состояния'));
    const states = el('div', { className: 'grid' });
    card(states, { kind: 'not_indexed', hours: 6 });
    card(states, { kind: 'scanning', done: 2, total: 6, hours: 6 });
    card(states, { kind: 'loading', hours: 6 });
    card(states, { kind: 'error', code: 'API_UNREACHABLE', apiBase: 'http://localhost:8080' });
    col.append(states);

    col.append(el('h2', {}, 'Точки в списках (Trending, поиск)'));
    const list = el('div', { className: 'slot', style: 'width: auto; max-width: 520px' });
    Object.keys(S.summaries).forEach((repo) => listRow(list, repo));
    col.append(list);
  });
})();
