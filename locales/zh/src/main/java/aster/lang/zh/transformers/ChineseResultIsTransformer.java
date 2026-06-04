package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.canonicalizer.SyntaxTransformer;
import aster.core.lexicon.CanonicalizationConfig;

import java.util.regex.Pattern;

/**
 * 中文 {@code 结果为 X} → {@code Return X} 重写变换器。
 * <p>
 * 将中文"结果为"表达式翻译为英文 Return 语句。
 */
public final class ChineseResultIsTransformer implements SyntaxTransformer {

    public static final ChineseResultIsTransformer INSTANCE = new ChineseResultIsTransformer();

    private static final Pattern CHINESE_RESULT_IS = Pattern.compile(
            "^(\\s*)\u7ED3\u679C\u4E3A\\s+",
            Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS
    );

    private ChineseResultIsTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return segmenter.replaceOutsideStrings(source, CHINESE_RESULT_IS, "$1Return ");
    }
}
