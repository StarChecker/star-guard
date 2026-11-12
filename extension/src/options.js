// Настройки: адрес сервера, период анализа, точки в списках.
(function () {
  'use strict';
  const Period = window.StarGuardPeriod;
  const DEFAULTS = { apiBase: 'http://localhost:8080', hours: 6, period: null, listDots: true };
  const UNIT_DAYS = { days: 1, months: 30, years: 365 };
  const $ = (id) => document.getElementById(id);
  const status = $('status');
  let shortHours = 6;   // короткое часовое окно для сводок индекса

  function show(text, kind) {
    status.textContent = text;
    status.className = kind || '';
  }

  function originPattern(apiBase) {
    const url = new URL(apiBase);
    return url.protocol + '//' + url.hostname + '/*';
  }

  function showCustom(on) {
    $('customPeriod').hidden = !on;
  }

  function fillCustom(period) {
    let unit = 'days';
    let value = period.days || 30;
    if (period.kind === 'hours') {
      unit = 'hours';
      value = period.hours || 6;
    } else if (value >= 365 && value % 365 === 0) {
      unit = 'years';
      value = value / 365;
    } else if (value >= 30 && value % 30 === 0) {
      unit = 'months';
      value = value / 30;
    }
    $('customLength').value = String(value);
    $('customUnit').value = unit;
    $('customTo').value = period.to || '';
    syncCustomTo();
  }

  /** Для часов дата окончания не поддерживается — окно GH Archive всегда заканчивается сейчас. */
  function syncCustomTo() {
    const hours = $('customUnit').value === 'hours';
    $('customTo').disabled = hours;
    if (hours) $('customTo').value = '';
    $('customLength').max = hours ? '24' : String(Period.MAX_DAYS);
  }

  async function load() {
    const stored = await chrome.storage.sync.get(DEFAULTS);
    shortHours = Period.clampHours(stored.hours, DEFAULTS.hours);
    $('apiBase').value = stored.apiBase;
    $('listDots').checked = stored.listDots !== false;

    const period = Period.normalize(stored.period, shortHours);
    const preset = Period.presetId(period);
    if (preset) {
      $('period').value = preset;
      showCustom(false);
    } else {
      $('period').value = 'custom';
      fillCustom(period.kind === 'days' ? period : { days: 30, to: null });
      showCustom(true);
    }
  }

  function customPeriod() {
    const length = Number($('customLength').value);
    if (!Number.isInteger(length) || length < 1) {
      show('Длина периода — целое число от 1', 'bad');
      return null;
    }
    if ($('customUnit').value === 'hours') {
      if (length > 24) {
        show('Для часов максимум — 24', 'bad');
        return null;
      }
      return { kind: 'hours', hours: length };
    }
    const days = Math.round(length * UNIT_DAYS[$('customUnit').value]);
    if (days < 1 || days > Period.MAX_DAYS) {
      show('Период — от 1 дня до 10 лет', 'bad');
      return null;
    }
    const to = $('customTo').value || null;
    if (to) {
      if (!Period.DATE_RE.test(to)) {
        show('Некорректная дата окончания', 'bad');
        return null;
      }
      const today = new Date().toISOString().slice(0, 10);
      if (to > today) {
        show('Дата окончания не может быть в будущем', 'bad');
        return null;
      }
    }
    return to ? { kind: 'days', days, to } : { kind: 'days', days };
  }

  async function save() {
    let apiBase;
    try {
      apiBase = new URL($('apiBase').value.trim()).toString().replace(/\/+$/, '');
    } catch (ignored) {
      show('Некорректный адрес сервера', 'bad');
      return false;
    }

    const choice = $('period').value;
    const period = choice === 'custom' ? customPeriod() : Period.fromPreset(choice);
    if (!period) {
      if (choice !== 'custom') show('Выберите период анализа', 'bad');
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
    shortHours = period.kind === 'hours' ? period.hours : shortHours;
    await chrome.storage.sync.set({
      apiBase,
      period,
      hours: shortHours,
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

  $('period').addEventListener('change', () => {
    const custom = $('period').value === 'custom';
    showCustom(custom);
    if (custom) syncCustomTo();
  });
  $('customUnit').addEventListener('change', syncCustomTo);
  $('save').addEventListener('click', save);
  $('ping').addEventListener('click', async () => { if (await save()) ping(); });
  load();
})();
