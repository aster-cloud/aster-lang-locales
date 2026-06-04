package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.lexicon.CanonicalizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文运算符变换器单元测试。
 */
@DisplayName("ChineseOperatorTransformer")
class ChineseOperatorTransformerTest {

    private ChineseOperatorTransformer transformer;
    private CanonicalizationConfig config;
    private StringSegmenter segmenter;

    @BeforeEach
    void setUp() {
        transformer = ChineseOperatorTransformer.INSTANCE;
        config = CanonicalizationConfig.defaults();
        segmenter = new StringSegmenter("「", "」");
    }

    @Test
    @DisplayName("大于等于 → >=")
    void testGreaterThanOrEqual() {
        String result = transformer.transform("x 大于等于 10", config, segmenter);
        assertThat(result).contains(">=");
    }

    @Test
    @DisplayName("小于等于 → <=")
    void testLessThanOrEqual() {
        String result = transformer.transform("x 小于等于 10", config, segmenter);
        assertThat(result).contains("<=");
    }

    @Test
    @DisplayName("不等于 → !=")
    void testNotEquals() {
        String result = transformer.transform("x 不等于 y", config, segmenter);
        assertThat(result).contains("!=");
    }

    @Test
    @DisplayName("等于 → ==")
    void testEquals() {
        String result = transformer.transform("x 等于 y", config, segmenter);
        assertThat(result).contains("==");
    }

    @Test
    @DisplayName("则 在行尾 → :")
    void testThenAtLineEnd() {
        String result = transformer.transform("如果 条件 则\n  返回 真", config, segmenter);
        assertThat(result).contains(":");
        assertThat(result).doesNotContain("则");
    }

    @Test
    @DisplayName("保护字符串字面量")
    void testPreserveStrings() {
        String result = transformer.transform("返回 「大于等于」", config, segmenter);
        assertThat(result).contains("「大于等于」");
    }

    @Test
    @DisplayName("长运算符优先于短运算符（大于等于 vs 等于）")
    void testLongOperatorPriority() {
        String result = transformer.transform("x 大于等于 10", config, segmenter);
        assertThat(result).contains(">=");
        assertThat(result).doesNotContain("==");
    }
}
