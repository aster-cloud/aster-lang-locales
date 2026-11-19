package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.lexicon.CanonicalizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文 "令 X 定义为 Y" → "Let X be Y" 变换器单元测试。
 * <p>
 * 空白只认 ASCII 空格、制表符与 U+3000（aster-lang-locales#143，语言规则见 aster-lang-ts#348）：
 * 其余 Unicode 空白与换行不匹配，原样留给词法器（报 E050）或解析器（绑定不跨行）。
 * 跨引擎的拒绝码由 aster-lang-core 的 parse diagnostics parity 夹具与 aster-lang-test 的 tier1-reject 样本锁定。
 */
@DisplayName("ChineseLetBeTransformer")
class ChineseLetBeTransformerTest {

    private ChineseLetBeTransformer transformer;
    private CanonicalizationConfig config;
    private StringSegmenter segmenter;

    @BeforeEach
    void setUp() {
        transformer = ChineseLetBeTransformer.INSTANCE;
        config = CanonicalizationConfig.defaults();
        segmenter = new StringSegmenter("「", "」");
    }

    @Test
    @DisplayName("基本转换：令 x 定义为 42 → Let x be 42")
    void testBasicLetBe() {
        assertThat(transformer.transform("令 x 定义为 42", config, segmenter)).isEqualTo("Let x be 42");
    }

    @Test
    @DisplayName("缩进原样保留")
    void testIndentationPreserved() {
        assertThat(transformer.transform("  令 总数 定义为 x", config, segmenter)).isEqualTo("  Let 总数 be x");
    }

    @Test
    @DisplayName("U+3000 与制表符是空白（变换器早于全角转半角执行）")
    void testIdeographicSpaceAndTab() {
        assertThat(transformer.transform("　 令　总数\t定义为　x", config, segmenter))
            .isEqualTo("　 Let 总数 be x");
    }

    @Test
    @DisplayName("其余 Unicode 空白不是空白：不变换")
    void testOtherUnicodeWhitespaceNotMatched() {
        for (char ws : new char[] { '\u00A0', '\u2003', '\u2009', '\u202F', '\u205F', '\u1680', '\uFEFF' }) {
            for (String input : new String[] {
                "令" + ws + "总数 定义为 x",
                "令 总数" + ws + "定义为 x",
                "令 总数 定义为" + ws + "x",
                ws + "令 总数 定义为 x",
            }) {
                assertThat(transformer.transform(input, config, segmenter)).as(input).isEqualTo(input);
            }
        }
    }

    @Test
    @DisplayName("绑定不跨行：定义为 前后的换行不匹配")
    void testNoMatchAcrossLines() {
        for (String input : new String[] { "令 总数 定义为\n  x", "令 总数\n  定义为 x", "令\n总数 定义为 x" }) {
            assertThat(transformer.transform(input, config, segmenter)).as(input).isEqualTo(input);
        }
    }

    @Test
    @DisplayName("保护字符串字面量")
    void testPreserveStrings() {
        assertThat(transformer.transform("返回 「令 x 定义为 y」", config, segmenter)).isEqualTo("返回 「令 x 定义为 y」");
    }
}
