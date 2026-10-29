package aster.lang.en;

import aster.lang.testsupport.OverlayResources;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
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
        assertThat(overlays).containsKeys("typeInferenceRules", "inputGenerationRules", "lspUiTexts");
        assertThat(overlays).hasSize(3);
    }

    /**
     * ★守卫：resources/overlays/ 下（含子目录）的每个 .json 都必须在 getOverlayResources() 注册。
     *
     * <p>issue #80 的根因不是"忘了写一行"，而是**没有任何机制发现忘了写**：
     * 文件躺在磁盘上却未注册，其内容永远不会被加载；而原测试的 {@code hasSize(N)}
     * 把遗漏**锁死**了——任何人补上注册反而会让测试变红。
     *
     * <p>「文件在磁盘上」与「文件被注册」是两件事，只断言后者永远发现不了前者的缺口。
     * 本守卫从磁盘反推期望集合，故新增 overlay 文件却忘记注册时必然报红。
     */
    @Test
    @DisplayName("守卫：磁盘上每个 overlay 文件都必须被注册")
    void everyOverlayFileOnDiskIsRegistered() {
        List<String> unregistered = OverlayResources.unregisteredOnDisk(new EnUsPlugin());
        assertThat(unregistered)
                .withFailMessage(
                        "以下 overlay 文件在磁盘上却未在 getOverlayResources() 注册，"
                                + "它们的内容永远不会被加载：%s", unregistered)
                .isEmpty();
    }

    @Test
    @DisplayName("overlay 路径位于 overlays/en-US/ 下且在 classpath 上只有一份（防多语言包同 classpath 时互相遮蔽）")
    void overlayPathsAreNamespacedAndUnique() {
        assertThat(OverlayResources.problems(new EnUsPlugin(), "en-US")).isEmpty();
    }

    @Test
    @DisplayName("type-inference-rules.json 格式正确且正则可编译")
    void testTypeInferenceRulesJson() throws Exception {
        JsonNode root = loadOverlay("overlays/en-US/type-inference-rules.json");
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
        JsonNode root = loadOverlay("overlays/en-US/lsp-ui-texts.json");
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
