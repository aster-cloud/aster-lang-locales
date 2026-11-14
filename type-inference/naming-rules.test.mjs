// naming-rules.json 与 test-vectors.json 的自洽检查（ADR 0056 §3.2）：参考实现逐条跑测试向量，
// 并检查规则表本身的结构约束（schema 校验由 gradle verifyTypeInferenceRules 负责）。
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { inferType, tokenize, constraintType, refine, ruleMatches } from './infer.mjs';

const dir = dirname(fileURLToPath(import.meta.url));
const table = JSON.parse(readFileSync(join(dir, 'naming-rules.json'), 'utf8'));
const vectors = JSON.parse(readFileSync(join(dir, 'test-vectors.json'), 'utf8'));

test('全部测试向量与参考实现一致', () => {
  const wrong = vectors
    .map((v) => ({ ...v, actual: inferType(table, v.name, v.constraints) }))
    .filter((v) => v.actual !== v.expected)
    .map((v) => `${v.name} ${JSON.stringify(v.constraints)}：期望 ${v.expected}，实际 ${v.actual}`);
  assert.deepEqual(wrong, []);
});

test('测试向量 (name, constraints) 不重复', () => {
  const keys = vectors.map((v) => `${v.name}\u0000${JSON.stringify(v.constraints)}`);
  assert.equal(new Set(keys).size, keys.length);
});

test('每条规则至少有一个命中向量（该规则是唯一胜者）和一个近似但不命中的向量', () => {
  // 命中：向量的推断类型由该规则给出；近似不命中：名字与该规则的某个词 / 串共享字母，但该规则不命中
  const winners = new Map();
  for (const v of vectors) {
    if (v.constraints.length > 0) continue;
    const words = tokenize(v.name);
    let best = null;
    table.rules.forEach((rule, i) => {
      const hit = ruleMatches(rule, words, v.name);
      if (hit && (best === null || rule.priority > table.rules[best].priority)) best = i;
    });
    if (best !== null) winners.set(best, (winners.get(best) ?? 0) + 1);
  }
  const unhit = table.rules.map((_, i) => i).filter((i) => !winners.has(i));
  assert.deepEqual(unhit, [], `没有命中向量的规则（下标）：${unhit}`);
  for (const [i, rule] of table.rules.entries()) {
    const items = rule.words ?? rule.strings;
    const near = vectors.some((v) => {
      const lower = v.name.toLowerCase();
      return items.some((w) => lower.includes(w)) && !ruleMatches(rule, tokenize(v.name), v.name);
    });
    assert.ok(near, `规则 ${i}（${rule.match} ${items.slice(0, 3).join(', ')}…）缺少近似但不命中的向量`);
  }
});

test('规则表结构：共 22 条，布尔前缀规则是 14 个词各配 5 种词形', () => {
  assert.equal(table.version, 1);
  assert.equal(table.default, 'Text');
  assert.equal(table.rules.length, 22);
  const prefix = table.rules.find((r) => r.match === 'firstWord');
  assert.equal(prefix.words.length, 70);
  const stems = prefix.words.filter((_, i) => i % 5 === 0);
  for (const [i, stem] of stems.entries()) {
    assert.deepEqual(prefix.words.slice(i * 5, i * 5 + 5), ['', 's', 'es', 'd', 'ed'].map((suffix) => stem + suffix));
  }
});

test('分词示例（ADR 0056 §3.2 第 1 步）', () => {
  assert.deepEqual(tokenize('interestRateAPR'), ['interest', 'rate', 'apr']);
  assert.deepEqual(tokenize('userID'), ['user', 'id']);
  assert.deepEqual(tokenize('days_since_consent'), ['days', 'since', 'consent']);
  assert.deepEqual(tokenize('monthlyPremium'), ['monthly', 'premium']);
  assert.deepEqual(tokenize('pre_auth__Code'), ['pre', 'auth', 'code']);
  assert.deepEqual(tokenize('XMLHttpRequest'), ['xml', 'http', 'request']);
  assert.deepEqual(tokenize('abc123Score'), ['abc', '123', 'score']);
  assert.deepEqual(tokenize('是否成年'), []);
  assert.deepEqual(tokenize('kundeGültig'), []);
  assert.deepEqual(tokenize(''), []);
});

test('约束修正与书写顺序无关（ADR 0056 §3.2 第 5 步）', () => {
  const range = { kind: 'Range', min: '1', max: '10' };
  const pattern = { kind: 'Pattern', regexp: 'x' };
  assert.equal(constraintType([pattern, range]), 'Int');
  assert.equal(constraintType([range, pattern]), 'Int');
  assert.equal(constraintType([{ kind: 'Range', min: null, max: '0.5' }]), 'Float');
  assert.equal(constraintType([{ kind: 'Required' }]), null);
  assert.equal(refine('Text', 'Float'), 'Float');
  assert.equal(refine('Int', 'Float'), 'Float');
  assert.equal(refine('Float', 'Int'), 'Float');
  assert.equal(refine('Bool', 'Int'), 'Bool');
  assert.equal(refine('Int', 'Text'), 'Int');
});
