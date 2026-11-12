// Star Guard — фоновый service worker.
// Все запросы к API идут отсюда: из content script кросс-доменный fetch к нашему серверу
// упирается в CORS. Принимаем только сообщения фиксированных типов с проверенным именем
// репозитория — произвольный URL страница подсунуть не может.

importScripts('config.js');

const CONFIG = self.STAR_GUARD_CONFIG;
const DEFAULT_SETTINGS = {
  apiBase: '',  // пусто — адрес из server.json, иначе запасной из config.js
  hours: 6,
  listDots: true,
};

const REPO_RE = /^[A-Za-z0-9_.-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/;
const SCAN_ID_RE = /^[0-9a-f-]{36}$/i;
const REPORT_TTL_MS = 5 * 60 * 1000;
const SUMMARY_TTL_MS = 10 * 60 * 1000;
const REMOTE_TTL_MS = 5 * 60 * 1000;
const REMOTE_RETRY_MS = 30 * 1000;
const BATCH_SIZE = 50;

const reportCache = new Map();   // key → { at, value }
const summaryCache = new Map();  // key → { at, value }
let remote = null;               // { at, ttl, apiBase } — последний прочитанный server.json

function normalizeBase(value) {
  try {
    const url = new URL(String(value || '').trim());
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return '';
    return url.toString().replace(/\/+$/, '');
  } catch (ignored) {
    return '';
  }
}

/** Адрес из server.json; при сбое — последний удачно прочитанный. */
async function remoteApiBase() {
  if (remote && Date.now() - remote.at < remote.ttl) {
    return remote.apiBase;
  }
  let apiBase = '';
  let ok = false;
  if (CONFIG.remoteConfigUrl) {
    try {
      const response = await fetch(CONFIG.remoteConfigUrl, { cache: 'no-store' });
      if (response.ok) {
        apiBase = normalizeBase((await response.json()).apiBase);
        ok = true;
      }
    } catch (ignored) {
      ok = false;
    }
  }
  if (ok) {
    await chrome.storage.local.set({ remoteApiBase: apiBase });
  } else {
    apiBase = (await chrome.storage.local.get({ remoteApiBase: '' })).remoteApiBase;
  }
  if (remote && remote.apiBase !== apiBase) {
    reportCache.clear();
    summaryCache.clear();
  }
  remote = { at: Date.now(), ttl: ok ? REMOTE_TTL_MS : REMOTE_RETRY_MS, apiBase };
  return apiBase;
}

async function settings() {
  const stored = await chrome.storage.sync.get(DEFAULT_SETTINGS);
  let own = normalizeBase(stored.apiBase);
  // раньше в настройки сохранялся сам адрес по умолчанию — считаем это «автоматически»
  if (own === normalizeBase(CONFIG.apiBase)) own = '';
  let apiBase = own;
  let source = 'settings';
  if (!apiBase) {
    apiBase = await remoteApiBase();
    source = 'remote';
  }
  if (!apiBase) {
    apiBase = normalizeBase(CONFIG.apiBase);
    source = 'default';
  }
  const hours = Math.min(24, Math.max(1, Number(stored.hours) || DEFAULT_SETTINGS.hours));
  return { apiBase, source, hours, listDots: stored.listDots !== false };
}

function fresh(cache, key, ttl) {
  const hit = cache.get(key);
  if (hit && Date.now() - hit.at < ttl) {
    return hit.value;
  }
  cache.delete(key);
  return undefined;
}

async function call(path, init) {
  const { apiBase } = await settings();
  let response;
  try {
    response = await fetch(apiBase + path, {
      ...init,
      headers: { Accept: 'application/json', ...(init && init.headers) },
    });
  } catch (error) {
    return { ok: false, status: 0, code: 'API_UNREACHABLE', message: String(error), apiBase };
  }
  let body = null;
  try {
    body = await response.json();
  } catch (ignored) {
    body = null;
  }
  if (!response.ok) {
    return {
      ok: false,
      status: response.status,
      code: (body && body.code) || 'HTTP_' + response.status,
      message: (body && body.detail) || response.statusText,
      apiBase,
    };
  }
  return { ok: true, data: body, apiBase };
}

async function report(repo, hours) {
  const key = repo.toLowerCase() + '|' + hours;
  const cached = fresh(reportCache, key, REPORT_TTL_MS);
  if (cached) {
    return cached;
  }
  const [owner, name] = repo.split('/');
  const result = await call(
    `/api/v1/repos/${encodeURIComponent(owner)}/${encodeURIComponent(name)}?hours=${hours}`);
  if (result.ok) {
    reportCache.set(key, { at: Date.now(), value: result });
  }
  return result;
}

async function batch(repos, hours) {
  const out = {};
  const missing = [];
  for (const repo of repos) {
    const cached = fresh(summaryCache, repo.toLowerCase() + '|' + hours, SUMMARY_TTL_MS);
    if (cached) {
      out[repo.toLowerCase()] = cached;
    } else {
      missing.push(repo);
    }
  }
  for (let i = 0; i < missing.length; i += BATCH_SIZE) {
    const chunk = missing.slice(i, i + BATCH_SIZE);
    const result = await call('/api/v1/repos/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ repos: chunk, hours }),
    });
    if (!result.ok) {
      return result;
    }
    for (const item of result.data) {
      const key = item.full_name.toLowerCase();
      out[key] = item;
      summaryCache.set(key + '|' + hours, { at: Date.now(), value: item });
    }
  }
  return { ok: true, data: out };
}

async function scanStart(repo, hours) {
  const [owner, name] = repo.split('/');
  return call('/api/v1/scans', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ owner, name, hours }),
  });
}

async function scanStatus(id) {
  const result = await call('/api/v1/scans/' + encodeURIComponent(id));
  if (result.ok && result.data.status === 'done' && result.data.report) {
    const repo = result.data.repo.full_name;
    const hours = result.data.report.window.hours;
    reportCache.set(repo.toLowerCase() + '|' + hours, {
      at: Date.now(),
      value: { ok: true, data: result.data.report, apiBase: result.apiBase },
    });
  }
  return result;
}

async function handle(message) {
  if (!message || typeof message.type !== 'string') {
    return { ok: false, code: 'BAD_MESSAGE' };
  }
  const current = await settings();
  const hours = Number.isInteger(message.hours) ? Math.min(24, Math.max(1, message.hours)) : current.hours;
  switch (message.type) {
    case 'settings':
      return { ok: true, data: current };
    case 'report':
      if (!REPO_RE.test(message.repo || '')) return { ok: false, code: 'BAD_REPO' };
      return report(message.repo, hours);
    case 'batch': {
      const repos = Array.isArray(message.repos) ? message.repos.filter((r) => REPO_RE.test(r)) : [];
      if (repos.length === 0) return { ok: true, data: {} };
      return batch(repos.slice(0, 200), hours);
    }
    case 'preview': {
      if (!REPO_RE.test(message.repo || '')) return { ok: false, code: 'BAD_REPO' };
      const [owner, name] = message.repo.split('/');
      return call(`/api/v1/preview/${encodeURIComponent(owner)}/${encodeURIComponent(name)}`);
    }
    case 'scanStart':
      if (!REPO_RE.test(message.repo || '')) return { ok: false, code: 'BAD_REPO' };
      return scanStart(message.repo, hours);
    case 'scanStatus':
      if (!SCAN_ID_RE.test(message.id || '')) return { ok: false, code: 'BAD_SCAN_ID' };
      return scanStatus(message.id);
    case 'top': {
      // живые репозитории индекса с оценкой — для popup на страницах без репозитория
      const limit = Number.isInteger(message.limit) ? Math.min(50, Math.max(1, message.limit)) : 15;
      const level = ['low', 'medium', 'high', 'critical'].includes(message.maxLevel) ? `&max_level=${message.maxLevel}` : '';
      return call(`/api/v1/index/repos?limit=${limit}&existing_only=true&with_score=true${level}`);
    }
    case 'ping': {
      // 200 или 503 (индекс прогревается) — сервер жив; иначе — недоступен
      const result = await call('/api/v1/index/repos?limit=1');
      const alive = result.ok || result.status === 503;
      return { ok: alive, code: alive ? 'OK' : result.code, apiBase: result.apiBase, message: result.message };
    }
    case 'serverInfo': {
      // для настроек: откуда сейчас взят адрес; force — перечитать server.json
      if (message.force) remote = null;
      const { apiBase, source } = await settings();
      return { ok: true, apiBase, source, remoteConfigUrl: CONFIG.remoteConfigUrl, fallback: CONFIG.apiBase };
    }
    case 'clearCache':
      reportCache.clear();
      summaryCache.clear();
      return { ok: true };
    default:
      return { ok: false, code: 'UNKNOWN_TYPE' };
  }
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  // Принимаем сообщения только от своего расширения (content script, popup, options).
  if (sender.id !== chrome.runtime.id) {
    return false;
  }
  handle(message).then(sendResponse, (error) =>
    sendResponse({ ok: false, code: 'INTERNAL', message: String(error) }));
  return true;
});

// content script хранит id идущего скана в session storage, чтобы пережить переходы по страницам
chrome.storage.session.setAccessLevel({ accessLevel: 'TRUSTED_AND_UNTRUSTED_CONTEXTS' }).catch(() => {});

chrome.storage.onChanged.addListener((changes, area) => {
  if (area !== 'sync') return;
  reportCache.clear();
  summaryCache.clear();
});
