// 由 Unicode 15.1 CaseFolding.txt 生成简单大小写折叠表与测试向量（ADR 0051 §6.3）。
// 仅取状态 C 与 S；F（全折叠）与 T（土耳其语）一律忽略。
// 用法：node casefold/generate.mjs（输出确定，重复运行无差异）
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));

// 解析 txt：每行 "<code>; <status>; <mapping>; # name"，注释与空行跳过。
export function parseSimpleFolding(text) {
  const map = new Map();
  for (const line of text.split('\n')) {
    if (!line || line.startsWith('#')) continue;
    const [code, status, mapping] = line.split('#')[0].split(';').map((s) => s.trim());
    if (status !== 'C' && status !== 'S') continue;
    map.set(code, mapping);
  }
  return [...map].sort(([a], [b]) => (a < b ? -1 : 1));
}

// 测试向量与 ADR 0051 §6.3 表逐行对应；codepoints 以显式码点消除组合/分解形式的歧义。
export const TEST_VECTORS = [
  { a: 'Stufe', b: 'stufe', equal: true, note: '基本大小写',
    codepoints: { a: [0x53, 0x74, 0x75, 0x66, 0x65], b: [0x73, 0x74, 0x75, 0x66, 0x65] } },
  { a: 'Straße', b: 'STRASSE', equal: false, note: '简单折叠不把 ß 展开为 ss（全折叠才会）',
    codepoints: { a: [0x53, 0x74, 0x72, 0x61, 0xdf, 0x65], b: [0x53, 0x54, 0x52, 0x41, 0x53, 0x53, 0x45] } },
  { a: 'ẞ', b: 'ß', equal: true, note: 'CaseFolding.txt S 映射 U+1E9E -> U+00DF',
    codepoints: { a: [0x1e9e], b: [0xdf] } },
  { a: 'İ', b: 'i̇', equal: false,
    note: 'U+0130 在 CaseFolding.txt 仅有 F 与 T 映射，简单折叠保持不变；右侧为 i + U+0307 两个码点（NFC 不会合成为 U+0130）',
    codepoints: { a: [0x130], b: [0x69, 0x307] } },
  { a: 'Σ', b: 'σ', equal: true, note: 'Σ/σ/ς 三者两两相等，ς 的 C 映射为 σ',
    codepoints: { a: [0x3a3], b: [0x3c3] }, group: [[0x3a3], [0x3c3], [0x3c2]] },
  { a: 'ǅ', b: 'ǆ', equal: true, note: '标题大小写',
    codepoints: { a: [0x1c5], b: [0x1c6] } },
  { a: 'é', b: 'e\u0301', equal: true, note: '先 NFC 再折叠：U+00E9 与 e + U+0301',
    codepoints: { a: [0xe9], b: [0x65, 0x301] } },
  { a: 'नमस्ते', b: 'नमस्ते', equal: true, note: '天城文 / CJK 任意词仅与自身相等（无大小写）；distinct 中的词与其不等',
    codepoints: { a: [0x928, 0x92e, 0x938, 0x94d, 0x924, 0x947], b: [0x928, 0x92e, 0x938, 0x94d, 0x924, 0x947] },
    distinct: [[0x4f60, 0x597d]] },
];

const text = readFileSync(join(dir, 'CaseFolding-15.1.0.txt'), 'utf8');
const entries = parseSimpleFolding(text);
// 手工拼接以保证文件内键序为字典序：JS 对象会把纯数字键（如 "2126"）提前，不能用 JSON.stringify(object)。
const body = entries.map(([k, v]) => `  ${JSON.stringify(k)}: ${JSON.stringify(v)}`).join(',\n');
writeFileSync(join(dir, 'casefold-15.1.json'), `{\n "version": "15.1",\n "map": {\n${body}\n }\n}\n`);
writeFileSync(join(dir, 'test-vectors.json'), JSON.stringify(TEST_VECTORS, null, 1) + '\n');
