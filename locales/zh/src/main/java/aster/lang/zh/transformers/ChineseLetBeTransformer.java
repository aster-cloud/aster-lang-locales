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
     * 匹配 {@code 令 X 定义为 Y} 模式（v2 关键字）。
     * <p>
     * X 可以是中文标识符（字母、数字、下划线），不贪婪匹配到第一个 {@code 定义为}。
     */
    private static final Pattern CHINESE_LET_BE = Pattern.compile(
            "^(\\s*)令\\s+([\\p{L}][\\p{L}0-9_]*)\\s+定义为\\s+",
            Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS
    );

    private ChineseLetBeTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return segmenter.replaceOutsideStrings(source, CHINESE_LET_BE, "$1Let $2 be ");
    }
}
