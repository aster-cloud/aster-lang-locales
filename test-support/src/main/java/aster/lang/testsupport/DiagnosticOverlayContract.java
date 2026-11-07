package aster.lang.testsupport;

import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconExporter;
import aster.core.lexicon.LexiconPlugin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 语言包诊断 overlay 的契约测试基类：子类只需给出插件实例。
 *
 * <p>覆盖三件事：插件注册了两个诊断 overlay；译文覆盖英文码表全部错误码且占位符一致；
 * core 的 {@link LexiconExporter}（aster-lang-ts 生成诊断译文所读的 lexicons.json 的来源）
 * 确实把这两份 overlay 原样嵌入导出结果——即 Java 侧消费本语言包制品时译文可达。
 */
public abstract class DiagnosticOverlayContract {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 被测语言包插件。 */
    protected abstract LexiconPlugin plugin();

    @Test
    @DisplayName("注册 diagnosticMessages 与 diagnosticHelp 两个 overlay")
    void registersDiagnosticOverlays() {
        String prefix = OverlayResources.prefixOf(plugin().createLexicon().getId());
        assertThat(plugin().getOverlayResources())
                .containsEntry("diagnosticMessages", prefix + "diagnostic-messages.json")
                .containsEntry("diagnosticHelp", prefix + "diagnostic-help.json");
    }

    @Test
    @DisplayName("diagnostic-messages 覆盖全部错误码且占位符与英文一致")
    void messagesCoverBaseline() throws Exception {
        JsonNode root = overlay("diagnosticMessages");
        assertThat(root.path("version").asInt()).isEqualTo(1);
        List<String> problems = DiagnosticOverlays.problems(DiagnosticOverlays.baseline(), root.get("messages"), false);
        assertThat(problems).as("diagnostic-messages 与英文码表不一致").isEmpty();
    }

    @Test
    @DisplayName("diagnostic-help 覆盖全部错误码且占位符与英文一致")
    void helpCoversBaseline() throws Exception {
        JsonNode root = overlay("diagnosticHelp");
        assertThat(root.path("version").asInt()).isEqualTo(1);
        List<String> problems = DiagnosticOverlays.problems(DiagnosticOverlays.baseline(), root.get("help"), true);
        assertThat(problems).as("diagnostic-help 与英文码表不一致").isEmpty();
    }

    /**
     * 与磁盘上（本模块 src/main/resources 下）的文件比对，而不是与类加载器读到的资源比对：
     * 后者在路径冲突时会读到别的语言包的同名文件，测试就失去意义。
     */
    @Test
    @DisplayName("LexiconExporter 导出时原样嵌入本语言包磁盘上的两个诊断 overlay")
    void exporterEmbedsDiagnosticOverlays() throws Exception {
        LexiconPlugin plugin = plugin();
        Lexicon lexicon = plugin.createLexicon();
        StringWriter out = new StringWriter();
        new LexiconExporter().export("test", List.of(lexicon), Map.of(lexicon.getId(), plugin), out);

        JsonNode overlays = MAPPER.readTree(out.toString()).path("lexicons").path(lexicon.getId()).path("overlays");
        assertThat(overlays.get("diagnosticMessages")).isEqualTo(overlay("diagnosticMessages"));
        assertThat(overlays.get("diagnosticHelp")).isEqualTo(overlay("diagnosticHelp"));
    }

    /**
     * 语法目录句型 key → 共享码表错误码：同一个语法错误在服务端（overlay，按码）与编辑器（syntax-diagnostics，按 key）
     * 走两份文案，二者必须对齐，否则同一诊断在两个入口显示不同文字。
     * E050 不在表内：目录的 unrecognizedCharacter 是不带 {char} 的整句，编辑器对 E050 改用 unexpected 句型代入字符。
     */
    public static final Map<String, String> SYNTAX_CATALOG_CODES = Map.ofEntries(
            Map.entry("unterminatedString", "E051"),
            Map.entry("indentation", "E052"), Map.entry("literalOutOfRange", "E053"),
            Map.entry("nestingTooDeep", "E054"), Map.entry("expected", "E055"),
            Map.entry("expectedAfter", "E056"), Map.entry("expectedIn", "E057"),
            Map.entry("expectedAfterIn", "E058"), Map.entry("expectedAtEndOfStatement", "E059"),
            Map.entry("expectedBefore", "E060"), Map.entry("unexpected", "E061"),
            Map.entry("unexpectedExpecting", "E062"), Map.entry("noViable", "E063"),
            Map.entry("misplaced", "E065"), Map.entry("emptyBlock", "E066"),
            Map.entry("invalidValue", "E067"), Map.entry("callArity", "E068"),
            Map.entry("unknownName", "E069"));

    /** 文案逐字取自 overlay 的句型（aster-lang-locales#126 新增；早先的句型因排版空格差异只校占位符）。 */
    public static final List<String> SYNTAX_CATALOG_VERBATIM = List.of(
            "unterminatedString", "literalOutOfRange", "nestingTooDeep", "misplaced",
            "emptyBlock", "invalidValue", "callArity", "unknownName");

    /**
     * 语法目录（仓根 syntax-diagnostics/<id>.json，随 :en jar 进测试 classpath）与本语言包 overlay 的同码译文对齐：
     * 全部映射句型的占位符集合一致；#126 新增的 8 条逐字一致。
     */
    @Test
    @DisplayName("syntax-diagnostics 句型与 diagnostic-messages 同码译文对齐（占位符集合；新增句型逐字）")
    void syntaxCatalogMatchesOverlay() throws Exception {
        String id = plugin().createLexicon().getId();
        JsonNode messages = overlay("diagnosticMessages").get("messages");
        JsonNode catalog;
        try (InputStream in = DiagnosticOverlayContract.class.getResourceAsStream("/syntax-diagnostics/" + id + ".json")) {
            assertThat(in).as("测试 classpath 上应有 syntax-diagnostics/%s.json", id).isNotNull();
            catalog = MAPPER.readTree(in);
        }
        List<String> problems = new ArrayList<>();
        SYNTAX_CATALOG_CODES.forEach((key, code) -> {
            String sentence = catalog.path(key).asText(null);
            String translated = messages.path(code).asText(null);
            if (sentence == null || translated == null) {
                problems.add(key + "/" + code + ": 缺少文案");
                return;
            }
            if (!DiagnosticOverlays.placeholders(sentence).equals(DiagnosticOverlays.placeholders(translated))) {
                problems.add(key + "/" + code + ": 占位符不一致 目录=" + sentence + " overlay=" + translated);
            } else if (SYNTAX_CATALOG_VERBATIM.contains(key) && !sentence.equals(translated)) {
                problems.add(key + "/" + code + ": 文案不一致 目录=" + sentence + " overlay=" + translated);
            }
        });
        assertThat(problems).as("%s syntax-diagnostics 与 overlay 漂移", id).isEmpty();
    }

    /** 读取本模块磁盘上的 overlay 源文件（测试以模块目录为工作目录运行）。 */
    private JsonNode overlay(String key) throws Exception {
        String path = plugin().getOverlayResources().get(key);
        assertThat(path).as("未注册 overlay %s", key).isNotNull();
        Path file = Path.of("src/main/resources", path);
        assertThat(file).as("overlay 源文件不存在: %s", file.toAbsolutePath()).isRegularFile();
        return MAPPER.readTree(file.toFile());
    }
}
