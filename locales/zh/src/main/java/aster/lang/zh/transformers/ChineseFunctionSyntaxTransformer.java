package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.canonicalizer.SyntaxTransformer;
import aster.core.lexicon.CanonicalizationConfig;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文函数语法重排变换器。
 * <p>
 * 将中文函数定义语法重排为英文 IR 语法：
 * <ul>
 *   <li>{@code 规则 funcName（params）：} → {@code Rule funcName given params :}</li>
 * </ul>
 * <p>
 * 参数须写成 {@code 名 作为 类型}（翻译后为 {@code name as Type}），这是 givenParamList 接受的形式；
 * {@code 名: 类型} 虽会被重排，但语法不接受。aster-lang-ts 的同名变换器与本类逐字一致。
 */
public final class ChineseFunctionSyntaxTransformer implements SyntaxTransformer {

    public static final ChineseFunctionSyntaxTransformer INSTANCE = new ChineseFunctionSyntaxTransformer();

    /**
     * 匹配"规则 funcName（params）："。
     * <p>
     * 括号同时接受半角 {@code ()} 与全角 {@code （）}（U+FF08/U+FF09）：中文输入法默认输出全角，
     * 而 core Canonicalizer 的 fullWidthToHalf 在 preTranslationTransformers 链**之后**才执行，
     * 同链更早的 chinese-punctuation 也只映射 {@code 。，：、}——所以走到本变换器时全角括号
     * 仍是原样，只认半角会让全角写法静默漏过（issue #91）。行尾的 {@code ：} 无需在此处理，
     * 它已被 chinese-punctuation 转成 {@code :} 并落在末尾捕获组里原样保留。
     * <p>
     * 字符集逐项写出，与旧模式 {@code ^(\s*)…\s+…\s*…\s*(.*)$}（MULTILINE + UNICODE_CHARACTER_CLASS）
     * 的匹配结果完全相同，同时让 aster-lang-ts 的同名变换器能用同一组字符集逐字复现：
     * <ul>
     *   <li>{@code WS} = Unicode White_Space，即旧模式的 {@code \s}；</li>
     *   <li>{@code LT} = Java 行终止符，{@code ^} 认它们之后的位置，{@code .} 不匹配它们；</li>
     *   <li>缩进组只取 {@code WS} 减 {@code LT}：旧模式从空行行首起跑时把空行一并捕获又原样输出，
     *       改为从函数头所在行起跑，输出相同，但不再在每个空行起点重扫整段空白；</li>
     *   <li>末尾 {@code [^LT]*} 即旧模式的 {@code (.*)$}。</li>
     * </ul>
     */
    private static final String WS = "\\t\\n\\u000B\\u000C\\r \\u0085\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000";
    private static final String LT = "\\n\\r\\u0085\\u2028\\u2029";
    private static final String HWS = "\\t\\u000B\\u000C \\u00A0\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000";
    private static final Pattern RULE_FUNC = Pattern.compile(
            "(?<![^" + LT + "])([" + HWS + "]*)(?:规则|Rule)[" + WS + "]+([\\p{L}][\\p{L}0-9_]*)[" + WS + "]*"
                    + "[(（]([^)）]*?)[)）][" + WS + "]*([^" + LT + "]*)"
    );

    private ChineseFunctionSyntaxTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        // ★必须经 segmenter，只改写字符串**之外**的片段（issue #82）。
        //   本类此前是 7 个中文变换器里唯一忽略 segmenter 的——直接对整段源码跑正则，
        //   于是字符串字面量里形如 `规则 X(...)` 的内容会被当作函数声明改写。
        //   RULE_FUNC 锚定行首，多行字符串里自成一行的内容尤其危险。
        //   同目录另外 6 个变换器（Punctuation/Possessive/Operator/SetTo/ResultIs/LetBe）
        //   一律走 transformOutsideStrings 或 replaceOutsideStrings。
        return segmenter.transformOutsideStrings(source, ChineseFunctionSyntaxTransformer::rewriteRuleFunc);
    }

    /**
     * 只对「最后一个右括号所在函数头」及其之前的部分跑正则。
     * <p>
     * 每个匹配都必须含右括号，且以右括号后的空白与该行剩余部分收尾，所以截断点之后不可能有匹配，
     * 之前的匹配也不会越过截断点——结果与整段匹配相同。不截断时，最后一个右括号之后每个
     * {@code 规则 名(} 行首都会让惰性参数组扫到片段末尾再失败，耗时随源码长度平方增长
     * （1.6 万个未闭合函数头约 10 秒）。
     */
    static String rewriteRuleFunc(String s) {
        int cut = matchableEnd(s);
        return cut == 0 ? s : rewriteMatchable(s.substring(0, cut)) + s.substring(cut);
    }

    /** 最后一个右括号之后跳过空白，再到下一个行终止符（或片段末尾）为止；无右括号时为 0。 */
    static int matchableEnd(String s) {
        int i = Math.max(s.lastIndexOf(')'), s.lastIndexOf('\uFF09'));
        if (i < 0) {
            return 0;
        }
        i++;
        while (i < s.length() && isWhiteSpace(s.charAt(i))) {
            i++;
        }
        while (i < s.length() && !isLineTerminator(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isWhiteSpace(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r') || c == '\u0085' || c == '\u00A0' || c == '\u1680'
                || (c >= '\u2000' && c <= '\u200A') || c == '\u2028' || c == '\u2029' || c == '\u202F'
                || c == '\u205F' || c == '\u3000';
    }

    private static boolean isLineTerminator(char c) {
        return c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029';
    }

    private static String rewriteMatchable(String s) {
        Matcher m = RULE_FUNC.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String indent = m.group(1);
            String funcName = m.group(2);
            String params = m.group(3).trim();
            String rest = m.group(4);
            String replacement = params.isEmpty()
                    ? indent + "Rule " + funcName + " " + rest
                    : indent + "Rule " + funcName + " given " + params + " " + rest;
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
