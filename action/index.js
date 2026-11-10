// Star Guard — GitHub Action: проверка новых зависимостей в pull request.
// Без внешних пакетов: только встроенные fetch, fs и path из Node 20.
'use strict';

const fs = require('fs');

const MARKER = '<!-- star-guard-deps -->';
const LEVELS = ['low', 'medium', 'high', 'critical'];
const LEVEL_TEXT = {
  low: '🟢 низкий',
  medium: '🟡 средний',
  high: '🟠 высокий',
  critical: '🔴 критический',
};
const REPO_RE = /^[A-Za-z0-9_.-]{1,100}\/[A-Za-z0-9_.-]{1,100}$/;

function input(name, fallback) {
  const value = process.env['INPUT_' + name.replace(/ /g, '_').toUpperCase()];
  return value === undefined || value === '' ? fallback : value.trim();
}

function setOutput(name, value) {
  if (process.env.GITHUB_OUTPUT) {
    fs.appendFileSync(process.env.GITHUB_OUTPUT, `${name}=${value}\n`);
  }
}

// ---------- разбор манифестов ----------

function npmDependencies(text) {
  if (!text) return new Set();
  let json;
  try {
    json = JSON.parse(text);
  } catch (ignored) {
    return new Set();
  }
  const names = new Set();
  for (const key of ['dependencies', 'devDependencies', 'peerDependencies', 'optionalDependencies']) {
    for (const name of Object.keys(json[key] || {})) names.add(name);
  }
  return names;
}

function pipDependencies(text) {
  const names = new Set();
  for (const raw of (text || '').split('\n')) {
    const line = raw.replace(/#.*/, '').trim();
    if (!line || line.startsWith('-') || line.includes('://')) continue;
    const match = /^([A-Za-z0-9][A-Za-z0-9._-]*)/.exec(line);
    if (match) names.add(match[1].toLowerCase().replace(/[._]+/g, '-'));
  }
  return names;
}

function ecosystemOf(path) {
  const file = path.split('/').pop();
  if (file === 'package.json') return 'npm';
  if (/^requirements.*\.txt$/.test(file)) return 'pypi';
  return null;
}

function addedDependencies(ecosystem, baseText, headText) {
  const parse = ecosystem === 'npm' ? npmDependencies : pipDependencies;
  const before = parse(baseText);
  return [...parse(headText)].filter((name) => !before.has(name));
}

// ---------- пакет → репозиторий GitHub ----------

function githubRepoFromUrl(url) {
  if (typeof url !== 'string') return null;
  const match = /github\.com[/:]([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+?)(?:\.git)?(?:[/#?].*)?$/.exec(url.trim());
  if (!match) return null;
  const repo = match[1] + '/' + match[2];
  return REPO_RE.test(repo) ? repo : null;
}

async function resolveNpm(name, fetchImpl) {
  const response = await fetchImpl('https://registry.npmjs.org/' + name.replace('/', '%2F'));
  if (!response.ok) return null;
  const json = await response.json();
  const repository = json.repository;
  const url = typeof repository === 'string' ? repository : repository && repository.url;
  if (typeof url === 'string' && /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(url)) return url; // сокращение "owner/repo"
  return githubRepoFromUrl(url) || githubRepoFromUrl(json.homepage);
}

async function resolvePypi(name, fetchImpl) {
  const response = await fetchImpl('https://pypi.org/pypi/' + encodeURIComponent(name) + '/json');
  if (!response.ok) return null;
  const info = (await response.json()).info || {};
  const urls = Object.values(info.project_urls || {}).concat([info.home_page, info.download_url]);
  for (const url of urls) {
    const repo = githubRepoFromUrl(url);
    if (repo) return repo;
  }
  return null;
}

// ---------- GitHub API ----------

function github(token, fetchImpl) {
  const base = process.env.GITHUB_API_URL || 'https://api.github.com';
  return async (method, path, body) => {
    const response = await fetchImpl(base + path, {
      method,
      headers: {
        Authorization: 'Bearer ' + token,
        Accept: 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error(`GitHub API ${method} ${path}: ${response.status}`);
    return response.status === 204 ? null : response.json();
  };
}

async function fileAt(gh, repo, path, ref) {
  const file = await gh('GET', `/repos/${repo}/contents/${encodeURI(path)}?ref=${ref}`);
  if (!file || file.type !== 'file' || typeof file.content !== 'string') return null;
  return Buffer.from(file.content, 'base64').toString('utf8');
}

async function changedFiles(gh, repo, number) {
  const files = [];
  for (let page = 1; page <= 30; page++) {
    const chunk = await gh('GET', `/repos/${repo}/pulls/${number}/files?per_page=100&page=${page}`);
    if (!chunk || chunk.length === 0) break;
    files.push(...chunk);
    if (chunk.length < 100) break;
  }
  return files;
}

async function upsertComment(gh, repo, number, body) {
  const comments = await gh('GET', `/repos/${repo}/issues/${number}/comments?per_page=100`);
  const existing = (comments || []).find((c) => typeof c.body === 'string' && c.body.includes(MARKER));
  if (existing) {
    await gh('PATCH', `/repos/${repo}/issues/comments/${existing.id}`, { body });
  } else {
    await gh('POST', `/repos/${repo}/issues/${number}/comments`, { body });
  }
}

// ---------- Star Guard API ----------

async function summaries(apiUrl, repos, hours, fetchImpl) {
  const result = {};
  for (let i = 0; i < repos.length; i += 50) {
    const chunk = repos.slice(i, i + 50);
    const response = await fetchImpl(apiUrl.replace(/\/+$/, '') + '/api/v1/repos/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ repos: chunk, hours }),
    });
    if (!response.ok) throw new Error('Star Guard API ответил ' + response.status);
    for (const item of await response.json()) result[item.full_name.toLowerCase()] = item;
  }
  return result;
}

// ---------- отчёт ----------

function statusText(summary) {
  if (!summary) return '⚪ нет ответа';
  switch (summary.status) {
    case 'ok':
      return `${LEVEL_TEXT[summary.level] || summary.level} · ${summary.score}/100`;
    case 'not_indexed':
      return '⚪ не отслеживается';
    case 'no_data':
      return '⚪ нет свежих звёзд';
    case 'index_not_ready':
      return '⚪ индекс прогревается';
    default:
      return '⚪ ошибка';
  }
}

function escapeCell(text) {
  return String(text).replace(/\|/g, '\\|').replace(/\n/g, ' ');
}

function renderComment(rows, hours) {
  const flagged = rows.filter((r) => r.summary && r.summary.status === 'ok' && LEVELS.indexOf(r.summary.level) >= 1);
  const lines = [MARKER, '### 🛡️ Star Guard: новые зависимости', ''];
  if (rows.length === 0) {
    lines.push('Новых зависимостей в `package.json` и `requirements*.txt` не найдено.');
    return lines.join('\n');
  }
  lines.push(flagged.length
    ? `**${flagged.length} из ${rows.length}** новых зависимостей с признаками накрутки звёзд — стоит проверить их перед мержем.`
    : `Признаков накрутки звёзд у ${rows.length} новых зависимостей не найдено.`);
  lines.push('', '| Пакет | Репозиторий | Star Guard | Вердикт |', '|---|---|---|---|');
  for (const row of rows) {
    const repo = row.repo ? `[${row.repo}](https://github.com/${row.repo})` : '—';
    const verdict = row.summary && row.summary.verdict ? escapeCell(row.summary.verdict) : '';
    lines.push(`| \`${escapeCell(row.name)}\` (${row.ecosystem}) | ${repo} | ${row.repo ? statusText(row.summary) : '⚪ репозиторий не найден'} | ${verdict} |`);
  }
  lines.push('', `<sub>Окно анализа — ${hours} ч по GH Archive. Оценка показывает признаки, а не доказательство накрутки. «Не отслеживается» — репозитория нет в индексе популярных за окно.</sub>`);
  return lines.join('\n');
}

// ---------- main ----------

async function run(env, fetchImpl) {
  const apiUrl = input('api-url');
  if (!apiUrl) throw new Error('Не задан api-url');
  const token = input('github-token');
  const hours = Math.min(24, Math.max(1, Number(input('hours', '6')) || 6));
  const failOn = input('fail-on', 'none');
  const comment = input('comment', 'true') !== 'false';

  const event = JSON.parse(fs.readFileSync(env.GITHUB_EVENT_PATH, 'utf8'));
  const pr = event.pull_request;
  if (!pr) {
    console.log('Событие не pull_request — пропускаю.');
    return { rows: [], flagged: 0 };
  }
  const repo = env.GITHUB_REPOSITORY;
  const gh = github(token, fetchImpl);

  const rows = [];
  const seen = new Set();
  for (const file of await changedFiles(gh, repo, pr.number)) {
    const ecosystem = ecosystemOf(file.filename);
    if (!ecosystem || file.status === 'removed') continue;
    const [baseText, headText] = await Promise.all([
      file.status === 'added' ? null : fileAt(gh, repo, file.previous_filename || file.filename, pr.base.sha),
      fileAt(gh, repo, file.filename, pr.head.sha),
    ]);
    for (const name of addedDependencies(ecosystem, baseText, headText)) {
      const key = ecosystem + ':' + name;
      if (seen.has(key)) continue;
      seen.add(key);
      let resolved = null;
      try {
        resolved = ecosystem === 'npm' ? await resolveNpm(name, fetchImpl) : await resolvePypi(name, fetchImpl);
      } catch (error) {
        console.log(`Не удалось найти репозиторий ${name}: ${error.message}`);
      }
      rows.push({ name, ecosystem, repo: resolved });
    }
  }

  const repos = [...new Set(rows.filter((r) => r.repo).map((r) => r.repo))];
  const found = repos.length ? await summaries(apiUrl, repos, hours, fetchImpl) : {};
  for (const row of rows) row.summary = row.repo ? found[row.repo.toLowerCase()] : null;

  const body = renderComment(rows, hours);
  if (env.GITHUB_STEP_SUMMARY) fs.appendFileSync(env.GITHUB_STEP_SUMMARY, body + '\n');
  if (comment && rows.length > 0) await upsertComment(gh, repo, pr.number, body);

  const flagged = rows.filter((r) => r.summary && r.summary.status === 'ok' && LEVELS.indexOf(r.summary.level) >= 1).length;
  setOutput('flagged', flagged);
  setOutput('checked', repos.length);

  const threshold = LEVELS.indexOf(failOn);
  if (threshold >= 0) {
    const failing = rows.filter((r) => r.summary && r.summary.status === 'ok' && LEVELS.indexOf(r.summary.level) >= threshold);
    if (failing.length > 0) {
      throw new Error(`Зависимости с уровнем не ниже «${failOn}»: ${failing.map((r) => r.name).join(', ')}`);
    }
  }
  return { rows, flagged };
}

module.exports = {
  npmDependencies, pipDependencies, addedDependencies, ecosystemOf, githubRepoFromUrl,
  resolveNpm, resolvePypi, renderComment, run,
};

if (require.main === module) {
  run(process.env, fetch).catch((error) => {
    console.log('::error::' + error.message);
    process.exitCode = 1;
  });
}
