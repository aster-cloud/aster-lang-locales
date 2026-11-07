package aster.lang.de;

import aster.core.lexicon.LexiconPlugin;
import aster.lang.testsupport.DiagnosticOverlayContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("de-DE 诊断 overlay 契约")
class DeDeDiagnosticOverlayTest extends DiagnosticOverlayContract {

    @Override
    protected LexiconPlugin plugin() {
        return new DeDeLexiconPlugin();
    }

    /**
     * 带名词占位符的"意外的 X"句型不得用形容词直接修饰占位符：代入的名词性数不定
     * （Ausrückung 阴性、Token 中性、Fälle 复数），"Unerwartetes {token}" 会产出
     * "Unerwartetes Ausrückung"（aster-lang-locales#126）。改用冒号句式与 syntax-diagnostics 保持一致。
     */
    @Test
    @DisplayName("E061 / E062 的占位符前不用变格形容词")
    void unexpectedTemplatesDoNotInflectAgainstPlaceholder() throws Exception {
        try (InputStream in = DeDeDiagnosticOverlayTest.class.getResourceAsStream("/overlays/de-DE/diagnostic-messages.json")) {
            assertThat(in).isNotNull();
            JsonNode messages = new ObjectMapper().readTree(in).get("messages");
            assertThat(messages.get("E061").asText()).isEqualTo("Unerwartet: {token}");
            assertThat(messages.get("E062").asText()).isEqualTo("Unerwartet: {token}; erwartet: {expected}");
        }
    }
}
