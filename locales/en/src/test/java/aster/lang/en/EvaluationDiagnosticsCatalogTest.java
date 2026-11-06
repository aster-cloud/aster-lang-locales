package aster.lang.en;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评估诊断文案目录（{@code evaluation-diagnostics/<locale>.json}）的结构契约。
 *
 * <p>aster-api 的 evaluate 系列 400 诊断（入口歧义、非法 agent，aster-api#445）按 key 取文案，code 是稳定契约、
 * message 只供展示。目录与 syntax-diagnostics 同样的两条通道分发：Maven 随本 jar，npm 随
 * {@code @aster-cloud/ui-messages}。每个 locale 的 key 集与每条模板的占位符集都必须与 en-US 一致，
 * 否则 aster-api 会静默退回英文或漏填占位符。
 *
 * <p>读取的是测试 classpath（processResources 产物），验证 {@code locales/en/build.gradle.kts} 的拷贝接线。
 */
@DisplayName("evaluation-diagnostics 文案目录契约")
class EvaluationDiagnosticsCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    private static Map<String, String> load(String locale) throws Exception {
        String path = "/evaluation-diagnostics/" + locale + ".json";
        try (InputStream in = EvaluationDiagnosticsCatalogTest.class.getResourceAsStream(path)) {
            assertThat(in).as("classpath（processResources 产物）上应有 %s", path).isNotNull();
            JsonNode root = MAPPER.readTree(in);
            Map<String, String> out = new TreeMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                assertThat(e.getValue().isTextual()).as("%s 的 %s 应为字符串", locale, e.getKey()).isTrue();
                out.put(e.getKey(), e.getValue().asText());
            }
            return out;
        }
    }

    private static Set<String> placeholders(String template) {
        Set<String> names = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(template);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "zh-CN", "de-DE", "hi-IN"})
    @DisplayName("key 集与 en-US 一致，值非空")
    void keySetMatchesEnglish(String locale) throws Exception {
        Map<String, String> en = load("en-US");
        Map<String, String> own = load(locale);
        assertThat(own.keySet()).as(locale).isEqualTo(en.keySet());
        own.forEach((key, value) -> assertThat(value).as("%s %s", locale, key).isNotBlank());
    }

    @ParameterizedTest
    @ValueSource(strings = {"zh-CN", "de-DE", "hi-IN"})
    @DisplayName("每条模板的占位符集与 en-US 一致")
    void placeholdersMatchEnglish(String locale) throws Exception {
        Map<String, String> en = load("en-US");
        Map<String, String> own = load(locale);
        en.forEach((key, template) -> assertThat(placeholders(own.get(key)))
            .as("%s %s", locale, key)
            .isEqualTo(placeholders(template)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "zh-CN", "de-DE", "hi-IN"})
    @DisplayName("aster-api EvaluationDiagnostics 用到的 key 都在")
    void apiKeysPresent(String locale) throws Exception {
        // agent.* 后缀对应 aster-api AgentIdentity.InvalidAgentException.Reason 的枚举名
        assertThat(load(locale)).containsKeys(
            "entryAmbiguous.error", "entryAmbiguous.detail",
            "agent.REQUIRED", "agent.TOO_LONG", "agent.CONTROL_CHAR");
    }
}
