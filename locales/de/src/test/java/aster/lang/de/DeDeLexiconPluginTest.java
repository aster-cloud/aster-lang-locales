package aster.lang.de;

import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconPlugin;
import aster.core.lexicon.PunctuationConfig;
import aster.core.lexicon.SemanticTokenKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 德语语言包插件冒烟测试。
 * <p>
 * 验证 SPI 发现、JSON 加载、关键词完整性和标点配置。
 */
@DisplayName("DeDeLexiconPlugin 冒烟测试")
class DeDeLexiconPluginTest {

    private static Lexicon lexicon;

    @BeforeAll
    static void loadPlugin() {
        LexiconPlugin plugin = ServiceLoader.load(LexiconPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p instanceof DeDeLexiconPlugin)
                .findFirst()
                .orElseThrow(() -> new AssertionError("DeDeLexiconPlugin 未通过 SPI 发现"));
        lexicon = plugin.createLexicon();
    }

    @Test
    @DisplayName("SPI 能发现 DeDeLexiconPlugin")
    void testPluginDiscoveredViaSpi() {
        assertThat(lexicon).isNotNull();
    }

    @Test
    @DisplayName("词法表 ID 和元数据正确")
    void testLexiconIdAndMeta() {
        assertThat(lexicon.getId()).isEqualTo("de-DE");
        assertThat(lexicon.getName()).isNotBlank();
        assertThat(lexicon.getDirection()).isEqualTo(Lexicon.Direction.LTR);
    }

    @Test
    @DisplayName("所有 SemanticTokenKind 都有关键词映射")
    void testAllKeywordsMapped() {
        Map<SemanticTokenKind, String> keywords = lexicon.getKeywords();
        for (SemanticTokenKind kind : SemanticTokenKind.values()) {
            assertThat(keywords)
                    .as("缺少 %s 的关键词映射", kind)
                    .containsKey(kind);
            assertThat(keywords.get(kind))
                    .as("%s 的关键词值不应为空", kind)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("关键词抽样验证")
    void testKeywordSamples() {
        Map<SemanticTokenKind, String> kw = lexicon.getKeywords();
        assertThat(kw.get(SemanticTokenKind.IF)).isEqualToIgnoringCase("wenn");
        assertThat(kw.get(SemanticTokenKind.MODULE_DECL)).isEqualToIgnoringCase("Modul");
    }

    @Test
    @DisplayName("标点符号配置非空")
    void testPunctuationConfig() {
        PunctuationConfig punct = lexicon.getPunctuation();
        assertThat(punct.statementEnd()).isNotBlank();
        assertThat(punct.listSeparator()).isNotBlank();
        assertThat(punct.blockStart()).isNotBlank();
        assertThat(punct.stringQuoteOpen()).isNotBlank();
        assertThat(punct.stringQuoteClose()).isNotBlank();
    }

    @Test
    @DisplayName("德语插件不注册变换器")
    void testNoTransformersRegistered() {
        DeDeLexiconPlugin plugin = new DeDeLexiconPlugin();
        assertThat(plugin.getTransformers()).isEmpty();
    }

    @Test
    @DisplayName("R7-Backend-4: providedLexiconIds 与 createLexicon().getId() 一致")
    void testProvidedIdsMatchActualLexicon() {
        DeDeLexiconPlugin plugin = new DeDeLexiconPlugin();
        assertThat(plugin.providedLexiconIds())
            .as("plugin metadata 必须与 createLexicon() 一致")
            .containsExactly(plugin.createLexicon().getId());
    }
}
