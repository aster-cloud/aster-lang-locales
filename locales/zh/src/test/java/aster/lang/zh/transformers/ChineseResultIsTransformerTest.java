package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.lexicon.CanonicalizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文"结果为 X" → "Return X" 变换器单元测试。
 */
@DisplayName("ChineseResultIsTransformer")
class ChineseResultIsTransformerTest {

    private ChineseResultIsTransformer transformer;
    private CanonicalizationConfig config;
    private StringSegmenter segmenter;

    @BeforeEach
    void setUp() {
        transformer = ChineseResultIsTransformer.INSTANCE;
        config = CanonicalizationConfig.defaults();
        segmenter = new StringSegmenter("「", "」");
    }

    @Test
    @DisplayName("基本转换：结果为 42 → Return 42")
    void testBasicResultIs() {
        String result = transformer.transform("结果为 42", config, segmenter);
        assertThat(result).contains("Return 42");
    }

    @Test
    @DisplayName("带缩进的转换")
    void testWithIndentation() {
        String result = transformer.transform("  结果为 计算值", config, segmenter);
        assertThat(result).startsWith("  Return ");
    }

    @Test
    @DisplayName("带表达式的转换")
    void testWithExpression() {
        String result = transformer.transform("结果为 x + y", config, segmenter);
        assertThat(result).contains("Return x + y");
    }

    @Test
    @DisplayName("保护字符串字面量")
    void testPreserveStrings() {
        String result = transformer.transform("返回 「结果为 X」", config, segmenter);
        assertThat(result).contains("「结果为 X」");
    }

    @Test
    @DisplayName("非'结果为'行不受影响")
    void testNonResultIsLineUnaffected() {
        String input = "返回 42";
        String result = transformer.transform(input, config, segmenter);
        assertThat(result).isEqualTo(input);
    }
}
