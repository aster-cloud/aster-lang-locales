package aster.lang.zh;

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
 * 中文语言包 overlay JSON 验证测试。
 */
@DisplayName("ZhCn Overlay JSON 验证")
class ZhCnOverlayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("getOverlayResources 返回正确的资源映射")
    void testOverlayResources() {
        ZhCnPlugin plugin = new ZhCnPlugin();
        Map<String, String> overlays = plugin.getOverlayResources();
        assertThat(overlays).containsKeys(
                "typeInferenceRules", "inputGenerationRules",
                "diagnosticMessages", "diagnosticHelp", "lspUiTexts"
        );
        assertThat(overlays).hasSize(5);
    }

    @Test
    @DisplayName("type-inference-rules.json 格式正确且正则可编译")
    void testTypeInferenceRulesJson() throws Exception {
        JsonNode root = loadOverlay("overlays/type-inference-rules.json");
        assertThat(root.get("version").asInt()).isEqualTo(1);
        JsonNode rules = root.get("rules");
        assertThat(rules.isArray()).isTrue();
        assertThat(rules.size()).isGreaterThan(0);
        for (JsonNode rule : rules) {
            assertValidRegexRule(rule);
        }
    }

    @Test
    @DisplayName("input-generation-rules.json 格式正确且正则可编译")
    void testInputGenerationRulesJson() throws Exception {
        JsonNode root = loadOverlay("overlays/input-generation-rules.json");
        assertThat(root.get("version").asInt()).isEqualTo(1);
        JsonNode rules = root.get("rules");
        assertThat(rules.isArray()).isTrue();
        assertThat(rules.size()).isGreaterThan(0);
        for (JsonNode rule : rules) {
            assertThat(rule.has("pattern")).isTrue();
            assertThat(rule.has("value")).isTrue();
            assertThat(rule.has("priority")).isTrue();
            String pattern = rule.get("pattern").asText();
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new AssertionError("无效正则: " + pattern, e);
            }
        }
    }

    @Test
    @DisplayName("diagnostic-messages.json 格式正确")
    void testDiagnosticMessagesJson() throws Exception {
        JsonNode root = loadOverlay("overlays/diagnostic-messages.json");
        assertThat(root.get("version").asInt()).isEqualTo(1);
        JsonNode messages = root.get("messages");
        assertThat(messages.isObject()).isTrue();
        assertThat(messages.size()).isGreaterThan(0);
        messages.fieldNames().forEachRemaining(key -> {
            assertThat(key).matches("[EW]\\d+");
            assertThat(messages.get(key).asText()).isNotBlank();
        });
    }

    @Test
    @DisplayName("diagnostic-help.json 格式正确")
    void testDiagnosticHelpJson() throws Exception {
        JsonNode root = loadOverlay("overlays/diagnostic-help.json");
        assertThat(root.get("version").asInt()).isEqualTo(1);
        JsonNode help = root.get("help");
        assertThat(help.isObject()).isTrue();
        assertThat(help.size()).isGreaterThan(0);
        help.fieldNames().forEachRemaining(key -> {
            assertThat(key).matches("[EW]\\d+");
            assertThat(help.get(key).asText()).isNotBlank();
        });
    }

    @Test
    @DisplayName("lsp-ui-texts.json 格式正确且包含必需字段")
    void testLspUiTextsJson() throws Exception {
        JsonNode root = loadOverlay("overlays/lsp-ui-texts.json");
        assertThat(root.get("version").asInt()).isEqualTo(1);
        JsonNode texts = root.get("texts");
        assertThat(texts.isObject()).isTrue();
        String[] requiredKeys = {
            "effectsLabel", "moduleDeclaration", "functionDefinition",
            "functionLabel", "typeLabel", "enumLabel",
            "hintPrefix", "fixPrefix", "missingModuleHeader"
        };
        for (String key : requiredKeys) {
            assertThat(texts.has(key)).as("缺少必需字段: %s", key).isTrue();
        }
    }

    private void assertValidRegexRule(JsonNode rule) {
        assertThat(rule.has("pattern")).isTrue();
        assertThat(rule.has("type")).isTrue();
        assertThat(rule.has("priority")).isTrue();
        String pattern = rule.get("pattern").asText();
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new AssertionError("无效正则: " + pattern, e);
        }
        assertThat(rule.get("type").asText()).isIn("Bool", "Int", "Float", "Text", "DateTime");
    }

    private JsonNode loadOverlay(String path) throws Exception {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(is).as("资源不存在: %s", path).isNotNull();
            return MAPPER.readTree(is);
        }
    }
}
