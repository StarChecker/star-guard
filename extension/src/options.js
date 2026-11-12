// Настройки: адрес сервера, период анализа, точки в списках.
// Адрес сервера: пусто — «автоматически» (server.json из репозитория, иначе запасной из config.js),
// своё значение — перекрывает оба.
(function () {
  'use strict';
  const CONFIG = window.STAR_GUARD_CONFIG;
  const Period = window.StarGuardPeriod;
  const DEFAULTS = { apiBase: '', hours: 6, period: null, listDots: true };
  const SOURCES = { settings: 'из настроек', remote: 'из server.json в репозитории', default: 'запасной адрес сборки' };
  const DAY_MS = 24 * 60 * 60 * 1000;
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

  function showCustom(on) {
    $('customPeriod').hidden = !on;
  }

  function todayIso() {
    return new Date().toISOString().slice(0, 10);
  }

  function isoDaysAgo(days) {
    const today = Date.parse(todayIso() + 'T00:00:00Z');
    return new Date(today - (days - 1) * DAY_MS).toISOString().slice(0, 10);
  }

  /** Сколько дней назад была дата, включая сегодняшний день: 1 — сегодня. */
  function daysAgo(iso) {
    const at = Date.parse(iso + 'T00:00:00Z');
    if (!Number.isFinite(at)) return null;
    return Math.floor((Date.parse(todayIso() + 'T00:00:00Z') - at) / DAY_MS) + 1;
  }

  function daysWord(n) {
    const m100 = n % 100;
    const m10 = n % 10;
    if (m100 >= 11 && m100 <= 14) return 'дней';
    if (m10 === 1) return 'день';
    if (m10 >= 2 && m10 <= 4) return 'дня';
    return 'дней';
  }

  function fillCustom(period) {
    const days = period.kind === 'days' ? period.days : 30;
    $('customFrom').value = isoDaysAgo(Math.min(Period.MAX_DAYS, Math.max(1, days)));
    $('customFrom').max = todayIso();
    $('customFrom').min = isoDaysAgo(Period.MAX_DAYS);
    updateCustomHint();
  }

  function updateCustomHint() {
    const value = $('customFrom').value;
    const hint = $('customHint');
    hint.className = 'hint';
    if (!value) {
      hint.textContent = 'Выберите дату — посчитаем, сколько дней назад это было.';
      return;
    }
    if (value > todayIso()) {
      hint.textContent = 'Дата не может быть в будущем.';
      hint.className = 'hint bad';
      return;
    }
    const days = daysAgo(value);
    if (days === null || days < 1) {
      hint.textContent = 'Некорректная дата.';
      hint.className = 'hint bad';
      return;
    }
    if (days > Period.MAX_DAYS) {
      hint.textContent = 'Слишком давно — максимум 10 лет (3650 дней).';
      hint.className = 'hint bad';
      return;
    }
    hint.textContent = `Это ${days} ${daysWord(days)} назад — оценим период с ${value} по сегодня.`;
  }

  async function load() {
    $('remoteLink').href = CONFIG.remoteConfigUrl.replace('https://raw.githubusercontent.com/', 'https://github.com/').replace(/\/([^/]+)\/server\.json$/, '/blob/$1/server.json');
    const stored = await chrome.storage.sync.get(DEFAULTS);
    const own = stored.apiBase && stored.apiBase !== CONFIG.apiBase ? stored.apiBase : '';
    $('apiBase').value = own;
    $('listDots').checked = stored.listDots !== false;
    shortHours = Period.clampHours(stored.hours, DEFAULTS.hours);

    const period = Period.normalize(stored.period, shortHours);
    const preset = Period.presetId(period);
    if (preset) {
      $('period').value = preset;
      showCustom(false);
    } else {
      $('period').value = 'custom';
      fillCustom(period.kind === 'days' ? period : { kind: 'days', days: 30 });
      showCustom(true);
    }
    await refreshInfo(false);
  }

  function customPeriod() {
    const value = $('customFrom').value;
    if (!value) {
      show('Выберите дату начала периода', 'bad');
      return null;
    }
    if (value > todayIso()) {
      show('Дата не может быть в будущем', 'bad');
      return null;
    }
    const days = daysAgo(value);
    if (days === null || days < 1) {
      show('Некорректная дата', 'bad');
      return null;
    }
    if (days > Period.MAX_DAYS) {
      show('Слишком давно — максимум 10 лет (3650 дней)', 'bad');
      return null;
    }
    return { kind: 'days', days };
  }

  async function save() {
    const choice = $('period').value;
    const period = choice === 'custom' ? customPeriod() : Period.fromPreset(choice);
    if (!period) {
      if (choice !== 'custom') show('Выберите период анализа', 'bad');
      return false;
    }

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

    shortHours = period.kind === 'hours' ? period.hours : shortHours;
    await chrome.storage.sync.set({
      apiBase,
      period,
      hours: shortHours,
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

  $('period').addEventListener('change', () => {
    const custom = $('period').value === 'custom';
    showCustom(custom);
    if (custom) updateCustomHint();
  });
  $('customFrom').addEventListener('input', updateCustomHint);
  $('customFrom').addEventListener('change', updateCustomHint);
  $('reset').addEventListener('click', () => {
    $('apiBase').value = '';
    show('Адрес будет браться автоматически — нажмите «Сохранить»');
  });
  $('save').addEventListener('click', save);
  $('ping').addEventListener('click', async () => { if (await save()) ping(); });
  load();
})();
