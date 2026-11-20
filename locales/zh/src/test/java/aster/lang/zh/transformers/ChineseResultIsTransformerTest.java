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

    @Test
    @DisplayName("空白只认空格、tab 与 U+3000，不跨行（aster-lang-locales#147）")
    void testOnlyLexicalWhitespace() {
        assertThat(transformer.transform("\t结果为\tx", config, segmenter)).isEqualTo("\tReturn x");
        assertThat(transformer.transform("\u3000\u3000结果为\u3000x", config, segmenter)).isEqualTo("\u3000\u3000Return x");
        for (String input : new String[]{"结果为\u00A0x", "结果为\u2003x", "结果为\u000Bx", "结果为\fx",
                "结果为\nx", "\u00A0结果为 x", "\n\n结果为\n  x"}) {
            assertThat(transformer.transform(input, config, segmenter)).as(input).isEqualTo(input);
        }
        // 空行之后的行首照常改写（缩进不跨行吞掉前面的空行）
        assertThat(transformer.transform("\n\n  结果为 x", config, segmenter)).isEqualTo("\n\n  Return x");
    }
}
