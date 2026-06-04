package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.lexicon.CanonicalizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文"将 X 设为 Y" → "Let X be Y" 变换器单元测试。
 */
@DisplayName("ChineseSetToTransformer")
class ChineseSetToTransformerTest {

    private ChineseSetToTransformer transformer;
    private CanonicalizationConfig config;
    private StringSegmenter segmenter;

    @BeforeEach
    void setUp() {
        transformer = ChineseSetToTransformer.INSTANCE;
        config = CanonicalizationConfig.defaults();
        segmenter = new StringSegmenter("「", "」");
    }

    @Test
    @DisplayName("基本转换：将 x 设为 42 → Let x be 42")
    void testBasicSetTo() {
        String result = transformer.transform("将 x 设为 42", config, segmenter);
        assertThat(result).contains("Let x be 42");
    }

    @Test
    @DisplayName("带缩进的转换")
    void testWithIndentation() {
        String result = transformer.transform("  将 total 设为 100", config, segmenter);
        assertThat(result).startsWith("  Let total be ");
    }

    @Test
    @DisplayName("中文变量名")
    void testChineseVarName() {
        String result = transformer.transform("将 总价 设为 计算结果", config, segmenter);
        assertThat(result).contains("Let 总价 be 计算结果");
    }

    @Test
    @DisplayName("保护字符串字面量")
    void testPreserveStrings() {
        String result = transformer.transform("返回 「将 x 设为 y」", config, segmenter);
        assertThat(result).contains("「将 x 设为 y」");
    }

    @Test
    @DisplayName("非'将...设为...'行不受影响")
    void testNonSetToLineUnaffected() {
        String input = "令 x 为 10";
        String result = transformer.transform(input, config, segmenter);
        assertThat(result).isEqualTo(input);
    }
}
