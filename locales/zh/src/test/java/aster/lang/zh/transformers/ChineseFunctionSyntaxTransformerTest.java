package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.lexicon.CanonicalizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文函数语法重排变换器单元测试。
 */
@DisplayName("ChineseFunctionSyntaxTransformer")
class ChineseFunctionSyntaxTransformerTest {

    private ChineseFunctionSyntaxTransformer transformer;
    private CanonicalizationConfig config;
    private StringSegmenter segmenter;

    @BeforeEach
    void setUp() {
        transformer = ChineseFunctionSyntaxTransformer.INSTANCE;
        config = CanonicalizationConfig.defaults();
        segmenter = new StringSegmenter("「", "」");
    }

    @Test
    @DisplayName("字符串字面量内的「规则 X(...)」不得被改写（issue #82）")
    void stringLiteralContentIsNotRewritten() {
        // 本类此前是 7 个中文变换器里**唯一**忽略 segmenter 的——直接对整段源码跑正则。
        //
        // ★如实标注：这一条**不是**变异杀手。实测撤掉 segmenter 后本用例仍绿——
        //   因为 RULE_FUNC 锚定行首（^），而这里的「规则 inner」前面还有「返回 「」，
        //   本就匹配不上。真正能杀死变异的是下面的多行用例。
        //   保留本条是作为「常见写法不受影响」的正向覆盖，不冒充回归守卫。
        String src = "规则 outer(x: Text):\n  返回 「规则 inner(a: Int):」。";
        String result = transformer.transform(src, config, segmenter);

        assertThat(result)
            .as("字符串外的声明应被改写")
            .contains("Rule outer given x: Text");
        assertThat(result)
            .as("字符串**内**的内容必须原样保留，不得被改写成 Rule ... given ...")
            .contains("「规则 inner(a: Int):」");
    }

    @Test
    @DisplayName("★多行字符串里自成一行的「规则 …」同样受保护")
    void multilineStringLiteralIsNotRewritten() {
        // RULE_FUNC 是 MULTILINE 且锚定行首——多行字符串里自成一行的内容最危险。
        String src = "规则 doc(x: Text):\n  返回 「\n规则 fake(y: Int):\n」。";
        String result = transformer.transform(src, config, segmenter);

        assertThat(result).contains("Rule doc given x: Text");
        assertThat(result)
            .as("多行字符串内的行首「规则 …」不得被改写")
            .contains("规则 fake(y: Int):");
        assertThat(result)
            .as("不得出现由字符串内容改写出的 Rule fake")
            .doesNotContain("Rule fake");
    }

    @Test
    @DisplayName("规则+参数 → Rule+given")
    void testRuleWithParams() {
        String result = transformer.transform("规则 greet(name: Text):", config, segmenter);
        assertThat(result).contains("Rule greet given name: Text");
    }

    @Test
    @DisplayName("无参数函数")
    void testRuleWithoutParams() {
        String result = transformer.transform("规则 hello():", config, segmenter);
        assertThat(result).contains("Rule hello");
        assertThat(result).doesNotContain("given");
    }

    @Test
    @DisplayName("保留缩进")
    void testPreserveIndentation() {
        String result = transformer.transform("  规则 greet(name: Text):", config, segmenter);
        assertThat(result).startsWith("  Rule");
    }

    @Test
    @DisplayName("多参数函数")
    void testMultipleParams() {
        String result = transformer.transform("规则 add(a: Int, b: Int):", config, segmenter);
        assertThat(result).contains("Rule add given a: Int, b: Int");
    }

    @Test
    @DisplayName("中文函数名")
    void testChineseFunctionName() {
        String result = transformer.transform("规则 计算(值: Int):", config, segmenter);
        assertThat(result).contains("Rule 计算 given 值: Int");
    }

    @Test
    @DisplayName("全角括号 规则 计算（甲: 整数）： → Rule 计算 given 甲: 整数（issue #91）")
    void testFullWidthParentheses() {
        // 中文输入法默认输出全角括号，而 core 的 fullWidthToHalf 在变换器链**之后**才执行，
        // 所以变换器自己必须认识「（）」，否则全角写法永远匹配不上、语法糖静默失效。
        String result = transformer.transform("规则 计算（甲: 整数）：", config, segmenter);
        assertThat(result).contains("Rule 计算 given 甲: 整数");
        assertThat(result).doesNotContain("（").doesNotContain("）");
    }

    @Test
    @DisplayName("全角括号无参数函数")
    void testFullWidthParenthesesWithoutParams() {
        String result = transformer.transform("规则 你好（）：", config, segmenter);
        assertThat(result).contains("Rule 你好");
        assertThat(result).doesNotContain("given");
    }

    @Test
    @DisplayName("全角/半角括号混用同样重排")
    void testMixedWidthParentheses() {
        String result = transformer.transform("规则 计算（甲: 整数):", config, segmenter);
        assertThat(result).contains("Rule 计算 given 甲: 整数");
    }

    @Test
    @DisplayName("全角空格（U+3000）同样视为行内空白")
    void testIdeographicSpace() {
        String result = transformer.transform("规则\u3000折扣（甲 作为 整数）：", config, segmenter);
        assertThat(result).isEqualTo("Rule 折扣 given 甲 作为 整数 ：");
    }

    @Test
    @DisplayName("参数跨行时照旧重排：首尾换行被 trim 掉，单参数换行写法可编译")
    void testMultilineHeaderKeepsLegacyRewrite() {
        String result = transformer.transform("规则 折扣（\n  甲 作为 整数）：", config, segmenter);
        assertThat(result).isEqualTo("Rule 折扣 given 甲 作为 整数 ：");
    }

    @Test
    @DisplayName("★与旧模式结果逐字相同（随机输入，覆盖各类 Unicode 空白与行终止符）")
    void testEquivalentToLegacyPattern() {
        // 旧模式在空行与未闭合函数头上会二次回溯，新实现须只改性能、不改结果。
        Pattern legacy = Pattern.compile(
                "^(\\s*)(?:规则|Rule)\\s+([\\p{L}][\\p{L}0-9_]*)\\s*[(（]([^)）]*?)[)）]\\s*(.*)$",
                Pattern.MULTILINE | Pattern.UNICODE_CHARACTER_CLASS);
        Random random = new Random(236);
        int rewritten = 0;
        for (int i = 0; i < 50_000; i++) {
            String input = FuzzSource.next(random);
            String expected = legacyRewrite(legacy, input);
            assertThat(ChineseFunctionSyntaxTransformer.rewriteRuleFunc(input))
                    .as("输入 %s", input.codePoints().mapToObj(c -> String.format("U+%04X", c)).toList())
                    .isEqualTo(expected);
            if (!expected.equals(input)) {
                rewritten++;
            }
        }
        assertThat(rewritten).as("随机输入中须有足够多真正发生重排的用例").isGreaterThan(5_000);
    }

    /** 生成函数头形状的随机源码：每个槽位随机取空白、行终止符或干扰字符，并随机拼接噪声行。 */
    static final class FuzzSource {
        private static final String[] SPACE = {"", " ", "  ", "\t", "\u000B", "\u000C", "\u00A0", "\u3000", "\uFEFF",
                "\n", "\r", "\r\n", "\u0085", "\u2028", "\u2029", " \n "};
        private static final String[] KEYWORD = {"规则", "Rule", "规", "rule"};
        private static final String[] NAME = {"折扣", "a", "b1", "_x", "甲乙", "1a", ""};
        private static final String[] OPEN = {"(", "（", "", "(("};
        private static final String[] CLOSE = {")", "）", "", "))"};
        private static final String[] PARAM = {"甲 作为 整数", "a", "甲，乙", "a, b", "「x)」", "\"y)\"", "", "甲\n乙"};
        private static final String[] REST = {"：", ":", "，产出 整数：", "", "  返回 甲。", ")：", "「」"};
        private static final String[] NOISE = {"令 x 为 10", "返回 「规则 f(a)：」。", "  ", "", ")", "（"};

        static String next(Random random) {
            StringBuilder sb = new StringBuilder();
            for (int line = 1 + random.nextInt(3); line > 0; line--) {
                if (random.nextInt(4) == 0) {
                    sb.append(pick(random, NOISE));
                } else {
                    sb.append(pick(random, SPACE)).append(pick(random, KEYWORD)).append(pick(random, SPACE))
                            .append(pick(random, NAME)).append(pick(random, SPACE)).append(pick(random, OPEN))
                            .append(pick(random, SPACE)).append(pick(random, PARAM)).append(pick(random, SPACE))
                            .append(pick(random, CLOSE)).append(pick(random, SPACE)).append(pick(random, REST));
                }
                sb.append(pick(random, SPACE));
            }
            return sb.toString();
        }

        private static String pick(Random random, String[] options) {
            return options[random.nextInt(options.length)];
        }
    }

    private static String legacyRewrite(Pattern legacy, String s) {
        Matcher m = legacy.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String params = m.group(3).trim();
            String replacement = params.isEmpty()
                    ? m.group(1) + "Rule " + m.group(2) + " " + m.group(4)
                    : m.group(1) + "Rule " + m.group(2) + " given " + params + " " + m.group(4);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Test
    @DisplayName("空行与空白行原样保留，只改写函数头所在行")
    void testBlankLinesPreserved() {
        String result = transformer.transform("\n  \n规则 折扣（甲 作为 整数）：\n", config, segmenter);
        assertThat(result).isEqualTo("\n  \nRule 折扣 given 甲 作为 整数 ：\n");
    }

    @Test
    @DisplayName("★大量空白行或未闭合函数头时耗时线性（防正则二次回溯）")
    void testAdversarialInputIsLinear() {
        // 旧模式 ^(\s*) 与跨行参数组在这两类输入上随长度平方增长。
        String blank = " \n".repeat(64_000);
        String unclosed = "规则 a(\n".repeat(16_000);
        long start = System.nanoTime();
        assertThat(transformer.transform(blank, config, segmenter)).isEqualTo(blank);
        assertThat(transformer.transform(unclosed, config, segmenter)).isEqualTo(unclosed);
        // 末尾有右括号时不走截断捷径，仍须线性。
        assertThat(transformer.transform(blank + ")", config, segmenter)).isEqualTo(blank + ")");
        assertThat(transformer.transform(unclosed + ")", config, segmenter)).startsWith("Rule a given ");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 旧模式约 54 秒 + 10 秒；5 秒上限留足并行负载余量。
        assertThat(elapsedMs).as("线性实现应在 5 秒内完成，实际 %d ms", elapsedMs).isLessThan(5_000);
    }

    @Test
    @DisplayName("非函数行不受影响")
    void testNonFunctionLineUnaffected() {
        String input = "令 x 为 10";
        String result = transformer.transform(input, config, segmenter);
        assertThat(result).isEqualTo(input);
    }
}
