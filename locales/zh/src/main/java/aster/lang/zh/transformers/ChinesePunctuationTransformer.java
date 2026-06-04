package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.canonicalizer.SyntaxTransformer;
import aster.core.lexicon.CanonicalizationConfig;

/**
 * 中文标点 → 英文标点变换器（ANTLR 词法器只识别英文标点）。
 * <p>
 * 转换规则：
 * <ul>
 *   <li>{@code 。} (U+3002) → {@code .}</li>
 *   <li>{@code ，} (U+FF0C) → {@code ,}</li>
 *   <li>{@code ：} (U+FF1A) → {@code :}</li>
 *   <li>{@code 、} (U+3001) → {@code ,}</li>
 * </ul>
 */
public final class ChinesePunctuationTransformer implements SyntaxTransformer {

    public static final ChinesePunctuationTransformer INSTANCE = new ChinesePunctuationTransformer();

    private ChinesePunctuationTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return segmenter.transformOutsideStrings(source, ChinesePunctuationTransformer::translatePunctuation);
    }

    private static String translatePunctuation(String s) {
        StringBuilder result = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\u3002' -> result.append('.');     // 。→ .
                case '\uFF0C' -> result.append(',');     // ， → ,
                case '\uFF1A' -> result.append(':');     // ： → :
                case '\u3001' -> result.append(',');     // 、 → ,
                default -> result.append(ch);
            }
        }
        return result.toString();
    }
}
