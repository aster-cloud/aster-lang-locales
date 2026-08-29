package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.lexicon.CanonicalizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("非函数行不受影响")
    void testNonFunctionLineUnaffected() {
        String input = "令 x 为 10";
        String result = transformer.transform(input, config, segmenter);
        assertThat(result).isEqualTo(input);
    }
}
