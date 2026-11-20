package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.canonicalizer.SyntaxTransformer;
import aster.core.lexicon.CanonicalizationConfig;

import java.util.regex.Pattern;

/**
 * 中文 {@code 结果为 X} → {@code Return X} 重写变换器（翻译后变换器，排在 core {@code result-is} 之后）。
 * <p>
 * 关键词翻译通常已把独立的 {@code 结果为}（RESULT_IS）译成 {@code the result is}，由 core {@code result-is}
 * 改写成 {@code Return}；本变换器只处理翻译后仍留在行首的 {@code 结果为}。
 * <p>
 * 空白只认 ASCII 空格、tab 与 U+3000（aster-lang-ts#348 规则），不跨行：缩进与 {@code 结果为} 之后的空白都是
 * {@code [ \t]} 加 U+3000。其他 Unicode 空白与换行不是词法空白，不能被这里吞掉（aster-lang-locales#147）。
 */
public final class ChineseResultIsTransformer implements SyntaxTransformer {

    public static final ChineseResultIsTransformer INSTANCE = new ChineseResultIsTransformer();

    private static final Pattern CHINESE_RESULT_IS = Pattern.compile(
            "^([ \\t\\u3000]*)结果为[ \\t\\u3000]+",
            Pattern.MULTILINE
    );

    private ChineseResultIsTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return segmenter.replaceOutsideStrings(source, CHINESE_RESULT_IS, "$1Return ");
    }
}
