package aster.lang.en;

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
 * 英文语言包插件冒烟测试。
 * <p>
 * 验证 SPI 发现、JSON 加载、关键词完整性和标点配置。
 */
@DisplayName("EnUsPlugin 冒烟测试")
class EnUsPluginTest {

    private static Lexicon lexicon;

    @BeforeAll
    static void loadPlugin() {
        LexiconPlugin plugin = ServiceLoader.load(LexiconPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p instanceof EnUsPlugin)
                .findFirst()
                .orElseThrow(() -> new AssertionError("EnUsPlugin 未通过 SPI 发现"));
        lexicon = plugin.createLexicon();
    }

    @Test
    @DisplayName("SPI 能发现 EnUsPlugin")
    void testPluginDiscoveredViaSpi() {
        assertThat(lexicon).isNotNull();
    }

    @Test
    @DisplayName("词法表 ID 和元数据正确")
    void testLexiconIdAndMeta() {
        assertThat(lexicon.getId()).isEqualTo("en-US");
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
        assertThat(kw.get(SemanticTokenKind.IF)).isEqualToIgnoringCase("If");
        assertThat(kw.get(SemanticTokenKind.RETURN)).isEqualToIgnoringCase("Return");
        assertThat(kw.get(SemanticTokenKind.TRUE)).isEqualToIgnoringCase("true");
        assertThat(kw.get(SemanticTokenKind.MODULE_DECL)).isEqualToIgnoringCase("Module");
        assertThat(kw.get(SemanticTokenKind.LET)).isEqualToIgnoringCase("Let");
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
    @DisplayName("英文插件不注册变换器")
    void testNoTransformersRegistered() {
        EnUsPlugin plugin = new EnUsPlugin();
        assertThat(plugin.getTransformers()).isEmpty();
    }

    @Test
    @DisplayName("R7-Backend-4: providedLexiconIds 与 createLexicon().getId() 一致")
    void testProvidedIdsMatchActualLexicon() {
        // 防止 metadata 撒谎：hardcoded "en-US" 必须等于 JSON 实际 id
        EnUsPlugin plugin = new EnUsPlugin();
        assertThat(plugin.providedLexiconIds())
            .as("plugin metadata 必须与 createLexicon() 返回 id 一致 —— 防止 hot-plug 误判")
            .containsExactly(plugin.createLexicon().getId());
    }
}
