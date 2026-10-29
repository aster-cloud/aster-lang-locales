// 校验 generate.mjs 的生成物：仅含 C/S 映射，并用测试向量自检折叠语义。
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));
const OUTPUTS = ['casefold-15.1.json', 'test-vectors.json'];
// 导入生成器前记录生成物的修改时间：导入本身不得写文件。
const mtimesBeforeImport = OUTPUTS.map((f) => statSync(join(dir, f)).mtimeMs);
const { generate, parseSimpleFolding, TEST_VECTORS } = await import('./generate.mjs');

const table = JSON.parse(readFileSync(join(dir, 'casefold-15.1.json'), 'utf8'));
const txt = readFileSync(join(dir, 'CaseFolding-15.1.0.txt'), 'utf8');
const vectors = JSON.parse(readFileSync(join(dir, 'test-vectors.json'), 'utf8'));
const cs = parseSimpleFolding(txt);

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
  for (const [code, mapping] of cs) assert.equal(table.map[code], mapping);
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

test('导入生成器不写任何文件', () => {
  assert.deepEqual(OUTPUTS.map((f) => statSync(join(dir, f)).mtimeMs), mtimesBeforeImport);
});

test('parseSimpleFolding 只取 C/S，跳过 F/T、注释与空行，按键排序', () => {
  const sample = [
    '# 注释',
    '',
    '0130; T; 0069; # LATIN CAPITAL LETTER I WITH DOT ABOVE',
    '00DF; F; 0073 0073; # LATIN SMALL LETTER SHARP S',
    '1E9E; S; 00DF; # LATIN CAPITAL LETTER SHARP S',
    '0041; C; 0061; # LATIN CAPITAL LETTER A',
  ].join('\n');
  assert.deepEqual(parseSimpleFolding(sample), [['0041', '0061'], ['1E9E', '00DF']]);
});

test('重新生成与已提交的生成物逐字节一致', () => {
  const out = mkdtempSync(join(tmpdir(), 'casefold-'));
  try {
    generate(out);
    for (const f of OUTPUTS) {
      assert.ok(readFileSync(join(out, f)).equals(readFileSync(join(dir, f))), `${f} 与 generate.mjs 的输出不一致，请运行 node casefold/generate.mjs`);
    }
  } finally {
    rmSync(out, { recursive: true, force: true });
  }
});

test('test-vectors.json 与生成器中的 TEST_VECTORS 一致', () => {
  assert.deepEqual(vectors, TEST_VECTORS);
});
