package aster.lang.en;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 英文语言包 overlay JSON 验证测试。
 */
@DisplayName("EnUs Overlay JSON 验证")
class EnUsOverlayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("getOverlayResources 返回正确的资源映射")
    void testOverlayResources() {
        EnUsPlugin plugin = new EnUsPlugin();
        Map<String, String> overlays = plugin.getOverlayResources();
        assertThat(overlays).containsKeys("typeInferenceRules", "lspUiTexts");
        assertThat(overlays).hasSize(2);
    }

    @Test
    @DisplayName("type-inference-rules.json 格式正确且正则可编译")
    void testTypeInferenceRulesJson() throws Exception {
        JsonNode root = loadOverlay("overlays/type-inference-rules.json");
        assertThat(root.has("version")).isTrue();
        assertThat(root.get("version").asInt()).isEqualTo(1);

        JsonNode rules = root.get("rules");
        assertThat(rules.isArray()).isTrue();
        assertThat(rules.size()).isGreaterThan(0);

        for (JsonNode rule : rules) {
            assertThat(rule.has("pattern")).isTrue();
            assertThat(rule.has("type")).isTrue();
            assertThat(rule.has("priority")).isTrue();

            String pattern = rule.get("pattern").asText();
            String flags = rule.has("flags") ? rule.get("flags").asText() : "";
            try {
                int javaFlags = flags.contains("i") ? Pattern.CASE_INSENSITIVE : 0;
                Pattern.compile(pattern, javaFlags);
            } catch (PatternSyntaxException e) {
                throw new AssertionError("无效正则: " + pattern, e);
            }

            String type = rule.get("type").asText();
            assertThat(type).isIn("Bool", "Int", "Float", "Text", "DateTime");
        }
    }

    @Test
    @DisplayName("lsp-ui-texts.json 格式正确且包含必需字段")
    void testLspUiTextsJson() throws Exception {
        JsonNode root = loadOverlay("overlays/lsp-ui-texts.json");
        assertThat(root.has("version")).isTrue();
        assertThat(root.get("version").asInt()).isEqualTo(1);

        JsonNode texts = root.get("texts");
        assertThat(texts.isObject()).isTrue();

        String[] requiredKeys = {
            "effectsLabel", "moduleDeclaration", "functionDefinition",
            "functionLabel", "typeLabel", "enumLabel",
            "hintPrefix", "fixPrefix", "missingModuleHeader"
        };
        for (String key : requiredKeys) {
            assertThat(texts.has(key))
                    .as("缺少必需字段: %s", key)
                    .isTrue();
            assertThat(texts.get(key).asText()).isNotBlank();
        }
    }

    private JsonNode loadOverlay(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("资源不存在: %s", path).isNotNull();
            return MAPPER.readTree(is);
        }
    }
}
