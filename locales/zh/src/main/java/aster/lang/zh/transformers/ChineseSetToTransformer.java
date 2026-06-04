package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.canonicalizer.SyntaxTransformer;
import aster.core.lexicon.CanonicalizationConfig;

import java.util.regex.Pattern;

/**
 * 中文 {@code 将 X 设为 Y} → {@code Let X be Y} 重写变换器。
 * <p>
 * 将中文"将...设为..."结构翻译为英文赋值语法。
 */
public final class ChineseSetToTransformer implements SyntaxTransformer {

    public static final ChineseSetToTransformer INSTANCE = new ChineseSetToTransformer();

    private static final Pattern CHINESE_SET_TO = Pattern.compile(
            "^(\\s*)\u5C06\\s+([\\p{L}][\\p{L}0-9_]*)\\s+\u8BBE\u4E3A\\s+",
            Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS
    );

    private ChineseSetToTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return segmenter.replaceOutsideStrings(source, CHINESE_SET_TO, "$1Let $2 be ");
    }
}
