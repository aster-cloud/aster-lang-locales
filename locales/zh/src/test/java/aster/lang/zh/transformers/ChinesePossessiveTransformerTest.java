package aster.lang.zh.transformers;

import aster.core.canonicalizer.StringSegmenter;
import aster.core.lexicon.CanonicalizationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文"的"所有格变换器单元测试。
 */
@DisplayName("ChinesePossessiveTransformer")
class ChinesePossessiveTransformerTest {

    private ChinesePossessiveTransformer transformer;
    private CanonicalizationConfig config;
    private StringSegmenter segmenter;

    @BeforeEach
    void setUp() {
        transformer = ChinesePossessiveTransformer.INSTANCE;
        config = CanonicalizationConfig.defaults();
        segmenter = new StringSegmenter("「", "」");
    }

    @Test
    @DisplayName("有空格模式：用户 的 名字 → 用户.名字")
    void testWithSpace() {
        String result = transformer.transform("用户 的 名字", config, segmenter);
        assertThat(result).isEqualTo("用户.名字");
    }

    @Test
    @DisplayName("无空格模式：用户的名字 → 用户.名字")
    void testNoSpace() {
        String result = transformer.transform("用户的名字", config, segmenter);
        assertThat(result).isEqualTo("用户.名字");
    }

    @Test
    @DisplayName("单字前缀不触发（CJK 字符需 >= 2 个）")
    void testSingleCharPrefixNotTriggered() {
        String result = transformer.transform("我的结构体", config, segmenter);
        assertThat(result).isEqualTo("我的结构体");
    }

    @Test
    @DisplayName("单字后缀不触发")
    void testSingleCharSuffixNotTriggered() {
        String result = transformer.transform("用户的名", config, segmenter);
        // 后缀"名"只有1个CJK字符，不匹配 NO_SPACE 模式
        assertThat(result).isEqualTo("用户的名");
    }

    @Test
    @DisplayName("保护字符串字面量")
    void testPreserveStrings() {
        String result = transformer.transform("返回 「用户的名字」", config, segmenter);
        assertThat(result).contains("「用户的名字」");
    }

    @Test
    @DisplayName("多个'的'都能转换")
    void testMultiplePossessives() {
        String result = transformer.transform("驾驶员 的 车辆 的 型号", config, segmenter);
        assertThat(result).isEqualTo("驾驶员.车辆.型号");
    }
}
