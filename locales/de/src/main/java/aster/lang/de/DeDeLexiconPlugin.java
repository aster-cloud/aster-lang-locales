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

    @Override
    public Map<String, String> getOverlayResources() {
        return Map.of(
                "typeInferenceRules", "overlays/type-inference-rules.json",
                "inputGenerationRules", "overlays/input-generation-rules.json"
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
