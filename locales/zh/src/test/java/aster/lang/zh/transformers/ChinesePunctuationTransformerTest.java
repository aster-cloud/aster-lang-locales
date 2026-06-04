package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.lexicon.CanonicalizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文标点变换器单元测试。
 */
@DisplayName("ChinesePunctuationTransformer")
class ChinesePunctuationTransformerTest {

    private ChinesePunctuationTransformer transformer;
    private CanonicalizationConfig config;
    private StringSegmenter segmenter;

    @BeforeEach
    void setUp() {
        transformer = ChinesePunctuationTransformer.INSTANCE;
        config = CanonicalizationConfig.defaults();
        segmenter = new StringSegmenter("「", "」");
    }

    @Test
    @DisplayName("句号转换：。→ .")
    void testPeriodConversion() {
        String result = transformer.transform("返回 42。", config, segmenter);
        assertThat(result).isEqualTo("返回 42.");
    }

    @Test
    @DisplayName("逗号转换：， → ,")
    void testCommaConversion() {
        String result = transformer.transform("a，b，c", config, segmenter);
        assertThat(result).isEqualTo("a,b,c");
    }

    @Test
    @DisplayName("冒号转换：： → :")
    void testColonConversion() {
        String result = transformer.transform("规则 main：", config, segmenter);
        assertThat(result).isEqualTo("规则 main:");
    }

    @Test
    @DisplayName("顿号转换：、 → ,")
    void testEnumSeparatorConversion() {
        String result = transformer.transform("a、b、c", config, segmenter);
        assertThat(result).isEqualTo("a,b,c");
    }

    @Test
    @DisplayName("混合标点转换")
    void testMixedPunctuation() {
        String result = transformer.transform("模块 测试。\n规则 main：\n  返回 a，b。", config, segmenter);
        assertThat(result).isEqualTo("模块 测试.\n规则 main:\n  返回 a,b.");
    }

    @Test
    @DisplayName("保护字符串字面量")
    void testPreserveStrings() {
        String result = transformer.transform("返回 「句号。逗号，冒号：」。", config, segmenter);
        assertThat(result)
                .contains("「句号。逗号，冒号：」")
                .endsWith(".");
    }

    @Test
    @DisplayName("ASCII 引号字符串也受保护")
    void testPreserveAsciiQuotedStrings() {
        String result = transformer.transform("返回 \"句号。\"。", config, segmenter);
        assertThat(result)
                .contains("\"句号。\"")
                .endsWith(".");
    }
}
