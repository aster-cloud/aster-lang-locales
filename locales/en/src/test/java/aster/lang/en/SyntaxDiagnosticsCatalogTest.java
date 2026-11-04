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
 *
 * <p>读取的是测试 classpath 上的 {@code syntax-diagnostics/}，即 processResources 的产物（{@code build/resources/main}），
 * 与打进 jar 的内容是同一份；它验证的是 {@code locales/en/build.gradle.kts} 把仓根目录拷到该路径的接线。
 */
@DisplayName("syntax-diagnostics 文案目录契约")
class SyntaxDiagnosticsCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");

    private static Map<String, String> load(String locale) throws Exception {
        String path = "/syntax-diagnostics/" + locale + ".json";
        try (InputStream in = SyntaxDiagnosticsCatalogTest.class.getResourceAsStream(path)) {
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

    /**
     * 编辑器按 en-US 的值反查 key（aster-cloud {@code parse-diagnostic-l10n.ts} 的 NOUN_KEYS / FIXED_KEYS）：
     * 这些值必须逐字等于 aster-lang-ts 解析器输出的英文短语。改了其中任何一个，服务端照常显示，
     * 编辑器却会静默退回英文原文，所以全部锁住。改这里须同步 aster-lang-ts 与 cloud。
     */
    private static final Map<String, String> EDITOR_REVERSE_LOOKUP = Map.ofEntries(
        // 整句固定的消息
        Map.entry("unknownStatement", "Unknown statement"),
        Map.entry("unexpectedAtTopLevel", "Unexpected token at top level"),
        Map.entry("unexpectedExpression", "Unexpected expression"),
        // 解析器消息里的名词短语
        Map.entry("token.identifier", "identifier"),
        Map.entry("token.type", "type"),
        Map.entry("token.typeName", "type name"),
        Map.entry("token.returnType", "return type"),
        Map.entry("token.construction", "construction"),
        Map.entry("token.dot", "dot"),
        Map.entry("token.newline", "newline"),
        Map.entry("token.indent", "indent"),
        Map.entry("token.dedent", "dedent"),
        Map.entry("token.expression", "expression"),
        Map.entry("token.statement", "statement"),
        Map.entry("token.dataDefinition", "data definition"),
        Map.entry("token.enumDefinition", "enum definition"),
        Map.entry("token.ifExpression", "if-expression"),
        Map.entry("token.inlineIf", "inline if"),
        Map.entry("token.functionBody", "function body"),
        Map.entry("token.explicitBlock", "explicit block"),
        Map.entry("token.annotationArgumentValue", "annotation argument value"),
        Map.entry("token.integer", "integer"),
        Map.entry("token.atLeastOneStatement", "at least one statement"),
        Map.entry("token.workflowHeader", "workflow header"),
        Map.entry("token.workflowBody", "workflow body"),
        Map.entry("token.cases", "cases"),
        Map.entry("token.retryPolicy", "retry policy"));

    @org.junit.jupiter.api.Test
    @DisplayName("en-US 里编辑器反查用的 27 个值全部等于 aster-lang-ts 解析器的英文原文")
    void englishMatchesParserPhrasing() throws Exception {
        assertThat(load("en-US")).containsAllEntriesOf(EDITOR_REVERSE_LOOKUP);
    }
}
