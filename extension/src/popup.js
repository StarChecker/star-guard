// Popup: оценка репозитория из активной вкладки.
(function () {
  'use strict';
  const UI = window.StarGuardUI;
  const root = document.getElementById('root');
  const shadow = root.attachShadow({ mode: 'open' });
  const REPO_RE = /^[A-Za-z0-9_.-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/;
  const RESERVED = new Set(['settings', 'orgs', 'marketplace', 'explore', 'topics', 'trending', 'search',
    'notifications', 'pulls', 'issues', 'sponsors', 'login', 'new', 'codespaces', 'features', 'users', 'stars']);
  let repo = null;
  let hours = 6;

  const send = (message) => new Promise((resolve) =>
    chrome.runtime.sendMessage(message, (response) => resolve(response || { ok: false, code: 'NO_RESPONSE' })));

  const actions = {
    onScan: () => scan(),
    onRetry: () => send({ type: 'clearCache' }).then(load),
    onOpenOptions: () => chrome.runtime.openOptionsPage(),
  };

  function render(state) {
    UI.renderCard(shadow, state, actions);
  }

  function toState(response) {
    if (response.ok) return { kind: 'report', data: response.data };
    if (response.code === 'REPO_NOT_INDEXED') return { kind: 'not_indexed', hours };
    if (response.code === 'REPO_NOT_FOUND_IN_WINDOW') return { kind: 'no_data', hours };
    if (response.code === 'INDEX_NOT_READY') return { kind: 'index_not_ready', hours };
    return { kind: 'error', hours, code: response.code, message: response.message, apiBase: response.apiBase };
  }

  async function load() {
    render({ kind: 'loading', hours });
    render(toState(await send({ type: 'report', repo, hours })));
  }

  async function scan() {
    render({ kind: 'scanning', done: 0, total: hours, hours });
    const started = await send({ type: 'scanStart', repo, hours });
    if (!started.ok) return render(toState(started));
    const poll = async () => {
      const status = await send({ type: 'scanStatus', id: started.data.id });
      if (!status.ok) return render(toState(status));
      const job = status.data;
      if (job.status === 'done') return render({ kind: 'report', data: job.report });
      if (job.status === 'failed') return render(toState({ ok: false, code: job.error_code, message: job.error_message }));
      render({ kind: 'scanning', done: job.hours_done, total: job.hours_total, hours });
      setTimeout(poll, 1500);
    };
    poll();
  }

  async function init() {
    const settings = await send({ type: 'settings' });
    if (settings.ok) hours = settings.data.hours;
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
      shadow.replaceChildren(Object.assign(document.createElement('style'), { textContent: UI.STYLES }),
        Object.assign(document.createElement('p'), {
          className: 'sg', textContent: 'Откройте страницу репозитория на GitHub — здесь появится оценка его звёзд.',
          style: 'color: var(--fgColor-muted); padding: 12px 0',
        }));
      return;
    }
    load();
  }

  init();
})();
