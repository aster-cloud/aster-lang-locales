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
 *   <li>{@code 规则 funcName（params）：} → {@code Rule funcName given params:}</li>
 * </ul>
 */
public final class ChineseFunctionSyntaxTransformer implements SyntaxTransformer {

    public static final ChineseFunctionSyntaxTransformer INSTANCE = new ChineseFunctionSyntaxTransformer();

    /** 匹配"规则 funcName（params）：" */
    private static final Pattern RULE_FUNC = Pattern.compile(
            "^(\\s*)(?:\u89C4\u5219|Rule)\\s+([\\p{L}][\\p{L}0-9_]*)\\s*\\(([^)]*?)\\)\\s*(.*)$",
            Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS
    );

    private ChineseFunctionSyntaxTransformer() {}

    @Override
    public String transform(String source, CanonicalizationConfig config, StringSegmenter segmenter) {
        return rewriteRuleFunc(source);
    }

    private String rewriteRuleFunc(String s) {
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
