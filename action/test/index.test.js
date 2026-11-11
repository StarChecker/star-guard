'use strict';

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const action = require('../index.js');

test('находит только добавленные npm-зависимости', () => {
  const base = JSON.stringify({ dependencies: { react: '^18' } });
  const head = JSON.stringify({ dependencies: { react: '^18', 'left-pad': '1' }, devDependencies: { '@scope/tool': '2' } });
  assert.deepStrictEqual(action.addedDependencies('npm', base, head).sort(), ['@scope/tool', 'left-pad']);
  assert.deepStrictEqual(action.addedDependencies('npm', null, head).length, 3);
});

test('разбирает requirements.txt с версиями, extras и комментариями', () => {
  const head = 'requests==2.31\n# comment\nuvicorn[standard]>=0.20\n-r other.txt\nGit+https://x\nSome_Pkg.Name~=1\n';
  assert.deepStrictEqual([...action.pipDependencies(head)], ['requests', 'uvicorn', 'some-pkg-name']);
  assert.deepStrictEqual(action.addedDependencies('pypi', 'requests==2.0', head), ['uvicorn', 'some-pkg-name']);
});

test('определяет экосистему по имени файла', () => {
  assert.strictEqual(action.ecosystemOf('web/package.json'), 'npm');
  assert.strictEqual(action.ecosystemOf('requirements-dev.txt'), 'pypi');
  assert.strictEqual(action.ecosystemOf('package-lock.json'), null);
});

test('извлекает owner/repo из разных форм URL', () => {
  assert.strictEqual(action.githubRepoFromUrl('git+https://github.com/facebook/react.git'), 'facebook/react');
  assert.strictEqual(action.githubRepoFromUrl('git@github.com:psf/requests.git'), 'psf/requests');
  assert.strictEqual(action.githubRepoFromUrl('https://github.com/encode/uvicorn/tree/main'), 'encode/uvicorn');
  assert.strictEqual(action.githubRepoFromUrl('https://gitlab.com/a/b'), null);
});

test('полный прогон: читает PR, находит репозитории, пишет комментарий и падает по fail-on', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sg-action-'));
  const eventPath = path.join(dir, 'event.json');
  fs.writeFileSync(eventPath, JSON.stringify({
    pull_request: { number: 7, base: { sha: 'base' }, head: { sha: 'head' } },
  }));
  const posted = [];
  const b64 = (text) => Buffer.from(text).toString('base64');
  const files = {
    'base:package.json': JSON.stringify({ dependencies: { react: '18' } }),
    'head:package.json': JSON.stringify({ dependencies: { react: '18', 'gpt-agent-pro': '1', 'left-pad': '1' } }),
  };
  const fetchImpl = async (url, init = {}) => {
    const json = (body, status = 200) => ({ ok: status < 300, status, json: async () => body });
    if (url.includes('/pulls/7/files')) return json([{ filename: 'package.json', status: 'modified' }]);
    const contents = /\/contents\/(.+)\?ref=(\w+)/.exec(url);
    if (contents) {
      const text = files[contents[2] + ':' + contents[1]];
      return text ? json({ type: 'file', content: b64(text) }) : json(null, 404);
    }
    if (url === 'https://registry.npmjs.org/gpt-agent-pro') return json({ repository: { url: 'git+https://github.com/quick-ai/gpt-agent-pro.git' } });
    if (url === 'https://registry.npmjs.org/left-pad') return json({ repository: 'stevemao/left-pad' });
    if (url.endsWith('/api/v1/repos/batch')) {
      const body = JSON.parse(init.body);
      assert.deepStrictEqual(body.repos.sort(), ['quick-ai/gpt-agent-pro', 'stevemao/left-pad']);
      return json([
        { full_name: 'quick-ai/gpt-agent-pro', status: 'ok', score: 78, level: 'high', verdict: 'Заметные признаки накрутки.' },
        { full_name: 'stevemao/left-pad', status: 'not_indexed', score: null },
      ]);
    }
    if (url.includes('/issues/7/comments') && (!init.method || init.method === 'GET')) return json([]);
    if (url.includes('/issues/7/comments') && init.method === 'POST') {
      posted.push(JSON.parse(init.body).body);
      return json({ id: 1 }, 201);
    }
    throw new Error('unexpected ' + url);
  };

  process.env['INPUT_API-URL'] = 'https://sg.example.com';
  process.env['INPUT_GITHUB-TOKEN'] = 't';
  process.env['INPUT_FAIL-ON'] = 'high';
  const env = { GITHUB_EVENT_PATH: eventPath, GITHUB_REPOSITORY: 'me/app' };

  await assert.rejects(() => action.run(env, fetchImpl), /gpt-agent-pro/);
  assert.strictEqual(posted.length, 1);
  assert.match(posted[0], /star-guard-deps/);
  assert.match(posted[0], /\*\*1 из 2\*\*/);
  assert.match(posted[0], /🟠 высокий · 78\/100/);
  assert.match(posted[0], /не отслеживается/);

  process.env['INPUT_FAIL-ON'] = 'none';
  const result = await action.run(env, fetchImpl);
  assert.strictEqual(result.flagged, 1);
});

test('комментарий без новых зависимостей', () => {
  assert.match(action.renderComment([], 6), /Новых зависимостей/);
});
