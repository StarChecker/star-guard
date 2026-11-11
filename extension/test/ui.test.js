// Проверка DOM-контракта без npm-зависимостей. Геометрию/темы смотрим в preview.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

class TestNode {
  constructor(tag, text = '') {
    this.tag = tag;
    this.children = [];
    this.attributes = {};
    this.style = {};
    this.listeners = {};
    this.text = text;
  }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; }
  setAttribute(name, value) { this.attributes[name] = String(value); }
  addEventListener(name, listener) { this.listeners[name] = listener; }
  set className(value) { this.attributes.class = value; }
  get textContent() { return this.text + this.children.map(n => n.textContent).join(''); }
  set textContent(value) { this.text = String(value); this.children = []; }
}

function setup() {
  const context = vm.createContext({ window: {}, Node: TestNode, document: {
    createElement: tag => new TestNode(tag),
    createElementNS: (_, tag) => new TestNode(tag),
    createTextNode: text => new TestNode('#text', String(text)),
  } });
  for (const file of ['src/ui.js', 'preview/samples.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '..', file), 'utf8'), context);
  }
  return { UI: context.window.StarGuardUI, samples: context.window.STAR_GUARD_SAMPLES };
}

function nodes(root, predicate) {
  return [root, ...root.children.flatMap(child => nodes(child, () => true))].filter(predicate);
}
const byClass = (root, name) => nodes(root, n => (n.attributes.class || '').split(' ').includes(name));
const byTag = (root, tag) => nodes(root, n => n.tag === tag);
function card(UI, data) {
  const root = new TestNode('shadow-root');
  UI.renderCard(root, { kind: 'report', data }, {});
  return byClass(root, 'card')[0];
}

test('popup/sidebar card has five initial actors, a native disclosure and activity tiles', () => {
  const { UI, samples } = setup();
  const root = card(UI, samples.farm);
  const section = byClass(root, 'suspicious-actors')[0];
  assert.equal(section.children.find(n => n.tag === 'ul').children.length, 5);
  const more = byClass(section, 'actor-more')[0];
  assert.equal(more.tag, 'details');
  assert.equal(more.attributes.open, undefined);
  assert.equal(byTag(more, 'summary')[0].textContent, 'Ещё 3');
  assert.equal(byClass(more, 'actor').length, 3);
  assert.match(section.textContent, /В ответе 8 из 52/);
  assert.match(section.textContent, /свежий или пустой аккаунт/);
  assert.match(section.textContent, /пересечение аудиторий/);
  assert.match(section.textContent, /Дата создания неизвестна/);
  assert.equal(byClass(root, 'tile').length, 4);
  assert.match(root.textContent, /5контрибьюторов/);
});

test('spike background uses exact interval boundaries within a chart bucket', () => {
  const { UI, samples } = setup();
  const root = card(UI, samples.farm);
  const band = byClass(root, 'spike-band')[0];
  assert.ok(Math.abs(Number(band.attributes.x) - 145 / 360 * 296) < .001);
  assert.ok(Math.abs(Number(band.attributes.width) - 10 / 360 * 296) < .001);
  assert.match(byTag(band, 'title')[0].textContent, /Подозрительный всплеск/);
  assert.match(byClass(root, 'spike-label')[0].textContent, /26 звёзд/);
});

test('organic report has no spike or actor disclosure', () => {
  const { UI, samples } = setup();
  const root = card(UI, samples.healthy);
  assert.equal(byClass(root, 'spike-band').length, 0);
  assert.equal(byClass(root, 'actor').length, 0);
  assert.equal(byClass(root, 'actor-more').length, 0);
  assert.match(root.textContent, /C2\/C4 не отметили аккаунты/);
});

test('hash mode renders no profile links and mock C1 stays neutral', () => {
  const { UI, samples } = setup();
  const root = card(UI, samples.private);
  const section = byClass(root, 'suspicious-actors')[0];
  assert.equal(byTag(section, 'a').length, 0);
  assert.match(section.textContent, /#000000000001/);
  assert.match(byClass(root, 'spike-band')[0].attributes.class, /neutral/);
  assert.match(root.textContent, /Всплеск без оценки C1/);
});

test('API strings remain text and invalid logins never become links', () => {
  const { UI, samples } = setup();
  samples.farm.suspicious_actors[0].login = '<img src=x onerror=alert(1)>';
  const root = card(UI, samples.farm);
  const first = byClass(root, 'actor')[0];
  assert.match(first.textContent, /<img src=x onerror=alert\(1\)>/);
  assert.equal(byTag(first, 'img').length, 0);
  assert.equal(byTag(first, 'a').length, 0);
});

test('older API and missing contributor data render without inventing zero', () => {
  const { UI, samples } = setup();
  delete samples.farm.suspicious_actors;
  delete samples.farm.stats.contributors;
  delete samples.farm.stars_timeline.spike_intervals;
  const root = card(UI, samples.farm);
  assert.equal(byClass(root, 'suspicious-actors').length, 0);
  assert.match(root.textContent, /—контрибьюторов/);
  assert.match(root.textContent, /Нет полных данных об участниках/);
});

test('GitHub star history does not present missing activity as zero', () => {
  const { UI, samples } = setup();
  const root = card(UI, samples.history);
  assert.ok(byClass(root, 'tile').every(tile => byTag(tile, 'b')[0].textContent === '—'));
});
