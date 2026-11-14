// 推断命名规则的参考实现（ADR 0056 §3.2），只用于本目录的自洽测试与生成测试向量；
// 两引擎（aster-lang-core TypeInference、aster-lang-ts parser/type-inference）各自按同一算法实现，
// 以 test-vectors.json 逐条对照。算法除分词式外不使用正则。

/** 分词式：不带任何标志、只用 ASCII 字符类与先行断言，在 Java 与 JS 中语义相同。 */
const WORD = /[A-Z]+(?=[A-Z][a-z])|[A-Z]?[a-z]+|[A-Z]+|[0-9]+/g;
const ASCII_IDENT = /^[A-Za-z0-9_]*$/;

/** ASCII 小写：只改 A–Z，其余字符（中文、变音符等）不变。 */
export function asciiLower(s) {
  let out = '';
  for (const ch of s) {
    const c = ch.codePointAt(0);
    out += c >= 0x41 && c <= 0x5a ? String.fromCodePoint(c + 0x20) : ch;
  }
  return out;
}

/** 第 1 步：分词。名字含 ASCII 字母、数字、`_` 以外的字符时词列表为空。 */
export function tokenize(name) {
  if (!ASCII_IDENT.test(name)) return [];
  const words = [];
  for (const segment of name.split('_')) {
    if (segment === '') continue;
    for (const m of segment.matchAll(WORD)) words.push(asciiLower(m[0]));
  }
  return words;
}

/** 第 2 步：单条规则是否命中。 */
export function ruleMatches(rule, words, name) {
  switch (rule.match) {
    case 'firstWord':
      return words.length > 0 && rule.words.includes(words[0]);
    case 'lastWord':
      return words.length > 0 && rule.words.includes(words[words.length - 1]);
    case 'lastWordMulti':
      return words.length >= 2 && rule.words.includes(words[words.length - 1]);
    case 'anyWord':
      return words.some((w) => rule.words.includes(w));
    case 'textPrefix':
      return rule.strings.some((s) => asciiLower(name).startsWith(s));
    case 'textSuffix':
      return rule.strings.some((s) => asciiLower(name).endsWith(s));
    default:
      throw new Error(`未知的 match：${rule.match}`);
  }
}

/** 第 3、4 步：按名字推断（与词法表无关）。命中规则中 priority 最大者胜，同优先级取靠前者。 */
export function inferFromName(table, name) {
  const words = tokenize(name);
  let best = null;
  for (const rule of table.rules) {
    if (!ruleMatches(rule, words, name)) continue;
    if (best === null || rule.priority > best.priority) best = rule;
  }
  return best === null ? table.default : best.type;
}

/** 第 5 步的约束类型 c：存在 Range 时看端点是否含 `.`；否则存在 Pattern 时为 Text；其余为 null。 */
export function constraintType(constraints) {
  const ranges = constraints.filter((c) => c.kind === 'Range');
  if (ranges.length > 0) {
    const decimal = ranges.some((r) => [r.min, r.max].some((v) => v !== null && v.includes('.')));
    return decimal ? 'Float' : 'Int';
  }
  return constraints.some((c) => c.kind === 'Pattern') ? 'Text' : null;
}

/** 第 5 步：名字推断结果 n 与约束类型 c 合并。 */
export function refine(n, c) {
  if (c === null || c === n) return n;
  if (n === 'Text' && (c === 'Int' || c === 'Float')) return c;
  if (n === 'Int' && c === 'Float') return 'Float';
  return n;
}

/** 未标注形参 / 字段的推断类型。 */
export function inferType(table, name, constraints = []) {
  return refine(inferFromName(table, name), constraintType(constraints));
}
