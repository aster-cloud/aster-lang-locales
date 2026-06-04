package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.canonicalizer.SyntaxTransformer;
import aster.core.lexicon.CanonicalizationConfig;

/**
 * 中文运算符和控制流关键词变换器（v2 关键字）。
 * <p>
 * 翻译规则：
 * <ul>
 *   <li>{@code 大于等于} → {@code >=}</li>
 *   <li>{@code 小于等于} → {@code <=}</li>
 *   <li>{@code 不等于} → {@code !=}</li>
 *   <li>{@code 等于} → {@code ==}</li>
 *   <li>{@code 则} (行尾) → {@code :}</li>
 *   <li>{@code 设置 X 为 Y} → {@code 令 X 定义为 Y}（v2: BE='定义为'）</li>
 * </ul>
 * <p>
 * 注意：不再自动添加句号。中文用户使用「。」作为语句终止符，
 * {@link ChinesePunctuationTransformer} 已在更早阶段将其转换为「.」。
 */
public final class ChineseOperatorTransformer implements SyntaxTransformer {

    public static final ChineseOperatorTransformer INSTANCE = new ChineseOperatorTransformer();

    private ChineseOperatorTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return segmenter.transformOutsideStrings(source, ChineseOperatorTransformer::translateOperators);
    }

    private static String translateOperators(String s) {
        // 比较运算符（先长后短，避免部分匹配）
        s = s.replace("大于等于", " >= ")
             .replace("小于等于", " <= ")
             .replace("不等于", " != ")
             .replace("等于", " == ");

        // "则" 在行尾 → ":"
        s = s.replaceAll("\\s+则\\s*$", ":");
        s = s.replaceAll("\\s+则\\s*\\n", ":\n");

        // "设置 X 为 Y" → "令 X 定义为 Y"（v2: BE='定义为'；sugar 输入仍接受 v1 风格的'为'）
        // 注意：[^为]+ 非贪婪匹配，因为我们要在第一个'为'处停止
        s = s.replaceAll("设置\\s+([^为]+)\\s+为\\s+",
                "令 $1 定义为 ");

        return s;
    }
}
