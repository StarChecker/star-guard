// Настройки: адрес сервера, окно, точки в списках.
(function () {
  'use strict';
  const DEFAULTS = { apiBase: 'http://localhost:8080', hours: 6, listDots: true };
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

  async function load() {
    const stored = await chrome.storage.sync.get(DEFAULTS);
    $('apiBase').value = stored.apiBase;
    $('hours').value = String(stored.hours);
    $('listDots').checked = stored.listDots !== false;
  }

  async function save() {
    let apiBase;
    try {
      apiBase = new URL($('apiBase').value.trim()).toString().replace(/\/+$/, '');
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
    await chrome.storage.sync.set({
      apiBase,
      hours: Number($('hours').value),
      listDots: $('listDots').checked,
    });
    show('Сохранено', 'ok');
    return true;
  }

  async function ping() {
    show('Проверяем…');
    const response = await new Promise((resolve) =>
      chrome.runtime.sendMessage({ type: 'ping' }, (r) => resolve(r || { ok: false })));
    show(response.ok ? 'Сервер отвечает' : 'Сервер недоступен: ' + (response.message || response.code), response.ok ? 'ok' : 'bad');
  }

  $('save').addEventListener('click', save);
  $('ping').addEventListener('click', async () => { if (await save()) ping(); });
  load();
})();
