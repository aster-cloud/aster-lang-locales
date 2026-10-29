// 校验 generate.mjs 的生成物：仅含 C/S 映射，并用测试向量自检折叠语义。
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));
const table = JSON.parse(readFileSync(join(dir, 'casefold-15.1.json'), 'utf8'));
const txt = readFileSync(join(dir, 'CaseFolding-15.1.0.txt'), 'utf8');
const vectors = JSON.parse(readFileSync(join(dir, 'test-vectors.json'), 'utf8'));

const cs = txt.split('\n')
  .filter((l) => l && !l.startsWith('#'))
  .map((l) => l.split('#')[0].split(';').map((s) => s.trim()))
  .filter((f) => f[1] === 'C' || f[1] === 'S');

// 按 ADR 0051 §6.3：先 NFC，再逐码点应用简单折叠。
function fold(s) {
  return [...s.normalize('NFC')]
    .map((ch) => {
      const hex = ch.codePointAt(0).toString(16).toUpperCase().padStart(4, '0');
      const to = table.map[hex];
      return to ? String.fromCodePoint(parseInt(to, 16)) : ch;
    })
    .join('');
}

test('版本与条目数等于 txt 中 C+S 行数', () => {
  assert.equal(table.version, '15.1');
  assert.equal(Object.keys(table.map).length, cs.length);
  assert.ok(cs.length > 1000);
});

test('每条映射均为单码点且与 txt 一致，键已排序', () => {
  for (const f of cs) assert.equal(table.map[f[0]], f[2]);
  for (const v of Object.values(table.map)) assert.match(v, /^[0-9A-F]{4,6}$/);
  // 文件内键序须为字典序（JSON.parse 会重排数字键，故读原文）。
  const keys = [...readFileSync(join(dir, 'casefold-15.1.json'), 'utf8').matchAll(/^ {2}"([0-9A-F]+)":/gm)].map((m) => m[1]);
  assert.equal(keys.length, cs.length);
  assert.deepEqual(keys, [...keys].sort());
});

test('关键码点', () => {
  assert.equal(table.map['1E9E'], '00DF');
  assert.equal(table.map['03C2'], '03C3');
  assert.equal(table.map['01C5'], '01C6');
  assert.equal(Object.hasOwn(table.map, '0130'), false);
});

test('测试向量共 8 行且折叠结果符合期望', () => {
  assert.equal(vectors.length, 8);
  for (const v of vectors) {
    const cp = (x) => String.fromCodePoint(...x);
    const a = v.codepoints ? cp(v.codepoints.a) : v.a;
    const b = v.codepoints ? cp(v.codepoints.b) : v.b;
    assert.equal(fold(a) === fold(b), v.equal, v.note);
    const group = (v.group ?? []).map(cp);
    for (const x of group) for (const y of group) assert.equal(fold(x), fold(y), v.note);
    for (const d of (v.distinct ?? []).map(cp)) assert.notEqual(fold(d), fold(a), v.note);
  }
});
