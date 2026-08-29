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

    /**
     * ★守卫：resources/overlays/ 下的每个 .json 都必须在 getOverlayResources() 注册。
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
    void everyOverlayFileOnDiskIsRegistered() throws Exception {
        Map<String, String> overlays = new ZhCnPlugin().getOverlayResources();
        java.util.Set<String> registered = new java.util.HashSet<>(overlays.values());

        java.nio.file.Path dir = java.nio.file.Path.of("src/main/resources/overlays");
        assertThat(java.nio.file.Files.isDirectory(dir))
                .withFailMessage("前置条件：overlays 目录应存在于 %s", dir.toAbsolutePath())
                .isTrue();

        java.util.List<String> unregistered = new java.util.ArrayList<>();
        try (var files = java.nio.file.Files.list(dir)) {
            files.filter(f -> f.toString().endsWith(".json"))
                    .map(f -> "overlays/" + f.getFileName())
                    .filter(rel -> !registered.contains(rel))
                    .forEach(unregistered::add);
        }

        assertThat(unregistered)
                .withFailMessage(
                        "以下 overlay 文件在磁盘上却未在 getOverlayResources() 注册，"
                                + "它们的内容永远不会被加载：%s", unregistered)
                .isEmpty();
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
    @DisplayName("diagnostic-help.json 的 E102/E103/E104 语义符合码表裁决（防重复符号/entry 规则语义漂移）")
    void testScopeCodeHelpRuling() throws Exception {
        // 错误码码表反向重建后（shared/error_codes.json 为真源）：
        // E102=MULTIPLE_ENTRY_RULES、E103=IMPORT_SYMBOL_CONFLICT、E104=DUPLICATE_SYMBOL。
        // 历史上 overlay 残留旧 E102=DUPLICATE_SYMBOL help，导致中文用户可见语义漂移。
        // 本用例钉死这三个码的中文 help 语义，防回滚旧语义。
        JsonNode help = loadOverlay("overlays/diagnostic-help.json").get("help");

        // E102 现为 MULTIPLE_ENTRY_RULES：语义应关于 @entry Rule，绝不能再是“重复声明/名称”。
        assertThat(help.has("E102")).as("E102 应存在").isTrue();
        String e102 = help.get("E102").asText();
        assertThat(e102).contains("@entry");
        assertThat(e102).doesNotContain("选择不同的名称");

        // E103 现为 IMPORT_SYMBOL_CONFLICT：语义应关于导入符号冲突。
        assertThat(help.has("E103")).as("E103 应存在").isTrue();
        assertThat(help.get("E103").asText()).contains("导入");

        // E104 现为 DUPLICATE_SYMBOL：承接旧“重复声明”语义。
        assertThat(help.has("E104")).as("E104 应存在").isTrue();
        assertThat(help.get("E104").asText()).contains("重复");
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
