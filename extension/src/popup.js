// Popup: оценка репозитория из активной вкладки.
(function () {
  'use strict';
  const UI = window.StarGuardUI;
  const Period = window.StarGuardPeriod;
  const root = document.getElementById('root');
  const shadow = root.attachShadow({ mode: 'open' });
  const REPO_RE = /^[A-Za-z0-9_.-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/;
  const RESERVED = new Set(['settings', 'orgs', 'marketplace', 'explore', 'topics', 'trending', 'search',
    'notifications', 'pulls', 'issues', 'sponsors', 'login', 'new', 'codespaces', 'features', 'users', 'stars']);
  let repo = null;
  let hours = 6;
  let period = { kind: 'hours', hours: 6 };

  const send = (message) => new Promise((resolve) =>
    chrome.runtime.sendMessage(message, (response) => resolve(response || { ok: false, code: 'NO_RESPONSE' })));

  const actions = {
    onScan: () => scan(period),
    onScanDay: () => scan({ kind: 'hours', hours: 24 }),
    onRetry: () => send({ type: 'clearCache' }).then(load),
    onOpenOptions: () => chrome.runtime.openOptionsPage(),
    onPeriodChange: (next) => changePeriod(next),
  };

  // Период из заголовка карточки — сохраняем в те же настройки, что и страница настроек.
  async function changePeriod(value) {
    const next = Period.normalize(value, hours);
    if (Period.key(next) === Period.key(period)) return;
    period = next;
    hours = Period.indexHours(next, hours);
    try {
      await chrome.storage.sync.set({ period, hours });
    } catch (ignored) {
      // не сохранилось — период применится только в этом popup
    }
    load();
  }

  let preview = null;
  let previewRequested = false;

  function render(state) {
    const waiting = ['not_indexed', 'no_data', 'scanning', 'index_not_ready'].includes(state.kind);
    if (waiting && !previewRequested) {
      previewRequested = true;
      send({ type: 'preview', repo }).then((response) => {
        if (!response.ok) return;
        preview = response.data;
        render(lastState);
      });
    }
    lastState = state;
    UI.renderCard(shadow, waiting && preview ? { ...state, preview } : state, actions);
  }
  let lastState = { kind: 'loading' };

  function toState(response, scanPeriod) {
    const label = scanPeriod || period;
    if (response.ok) return { kind: 'report', data: response.data, period: label };
    if (response.code === 'REPO_NOT_INDEXED') return { kind: 'not_indexed', period: label };
    if (response.code === 'REPO_NOT_FOUND_IN_WINDOW') return { kind: 'no_data', period: label };
    if (response.code === 'REPO_NOT_FOUND_ON_GITHUB') return { kind: 'gone', period: label };
    if (response.code === 'INDEX_NOT_READY') return { kind: 'index_not_ready', period: label };
    if (response.code === 'INVALID_REQUEST') return { kind: 'out_of_window', period: label, message: response.message };
    return { kind: 'error', period: label, code: response.code, message: response.message, apiBase: response.apiBase };
  }

  async function load() {
    render({ kind: 'loading', period });
    render(toState(await send({ type: 'report', repo, period }), period));
  }

  async function scan(requested) {
    const scanPeriod = typeof requested === 'number'
      ? { kind: 'hours', hours: Period.clampHours(requested, hours) }
      : Period.normalize(requested || period, hours);
    render({ kind: 'scanning', done: 0, total: scanPeriod.kind === 'hours' ? scanPeriod.hours : 0, period: scanPeriod });
    const started = await send({ type: 'scanStart', repo, period: scanPeriod });
    if (!started.ok) return render(toState(started, scanPeriod));
    const poll = async () => {
      const status = await send({ type: 'scanStatus', id: started.data.id });
      if (!status.ok) return render(toState(status, scanPeriod));
      const job = status.data;
      if (job.status === 'done') return render({ kind: 'report', data: job.report, period: scanPeriod });
      if (job.status === 'failed') {
        return render(toState({ ok: false, code: job.error_code, message: job.error_message }, scanPeriod));
      }
      render({ kind: 'scanning', done: job.hours_done, total: job.hours_total, period: scanPeriod, source: job.source });
      setTimeout(poll, 1500);
    };
    poll();
  }

  async function init() {
    const settings = await send({ type: 'settings' });
    if (settings.ok) {
      hours = settings.data.hours;
      period = settings.data.period || period;
    }
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    try {
      const url = new URL(tab.url);
      const parts = url.pathname.split('/').filter(Boolean);
      const candidate = parts.slice(0, 2).join('/');
      if (url.hostname === 'github.com' && parts.length >= 2 && !RESERVED.has(parts[0]) && REPO_RE.test(candidate)) {
        repo = candidate;
      }
    } catch (ignored) {
      repo = null;
    }
    if (!repo) {
      // Не страница репозитория: показываем живые репозитории индекса с оценкой — с чего начать.
      const open = (fullName) => chrome.tabs.update(tab.id, { url: 'https://github.com/' + fullName });
      UI.renderTop(shadow, { kind: 'loading' }, open);
      const top = await send({ type: 'top', limit: 15 });
      UI.renderTop(shadow, top.ok ? { kind: 'top', data: top.data }
        : { kind: 'error', message: top.code === 'API_UNREACHABLE' ? 'Сервер Star Guard недоступен.' : (top.message || top.code) }, open);
      return;
    }
    load();
  }

  init();
})();
