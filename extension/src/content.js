// Star Guard — content script для github.com.
// GitHub переходит между страницами без перезагрузки (Turbo), поэтому всё встраивание
// идемпотентно и перезапускается на событиях навигации и изменениях DOM.
(function () {
  'use strict';

  const UI = window.StarGuardUI;
  const MARK = 'data-star-guard';
  const RESERVED = new Set([
    'about', 'account', 'apps', 'codespaces', 'collections', 'contact', 'customer-stories', 'dashboard',
    'enterprise', 'enterprises', 'events', 'explore', 'features', 'github-copilot', 'issues', 'join',
    'login', 'logout', 'marketplace', 'new', 'notifications', 'orgs', 'organizations', 'pricing', 'pulls',
    'readme', 'search', 'security', 'settings', 'signup', 'site', 'sponsors', 'stars', 'team', 'topics',
    'trending', 'users',
  ]);
  const REPO_RE = /^[A-Za-z0-9_.-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/;

  let currentRepo = null;
  let cardState = { kind: 'loading' };
  let repoPreview = null;      // F5: базовая статистика GitHub, пока нет оценки
  let previewRequested = null; // для какого репозитория уже запрошен preview
  let settings = { hours: 6, listDots: true };
  let scanTimer = null;
  let floatingOpen = false;   // плавающая панель открыта значком
  let floatingClosed = false; // пользователь закрыл панель без значка
  const listCache = new Map();

  function send(message) {
    return new Promise((resolve) => {
      try {
        chrome.runtime.sendMessage(message, (response) => {
          if (chrome.runtime.lastError) {
            resolve({ ok: false, code: 'EXTENSION_ERROR', message: chrome.runtime.lastError.message });
          } else {
            resolve(response || { ok: false, code: 'NO_RESPONSE' });
          }
        });
      } catch (error) {
        // контекст расширения устарел (расширение перезагрузили) — молча выходим
        resolve({ ok: false, code: 'EXTENSION_ERROR', message: String(error) });
      }
    });
  }

  // ---------- какая страница ----------

  function repoFromPage() {
    const meta = document.querySelector('meta[name="octolytics-dimension-repository_nwo"]');
    if (meta && REPO_RE.test(meta.content)) {
      return meta.content;
    }
    const parts = location.pathname.split('/').filter(Boolean);
    if (parts.length >= 2 && !RESERVED.has(parts[0].toLowerCase())) {
      const candidate = parts[0] + '/' + parts[1];
      // без meta доверяем URL только при наличии шапки репозитория
      if (REPO_RE.test(candidate) && document.querySelector('#repository-container-header, [data-testid="repository-container-header"]')) {
        return candidate;
      }
    }
    return null;
  }

  // ---------- состояние карточки ----------

  function stateFromResponse(response, hours) {
    if (response.ok) return { kind: 'report', data: response.data };
    switch (response.code) {
      case 'REPO_NOT_INDEXED':
        return { kind: 'not_indexed', hours };
      case 'REPO_NOT_FOUND_IN_WINDOW':
        return { kind: 'no_data', hours };
      case 'REPO_NOT_FOUND_ON_GITHUB':
        return { kind: 'gone', hours };
      case 'INDEX_NOT_READY':
        return { kind: 'index_not_ready', hours };
      case 'INVALID_REQUEST':
        return { kind: 'out_of_window', hours, message: response.message };
      default:
        return { kind: 'error', hours, code: response.code, message: response.message, apiBase: response.apiBase };
    }
  }

  async function loadRepo(repo) {
    const hours = settings.hours;
    cardState = { kind: 'loading', hours };
    renderRepo();
    const pending = await scanFor(repo);
    if (pending) {
      followScan(repo, pending.id, hours);
      return;
    }
    const response = await send({ type: 'report', repo, hours });
    if (repo !== currentRepo) return;
    cardState = stateFromResponse(response, hours);
    renderRepo();
  }

  async function scanFor(repo) {
    const key = 'scan:' + repo.toLowerCase();
    try {
      const stored = await chrome.storage.session.get(key);
      return stored[key] || null;
    } catch (ignored) {
      return null;
    }
  }

  async function startScan(requestedHours) {
    const repo = currentRepo;
    const hours = requestedHours || settings.hours;
    cardState = { kind: 'scanning', done: 0, total: hours, hours };
    renderRepo();
    const response = await send({ type: 'scanStart', repo, hours });
    if (response.ok) {
      cardState = { kind: 'scanning', done: 0, total: response.data.hours_total, hours, source: response.data.source };
      renderRepo();
    }
    if (!response.ok) {
      cardState = stateFromResponse(response, hours);
      renderRepo();
      return;
    }
    try {
      await chrome.storage.session.set({ ['scan:' + repo.toLowerCase()]: { id: response.data.id } });
    } catch (ignored) {
      // session storage недоступен в старых версиях — просто не переживём перезагрузку
    }
    followScan(repo, response.data.id, hours);
  }

  function followScan(repo, id, scanHours) {
    let hours = scanHours;
    clearTimeout(scanTimer);
    const tick = async () => {
      if (repo !== currentRepo) return;
      const response = await send({ type: 'scanStatus', id });
      if (repo !== currentRepo) return;
      if (!response.ok) {
        await forgetScan(repo);
        cardState = response.code === 'SCAN_NOT_FOUND' ? { kind: 'not_indexed', hours } : stateFromResponse(response, hours);
        renderRepo();
        return;
      }
      const scan = response.data;
      // для истории GitHub шаги — это страницы, а окно — 30 дней
      hours = scan.source === 'github' ? 720 : (scan.hours_total || hours);
      if (scan.status === 'done') {
        await forgetScan(repo);
        cardState = { kind: 'report', data: scan.report };
      } else if (scan.status === 'failed') {
        await forgetScan(repo);
        cardState = stateFromResponse({ ok: false, code: scan.error_code, message: scan.error_message }, hours);
      } else {
        cardState = { kind: 'scanning', done: scan.hours_done, total: scan.hours_total, hours, source: scan.source };
        scanTimer = setTimeout(tick, 1500);
      }
      renderRepo();
    };
    tick();
  }

  async function forgetScan(repo) {
    try {
      await chrome.storage.session.remove('scan:' + repo.toLowerCase());
    } catch (ignored) {
      // нет session storage — нечего чистить
    }
  }

  const actions = {
    onScan: () => startScan(),
    onScanDay: () => startScan(24),
    onRetry: () => { send({ type: 'clearCache' }).then(() => loadRepo(currentRepo)); },
    onOpenOptions: () => window.open(chrome.runtime.getURL('options.html'), '_blank'),
    onWindowChange: (hours) => changeWindow(hours),
    onClose: () => { floatingOpen = false; floatingClosed = true; renderRepo(); },
  };

  // Смена окна из карточки: сразу применяем на странице и сохраняем в настройки,
  // чтобы то же окно использовали popup и точки в списках.
  async function changeWindow(value) {
    const hours = Math.min(24, Math.max(1, Math.round(Number(value))));
    if (!Number.isFinite(hours) || hours === settings.hours) return;
    settings = { ...settings, hours };
    listCache.clear();
    document.querySelectorAll('[data-star-guard-dot]').forEach((dot) => dot.remove());
    document.querySelectorAll('[data-star-guard-done]').forEach((link) => link.removeAttribute('data-star-guard-done'));
    scheduleListDots();
    if (currentRepo) loadRepo(currentRepo);
    try {
      await chrome.storage.sync.set({ hours });
    } catch (ignored) {
      // не удалось сохранить — окно всё равно применилось к текущей странице
    }
  }

  // ---------- встраивание на странице репозитория ----------

  function host(kind, display) {
    const el = document.createElement('div');
    el.setAttribute(MARK, kind);
    el.style.display = display;
    el.attachShadow({ mode: 'open' });
    return el;
  }

  function badgeAnchor() {
    const counter = document.querySelector('#repo-stars-counter-star, #repo-stars-counter-unstar');
    if (counter) {
      const group = counter.closest('li') || counter.closest('.BtnGroup, .starring-container');
      if (group) return group;
    }
    const stargazers = document.querySelector('#repository-container-header a[href$="/stargazers"]');
    return stargazers ? stargazers.closest('li') || stargazers : null;
  }

  function cardAnchor() {
    const grid = document.querySelector('.Layout-sidebar .BorderGrid');
    if (grid) return { el: grid, mode: 'grid' };
    const sidebar = document.querySelector('.Layout-sidebar');
    return sidebar ? { el: sidebar, mode: 'sidebar' } : null;
  }

  function ensureRepoUi() {
    let badge = document.querySelector(`[${MARK}="badge"]`);
    if (!badge) {
      const anchor = badgeAnchor();
      if (anchor) {
        badge = host('badge', 'inline-flex');
        badge.style.marginLeft = '8px';
        badge.style.verticalAlign = 'middle';
        if (anchor.tagName === 'LI') {
          const li = document.createElement('li');
          li.setAttribute(MARK, 'badge-li');
          li.append(badge);
          anchor.after(li);
        } else {
          anchor.after(badge);
        }
      }
    }

    let card = document.querySelector(`[${MARK}="card"]`);
    if (!card) {
      const anchor = cardAnchor();
      if (anchor) {
        card = host('card', 'block');
        if (anchor.mode === 'grid') {
          const row = document.createElement('div');
          row.className = 'BorderGrid-row';
          row.setAttribute(MARK, 'card-row');
          const cell = document.createElement('div');
          cell.className = 'BorderGrid-cell';
          cell.append(card);
          row.append(cell);
          const about = anchor.el.querySelector('.BorderGrid-row');
          if (about) about.after(row); else anchor.el.prepend(row);
        } else {
          anchor.el.prepend(card);
        }
      }
    }

    // Нет сайдбара (узкое окно, другая вёрстка) — плавающая панель по клику на значок.
    let floating = document.querySelector(`[${MARK}="floating"]`);
    if (!card && !floating) {
      floating = host('floating', 'none');
      Object.assign(floating.style, { position: 'fixed', right: '16px', bottom: '16px', zIndex: '100' });
      document.body.append(floating);
    }
    return { badge, card, floating };
  }

  function renderRepo() {
    const { badge, card, floating } = ensureRepoUi();
    if (badge) {
      UI.renderBadge(badge.shadowRoot, cardState, () => {
        if (cardState.kind === 'not_indexed') {
          startScan();
          return;
        }
        if (card) {
          card.scrollIntoView({ behavior: 'smooth', block: 'center' });
        } else if (floating) {
          floatingOpen = !floatingOpen;
          renderRepo();
        }
      });
    }
    if (card) {
      UI.renderCard(card.shadowRoot, withPreview(), actions);
    }
    if (floating) {
      const show = badge ? floatingOpen : !floatingClosed;
      floating.style.display = show ? 'block' : 'none';
      UI.renderCard(floating.shadowRoot, withPreview(), actions, { floating: true });
    }
  }

  /** Пока оценки нет, подмешиваем в карточку preview GitHub (запрашивается один раз на репозиторий). */
  function withPreview() {
    const waiting = ['not_indexed', 'no_data', 'scanning', 'index_not_ready'].includes(cardState.kind);
    if (!waiting || !currentRepo) return cardState;
    if (previewRequested !== currentRepo) {
      previewRequested = currentRepo;
      const repo = currentRepo;
      send({ type: 'preview', repo }).then((response) => {
        if (repo !== currentRepo || !response.ok) return;
        repoPreview = response.data;
        renderRepo();
      });
    }
    return repoPreview ? { ...cardState, preview: repoPreview } : cardState;
  }

  function removeRepoUi() {
    document.querySelectorAll(`[${MARK}="badge"], [${MARK}="badge-li"], [${MARK}="card"], [${MARK}="card-row"], [${MARK}="floating"]`)
      .forEach((el) => el.remove());
  }

  // ---------- точки в списках (Trending, поиск, звёзды, репозитории организации) ----------

  function repoFromStargazersLink(link) {
    let url;
    try {
      url = new URL(link.href, location.origin);
    } catch (ignored) {
      return null;
    }
    if (url.origin !== location.origin) return null;
    const parts = url.pathname.split('/').filter(Boolean);
    if (parts.length !== 3 || parts[2] !== 'stargazers') return null;
    const repo = parts[0] + '/' + parts[1];
    return REPO_RE.test(repo) ? repo : null;
  }

  let listTimer = null;
  function scheduleListDots() {
    clearTimeout(listTimer);
    listTimer = setTimeout(listDots, 400);
  }

  async function listDots() {
    if (!settings.listDots) return;
    const links = [...document.querySelectorAll('a[href$="/stargazers"]:not([data-star-guard-done])')]
      .filter((link) => !link.closest('#repository-container-header'));
    const byRepo = new Map();
    for (const link of links) {
      const repo = repoFromStargazersLink(link);
      if (!repo || (currentRepo && repo.toLowerCase() === currentRepo.toLowerCase())) continue;
      link.setAttribute('data-star-guard-done', '');
      if (!byRepo.has(repo)) byRepo.set(repo, []);
      byRepo.get(repo).push(link);
    }
    if (byRepo.size === 0) return;

    const unknown = [...byRepo.keys()].filter((repo) => !listCache.has(repo.toLowerCase()));
    if (unknown.length > 0) {
      const response = await send({ type: 'batch', repos: unknown, hours: settings.hours });
      if (!response.ok) return;
      for (const repo of unknown) {
        listCache.set(repo.toLowerCase(), response.data[repo.toLowerCase()] || null);
      }
    }
    for (const [repo, repoLinks] of byRepo) {
      const summary = listCache.get(repo.toLowerCase());
      for (const link of repoLinks) {
        const dot = UI.listDot(summary);
        if (dot) link.prepend(dot);
      }
    }
  }

  // ---------- навигация ----------

  let lastPath = null;
  function onPage() {
    const repo = repoFromPage();
    if (location.pathname !== lastPath || repo !== currentRepo) {
      lastPath = location.pathname;
      clearTimeout(scanTimer);
      if (repo !== currentRepo) {
        removeRepoUi();
        repoPreview = null;
        previewRequested = null;
        floatingOpen = false;
        floatingClosed = false;
        currentRepo = repo;
        if (repo) loadRepo(repo);
      }
    }
    if (currentRepo) {
      const missing = !document.querySelector(`[${MARK}="badge"]`) || !document.querySelector(`[${MARK}="card"], [${MARK}="floating"]`);
      if (missing) renderRepo();
    }
    scheduleListDots();
  }

  let observerTimer = null;
  const observer = new MutationObserver(() => {
    clearTimeout(observerTimer);
    observerTimer = setTimeout(onPage, 250);
  });

  async function init() {
    const response = await send({ type: 'settings' });
    if (response.ok) settings = response.data;
    ['turbo:load', 'turbo:render', 'pjax:end', 'popstate'].forEach((event) =>
      document.addEventListener(event, () => setTimeout(onPage, 50)));
    window.addEventListener('popstate', () => setTimeout(onPage, 50));
    observer.observe(document.body, { childList: true, subtree: true });
    chrome.storage.onChanged.addListener(async (changes, area) => {
      if (area !== 'sync') return;
      const fresh = await send({ type: 'settings' });
      if (!fresh.ok) return;
      // Свою смену окна content script уже применил и перерисовал — не грузим повторно.
      const changed = fresh.data.hours !== settings.hours
        || fresh.data.listDots !== settings.listDots
        || fresh.data.apiBase !== settings.apiBase;
      settings = fresh.data;
      if (!changed) return;
      listCache.clear();
      if (currentRepo) loadRepo(currentRepo);
    });
    onPage();
  }

  init();
})();
