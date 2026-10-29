package aster.lang.de;

import aster.core.identifier.DomainVocabulary;
import aster.core.identifier.VocabularyPlugin;
import aster.core.identifier.VocabularyPluginSupport;
import aster.core.lexicon.DynamicLexicon;
import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconPlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 德语语言包插件 (de-DE)。
 * <p>
 * 从 JSON 配置加载德语词法表，通过 SPI 机制注册到 {@link aster.core.lexicon.LexiconRegistry}。
 * 德语使用自定义规则（customRules）进行 Umlaut 规范化，不需要专用变换器。
 * <p>
 * 同时实现 {@link VocabularyPlugin}，提供德语领域词汇表（汽车保险、贷款金融）。
 */
public final class DeDeLexiconPlugin implements LexiconPlugin, VocabularyPlugin {

    @Override
    public java.util.Set<String> providedLexiconIds() {
        return java.util.Set.of("de-DE");
    }

    @Override
    public Lexicon createLexicon() {
        String json = loadResource("lexicons/de-DE.json");
        return DynamicLexicon.fromJsonString(json);
    }

    @Override
    public DomainVocabulary createVocabulary() {
        return VocabularyPluginSupport.loadVocabulary(getClass(), "vocabularies/insurance-auto-de-DE.json");
    }

    @Override
    public List<DomainVocabulary> getVocabularies() {
        return List.of(
            VocabularyPluginSupport.loadVocabulary(getClass(), "vocabularies/finance-loan-de-DE.json")
        );
    }

    /**
     * overlay 资源放在 {@code overlays/de-DE/} 下：各语言包 jar 常同处一个 classpath
     * （core 的 exportLexicons、aster-api 的父类加载器），资源路径若相同，按路径查找只会
     * 命中 classpath 上第一个 jar，其余语言的 overlay 被静默遮蔽。
     */
    @Override
    public Map<String, String> getOverlayResources() {
        return Map.of(
                "typeInferenceRules", "overlays/de-DE/type-inference-rules.json",
                "inputGenerationRules", "overlays/de-DE/input-generation-rules.json",
                // ★磁盘上一直有 overlays/lsp-ui-texts.json（23 条完整德语 LSP 文案），
                //   但从未在此注册 —— 德语 LSP UI 文案**永远不会被加载**。
                //   对照 zh 插件一直是注册的。见 issue #80。
                "lspUiTexts", "overlays/de-DE/lsp-ui-texts.json",
                // 诊断文案（错误码 → 德语 message/help），覆盖英文码表全部错误码，
                // 由 DeDeDiagnosticOverlayTest 校验覆盖面与占位符。
                "diagnosticMessages", "overlays/de-DE/diagnostic-messages.json",
                "diagnosticHelp", "overlays/de-DE/diagnostic-help.json"
        );
    }

    private String loadResource(String path) {
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load resource: " + path, e);
        }
    }
}
