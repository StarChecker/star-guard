// Star Guard — фоновый service worker.
// Все запросы к API идут отсюда: из content script кросс-доменный fetch к нашему серверу
// упирается в CORS. Принимаем только сообщения фиксированных типов с проверенным именем
// репозитория — произвольный URL страница подсунуть не может.

importScripts('period.js');

const Period = self.StarGuardPeriod;
const clampHours = Period.clampHours;
const normalizePeriod = Period.normalize;
const periodKey = Period.key;
const periodQuery = Period.query;
const periodBody = Period.body;

const DEFAULT_SETTINGS = {
  apiBase: 'http://localhost:8080',
  hours: 6,          // короткое окно для точек в списках и обратной совместимости
  period: null,      // { kind: 'hours'|'days'|'all', hours?, days?, to? }
  listDots: true,
};

const REPO_RE = /^[A-Za-z0-9_.-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/;
const SCAN_ID_RE = /^[0-9a-f-]{36}$/i;
const REPORT_TTL_MS = 5 * 60 * 1000;
const SUMMARY_TTL_MS = 10 * 60 * 1000;
const BATCH_SIZE = 50;

const reportCache = new Map();   // key → { at, value }
const summaryCache = new Map();  // key → { at, value }

async function settings() {
  const stored = await chrome.storage.sync.get(DEFAULT_SETTINGS);
  const apiBase = String(stored.apiBase || DEFAULT_SETTINGS.apiBase).replace(/\/+$/, '');
  const hours = clampHours(stored.hours, DEFAULT_SETTINGS.hours);
  return {
    apiBase,
    hours,
    period: normalizePeriod(stored.period, hours),
    listDots: stored.listDots !== false,
  };
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

async function report(repo, period) {
  const key = repo.toLowerCase() + '|' + periodKey(period);
  const cached = fresh(reportCache, key, REPORT_TTL_MS);
  if (cached) {
    return cached;
  }
  const [owner, name] = repo.split('/');
  const result = await call(
    `/api/v1/repos/${encodeURIComponent(owner)}/${encodeURIComponent(name)}?${periodQuery(period)}`);
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

async function scanStart(repo, period) {
  const [owner, name] = repo.split('/');
  return call('/api/v1/scans', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ owner, name, ...periodBody(period) }),
  });
}

async function scanStatus(id) {
  const result = await call('/api/v1/scans/' + encodeURIComponent(id));
  if (result.ok && result.data.status === 'done' && result.data.report) {
    const repo = result.data.repo.full_name;
    const hours = result.data.report.window.hours;
    reportCache.set(repo.toLowerCase() + '|h' + hours, {
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
  const hours = Number.isInteger(message.hours) ? clampHours(message.hours, current.hours) : current.hours;
  const period = normalizePeriod(message.period, hours);
  switch (message.type) {
    case 'settings':
      return { ok: true, data: current };
    case 'report':
      if (!REPO_RE.test(message.repo || '')) return { ok: false, code: 'BAD_REPO' };
      return report(message.repo, period);
    case 'batch': {
      const repos = Array.isArray(message.repos) ? message.repos.filter((r) => REPO_RE.test(r)) : [];
      if (repos.length === 0) return { ok: true, data: {} };
      // Точки в списках — сводки индекса: только часовое окно (максимум 24 ч).
      return batch(repos.slice(0, 200), hours);
    }
    case 'preview': {
      if (!REPO_RE.test(message.repo || '')) return { ok: false, code: 'BAD_REPO' };
      const [owner, name] = message.repo.split('/');
      return call(`/api/v1/preview/${encodeURIComponent(owner)}/${encodeURIComponent(name)}`);
    }
    case 'scanStart':
      if (!REPO_RE.test(message.repo || '')) return { ok: false, code: 'BAD_REPO' };
      return scanStart(message.repo, period);
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
