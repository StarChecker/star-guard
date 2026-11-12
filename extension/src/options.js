// Настройки: адрес сервера, окно, точки в списках.
// Адрес сервера: пусто — «автоматически» (server.json из репозитория, иначе запасной из config.js),
// своё значение — перекрывает оба.
(function () {
  'use strict';
  const CONFIG = window.STAR_GUARD_CONFIG;
  const DEFAULTS = { apiBase: '', hours: 6, listDots: true };
  const SOURCES = { settings: 'из настроек', remote: 'из server.json в репозитории', default: 'запасной адрес сборки' };
  const $ = (id) => document.getElementById(id);
  const status = $('status');

  function show(text, kind) {
    status.textContent = text;
    status.className = kind || '';
  }

  function originPattern(apiBase) {
    const url = new URL(apiBase);
    return url.protocol + '//' + url.hostname + '/*';
  }

  function send(message) {
    return new Promise((resolve) => chrome.runtime.sendMessage(message, (r) => resolve(r || { ok: false })));
  }

  async function refreshInfo(force) {
    const info = await send({ type: 'serverInfo', force: !!force });
    if (!info.ok) return;
    $('apiCurrent').textContent = info.apiBase;
    $('apiSource').textContent = SOURCES[info.source] || info.source;
    if (info.source !== 'settings') $('apiBase').placeholder = 'автоматически: ' + info.apiBase;
  }

  async function load() {
    $('remoteLink').href = CONFIG.remoteConfigUrl.replace('https://raw.githubusercontent.com/', 'https://github.com/').replace(/\/([^/]+)\/server\.json$/, '/blob/$1/server.json');
    const stored = await chrome.storage.sync.get(DEFAULTS);
    const own = stored.apiBase && stored.apiBase !== CONFIG.apiBase ? stored.apiBase : '';
    $('apiBase').value = own;
    $('hours').value = String(stored.hours);
    $('listDots').checked = stored.listDots !== false;
    await refreshInfo(false);
  }

  async function save() {
    const raw = $('apiBase').value.trim();
    let apiBase = '';
    if (raw) {
      try {
        apiBase = new URL(raw).toString().replace(/\/+$/, '');
      } catch (ignored) {
        show('Некорректный адрес сервера', 'bad');
        return false;
      }
      const pattern = originPattern(apiBase);
      const local = /^https?:\/\/(localhost|127\.0\.0\.1)\//.test(pattern);
      if (!local) {
        const granted = await chrome.permissions.request({ origins: [pattern] });
        if (!granted) {
          show('Без разрешения расширение не сможет обращаться к ' + new URL(apiBase).host, 'bad');
          return false;
        }
      }
    }
    await chrome.storage.sync.set({
      apiBase,
      hours: Number($('hours').value),
      listDots: $('listDots').checked,
    });
    await refreshInfo(true);
    show('Сохранено', 'ok');
    return true;
  }

  async function ping() {
    show('Проверяем…');
    const response = await send({ type: 'ping' });
    show(response.ok ? 'Сервер отвечает' : 'Сервер недоступен: ' + (response.message || response.code), response.ok ? 'ok' : 'bad');
  }

  $('reset').addEventListener('click', () => {
    $('apiBase').value = '';
    show('Адрес будет браться автоматически — нажмите «Сохранить»');
  });
  $('save').addEventListener('click', save);
  $('ping').addEventListener('click', async () => { if (await save()) ping(); });
  load();
})();
