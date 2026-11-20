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
     * 词法空白只认 ASCII 空格、tab 与 zh-CN 的 U+3000（aster-lang-ts#348 规则；U+3000 随后由全角转半角换成空格）：
     * 函数头各处的空白（行首缩进、关键词与名字之间、名字与括号之间、右括号之后）都只认 {@code SP}，不跨行。
     * 不认其他 Unicode 空白与换行：否则 {@code 规则<U+2003>f（…）}、{@code f<NBSP>（}、{@code ）<U+2003>，}
     * 以及在关键词、名字、括号之间换行的写法会被重排成合法函数头，绕过词法的空白规则（aster-lang-locales#147）。
     * 这些字符原样留下，由词法报 E050；换行留给语法报错。
     * <ul>
     *   <li>{@code LT} = Java 行终止符：{@code (?<![^LT])} 即行首，末尾 {@code [^LT]*} 取该行剩余部分；</li>
     *   <li>参数组 {@code [^)）]*?} 不变（可含换行与任意字符），与 aster-lang-ts 的同名变换器逐字一致。</li>
     * </ul>
     */
    private static final String SP = " \\t\\u3000";
    private static final String LT = "\\n\\r\\u0085\\u2028\\u2029";
    private static final Pattern RULE_FUNC = Pattern.compile(
            "(?<![^" + LT + "])([" + SP + "]*)(?:规则|Rule)[" + SP + "]+([\\p{L}][\\p{L}0-9_]*)[" + SP + "]*"
                    + "[(（]([^)）]*?)[)）][" + SP + "]*([^" + LT + "]*)"
    );

    private ChineseFunctionSyntaxTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        // ★必须经 segmenter，只改写字符串**之外**的片段（issue #82）。
        //   本类此前是 7 个中文变换器里唯一忽略 segmenter 的——直接对整段源码跑正则，
        //   于是字符串字面量里形如 `规则 X(...)` 的内容会被当作函数声明改写。
        //   RULE_FUNC 锚定行首，多行字符串里自成一行的内容尤其危险。
        //   同目录另外 5 个变换器（Punctuation/Possessive/Operator/ResultIs/LetBe）
        //   一律走 transformOutsideStrings 或 replaceOutsideStrings。
        return segmenter.transformOutsideStrings(source, ChineseFunctionSyntaxTransformer::rewriteRuleFunc);
    }

    /**
     * 只对「最后一个右括号所在函数头」及其之前的部分跑正则。
     * <p>
     * 每个匹配都必须含右括号，且以右括号后同一行的剩余部分收尾，所以截断点之后不可能有匹配，
     * 之前的匹配也不会越过截断点——结果与整段匹配相同。不截断时，最后一个右括号之后每个
     * {@code 规则 名(} 行首都会让惰性参数组扫到片段末尾再失败，耗时随源码长度平方增长
     * （1.6 万个未闭合函数头约 10 秒）。
     */
    static String rewriteRuleFunc(String s) {
        int cut = matchableEnd(s);
        return cut == 0 ? s : rewriteMatchable(s.substring(0, cut)) + s.substring(cut);
    }

    /**
     * 最后一个右括号之后到下一个行终止符（或片段末尾）为止；无右括号时为 0。右括号之后的 {@code SP} 与该行剩余部分
     * 都不含行终止符，所以匹配不会越过这一行。
     */
    static int matchableEnd(String s) {
        int i = Math.max(s.lastIndexOf(')'), s.lastIndexOf('\uFF09'));
        if (i < 0) {
            return 0;
        }
        i++;
        while (i < s.length() && !isLineTerminator(s.charAt(i))) {
            i++;
        }
        return i;
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
