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
 * 语法错误文案目录（{@code syntax-diagnostics/<locale>.json}）的结构契约。
 *
 * <p>目录是 aster-api（ANTLR 解析错误）与 aster-cloud 编辑器（aster-lang-ts 解析错误）共用的唯一真相源
 * （aster-api#423）：Maven 随本 jar 分发，npm 随 {@code @aster-cloud/ui-messages} 分发。两端按 key 取文案，
 * 所以每个 locale 的 key 集与每条模板的占位符集都必须与 en-US 一致，否则某一端会静默退回英文或漏填占位符。
 */
@DisplayName("syntax-diagnostics 文案目录契约")
class SyntaxDiagnosticsCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    private static Map<String, String> load(String locale) throws Exception {
        String path = "/syntax-diagnostics/" + locale + ".json";
        try (InputStream in = SyntaxDiagnosticsCatalogTest.class.getResourceAsStream(path)) {
            assertThat(in).as("jar 内应有 %s", path).isNotNull();
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
    @DisplayName("排版开关是布尔字面量")
    void styleFlagIsBoolean(String locale) throws Exception {
        assertThat(load(locale).get("style.spaceQuotesAfterHan")).isIn("true", "false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "zh-CN", "de-DE", "hi-IN"})
    @DisplayName("两端共用的核心句型都在（服务端 ANTLR 与编辑器 aster-lang-ts）")
    void sharedKeysPresent(String locale) throws Exception {
        assertThat(load(locale)).containsKeys(
            // 服务端（aster-api CnlErrorListener）
            "summary", "located", "moreErrors.one", "moreErrors.other", "unexpectedExpecting", "expectedBefore",
            "extraneous", "noViable",
            // 两端共用
            "expectedAtEndOfStatement", "unexpected", "or", "token.identifier", "token.newline",
            // 编辑器（aster-cloud parse-diagnostic-l10n）
            "expected", "expectedAfter", "expectedAfterIn", "expectedIn", "unknownStatement",
            "unexpectedAtTopLevel", "unexpectedExpression", "token.expression", "token.returnType");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("en-US 的编辑器句型与 aster-lang-ts 解析器的英文原句一致（编辑器按 en 值反查）")
    void englishMatchesParserPhrasing() throws Exception {
        Map<String, String> en = load("en-US");
        assertThat(en.get("unknownStatement")).isEqualTo("Unknown statement");
        assertThat(en.get("unexpectedAtTopLevel")).isEqualTo("Unexpected token at top level");
        assertThat(en.get("unexpectedExpression")).isEqualTo("Unexpected expression");
        assertThat(en.get("token.returnType")).isEqualTo("return type");
        assertThat(en.get("token.ifExpression")).isEqualTo("if-expression");
    }
}
