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
