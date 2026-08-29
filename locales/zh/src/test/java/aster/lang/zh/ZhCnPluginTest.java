package aster.lang.zh;

import aster.core.canonicalizer.TransformerRegistry;
import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconPlugin;
import aster.core.lexicon.LexiconRegistry;
import aster.core.lexicon.PunctuationConfig;
import aster.core.lexicon.SemanticTokenKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文语言包插件冒烟测试。
 * <p>
 * 验证 SPI 发现、JSON 加载、关键词完整性、标点配置和变换器注册。
 */
@DisplayName("ZhCnPlugin 冒烟测试")
class ZhCnPluginTest {

    private static Lexicon lexicon;
    private static ZhCnPlugin plugin;

    @BeforeAll
    static void loadPlugin() {
        plugin = (ZhCnPlugin) ServiceLoader.load(LexiconPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(p -> p instanceof ZhCnPlugin)
                .findFirst()
                .orElseThrow(() -> new AssertionError("ZhCnPlugin 未通过 SPI 发现"));
        lexicon = plugin.createLexicon();
    }

    @Test
    @DisplayName("SPI 能发现 ZhCnPlugin")
    void testPluginDiscoveredViaSpi() {
        assertThat(lexicon).isNotNull();
    }

    /**
     * ★守卫：javadoc 里的变换器数量必须与实际注册数一致（issue #83）。
     *
     * <p>类 javadoc 曾长期写「6 个」而实际注册 7 个——后加的 chinese-let-be 未同步计数。
     * 这类「注释声称 ≠ 实现」靠人读是发现不了的，故用测试钉住：
     * 改了 getTransformers() 却忘了改 javadoc，本条即报红。
     */
    @Test
    @DisplayName("守卫：javadoc 声称的变换器数量与实际注册数一致")
    void javadocTransformerCountMatchesActual() throws Exception {
        int actual = new ZhCnPlugin().getTransformers().size();

        java.nio.file.Path src = java.nio.file.Path.of(
                "src/main/java/aster/lang/zh/ZhCnPlugin.java");
        org.assertj.core.api.Assertions.assertThat(java.nio.file.Files.isRegularFile(src))
                .withFailMessage("前置条件：源文件应存在于 %s", src.toAbsolutePath())
                .isTrue();
        String text = java.nio.file.Files.readString(src);

        var m = java.util.regex.Pattern.compile("将\\s*(\\d+)\\s*个中文语法变换器").matcher(text);
        org.assertj.core.api.Assertions.assertThat(m.find())
                .withFailMessage("javadoc 里应有「将 N 个中文语法变换器」的描述")
                .isTrue();
        int claimed = Integer.parseInt(m.group(1));

        org.assertj.core.api.Assertions.assertThat(claimed)
                .withFailMessage("javadoc 声称注册 %d 个变换器，实际 %d 个", claimed, actual)
                .isEqualTo(actual);
    }

    @Test
    @DisplayName("词法表 ID 和元数据正确")
    void testLexiconIdAndMeta() {
        assertThat(lexicon.getId()).isEqualTo("zh-CN");
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
        assertThat(kw.get(SemanticTokenKind.IF)).contains("如果");
        assertThat(kw.get(SemanticTokenKind.RETURN)).contains("返回");
        // v2 关键字：TRUE='真值'、MATCH='匹配于'、BE='定义为'
        assertThat(kw.get(SemanticTokenKind.TRUE)).isEqualTo("真值");
        assertThat(kw.get(SemanticTokenKind.MATCH)).isEqualTo("匹配于");
        assertThat(kw.get(SemanticTokenKind.BE)).isEqualTo("定义为");
        assertThat(kw.get(SemanticTokenKind.MODULE_DECL)).contains("模块");
        assertThat(kw.get(SemanticTokenKind.LET)).contains("令");
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
    @DisplayName("7 个中文变换器已注册")
    void testTransformersRegistered() {
        Map<String, ?> transformers = plugin.getTransformers();
        assertThat(transformers).hasSize(7);
        assertThat(transformers).containsKeys(
                "chinese-punctuation",
                "chinese-possessive",
                "chinese-operator",
                "chinese-function-syntax",
                "chinese-set-to",
                "chinese-result-is",
                "chinese-let-be"
        );
    }

    @Test
    @DisplayName("变换器可从 TransformerRegistry 获取")
    void testTransformersInRegistry() {
        // 确保 LexiconRegistry 已初始化（会触发 SPI 注册变换器到 TransformerRegistry）
        LexiconRegistry.getInstance();
        assertThat(TransformerRegistry.contains("chinese-punctuation")).isTrue();
        assertThat(TransformerRegistry.contains("chinese-possessive")).isTrue();
        assertThat(TransformerRegistry.contains("chinese-operator")).isTrue();
        assertThat(TransformerRegistry.contains("chinese-function-syntax")).isTrue();
        assertThat(TransformerRegistry.contains("chinese-set-to")).isTrue();
        assertThat(TransformerRegistry.contains("chinese-result-is")).isTrue();
        assertThat(TransformerRegistry.contains("chinese-let-be")).isTrue();
    }

    @Test
    @DisplayName("R7-Backend-4: providedLexiconIds 与 createLexicon().getId() 一致")
    void testProvidedIdsMatchActualLexicon() {
        ZhCnPlugin plugin = new ZhCnPlugin();
        assertThat(plugin.providedLexiconIds())
            .as("plugin metadata 必须与 createLexicon() 一致")
            .containsExactly(plugin.createLexicon().getId());
    }
}
