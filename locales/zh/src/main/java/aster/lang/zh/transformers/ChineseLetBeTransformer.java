package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.canonicalizer.SyntaxTransformer;
import aster.core.lexicon.CanonicalizationConfig;

import java.util.regex.Pattern;

/**
 * 中文 {@code 令 X 定义为 Y} → {@code Let X be Y} 重写变换器（v2 关键字）。
 * <p>
 * 将中文"令...定义为..."变量绑定语法翻译为英文 Let...be... 语法。
 * <p>
 * v2 设计说明：原 v1 BE='为' 与 WHEN='为' 共享导致需要预翻译消歧；
 * v2 下 BE='定义为'（多字），WHEN='当'（多字），二者无字面冲突。本 transformer
 * 仍保留作为 fast-path，让 'Let X be ...' 输出更整洁；逻辑层面已经不需要
 * 它做消歧（关键词翻译表能正确处理）。
 */
public final class ChineseLetBeTransformer implements SyntaxTransformer {

    public static final ChineseLetBeTransformer INSTANCE = new ChineseLetBeTransformer();

    /**
     * 中文词法空白：ASCII 空格、制表符与全角空格 U+3000（aster-lang-ts#348 的语言规则）。
     * <p>
     * 本变换器在 preTranslationTransformers 链里执行，早于 core 的全角转半角，所以 U+3000 此时仍是原样，须显式列出。
     * 其余 Unicode 空白（U+00A0、U+2000–U+200A 等）不是空白，由词法器报 E050；换行也不属于空白，
     * 绑定不得跨行。
     */
    private static final String WS = "[ \\t\\u3000]";

    /**
     * 匹配 {@code 令 X 定义为 Y} 模式（v2 关键字）。
     * <p>
     * X 可以是中文标识符（字母、数字、下划线），不贪婪匹配到第一个 {@code 定义为}；也可以是名字转义 {@code `x`}（ADR 0064），
     * 转义按捕获组原样带到 {@code Let `x` be}（core 的分段器只放行整个包含转义的匹配，转义内容不被改动）。
     * <p>
     * 此前用 {@code UNICODE_CHARACTER_CLASS} 下的 {@code \s}，它匹配任意 Unicode 空白与换行：
     * {@code 令 X 定义为 Y} 里的 U+00A0 被当作空白吞掉、{@code 定义为} 后换行也能匹配，Java 接受 ts 报 E050 / E055 的源码
     * （aster-lang-locales#143）。空白收紧到 {@link #WS}，与词法器一致。
     */
    private static final Pattern CHINESE_LET_BE = Pattern.compile(
            "^(" + WS + "*)令" + WS + "+(`[^`\\r\\n]+`|[\\p{L}][\\p{L}0-9_]*)" + WS + "+定义为" + WS + "+",
            Pattern.MULTILINE
    );

    private ChineseLetBeTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return segmenter.replaceOutsideStrings(source, CHINESE_LET_BE, "$1Let $2 be ");
    }
}
