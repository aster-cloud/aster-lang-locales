package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.canonicalizer.SyntaxTransformer;
import aster.core.lexicon.CanonicalizationConfig;

import java.util.regex.Pattern;

/**
 * 中文结构助词"的" → 成员访问符 {@code .} 的变换器。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>带空格：{@code 用户 的 名字} → {@code 用户.名字}</li>
 *   <li>无空格：{@code 用户的名字} → {@code 用户.名字}</li>
 * </ul>
 */
public final class ChinesePossessiveTransformer implements SyntaxTransformer {

    public static final ChinesePossessiveTransformer INSTANCE = new ChinesePossessiveTransformer();

    /** CJK 字符之间的"的"（无空格模式） */
    private static final Pattern NO_SPACE = Pattern.compile(
            "([\\p{IsHan}]{2,})\u7684([\\p{IsHan}]{2,})"
    );

    private ChinesePossessiveTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return segmenter.transformOutsideStrings(source, text -> {
            // 带空格模式
            String s = text.replace(" \u7684 ", ".");
            // 无空格模式
            s = NO_SPACE.matcher(s).replaceAll("$1.$2");
            return s;
        });
    }
}
