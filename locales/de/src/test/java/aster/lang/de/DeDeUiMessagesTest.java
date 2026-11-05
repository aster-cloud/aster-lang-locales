package aster.lang.de;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 德语界面文案（ui-messages/de-DE.json）的回归测试。
 *
 * <p>只守已确认过的错误文案，避免 cloud 侧补充层长期覆盖源包（见 aster-lang-locales#104）。
 */
@DisplayName("DeDe ui-messages 文案回归")
class DeDeUiMessagesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode load() throws Exception {
        try (InputStream in = DeDeUiMessagesTest.class.getResourceAsStream("/ui-messages/de-DE.json")) {
            assertThat(in).as("classpath 上应有 ui-messages/de-DE.json").isNotNull();
            return MAPPER.readTree(in);
        }
    }

    @Test
    @DisplayName("快照 diff 面板回滚按钮使用可分动词的正确语序")
    void snapshotRollbackButtonIsGrammatical() throws Exception {
        JsonNode rollback = load().at("/domainVocabularies/snapshotsView/diff/rollback");
        assertThat(rollback.isTextual()).isTrue();
        assertThat(rollback.asText()).isEqualTo("Auf diesen Snapshot zurücksetzen");
    }

    @Test
    @DisplayName("全文不再出现「rollen zurück」这种错误拆分")
    void noBrokenSeparableVerb() throws Exception {
        assertThat(load().toString()).doesNotContain("rollen zurück");
    }

    @Test
    @DisplayName("快照 diff 面板的软删除提示用 Sie 形式（Ihrer），加载文案与站内「… wird geladen…」一致（#114）")
    void snapshotDiffUsesSieFormAndConsistentLoading() throws Exception {
        JsonNode diff = load().at("/domainVocabularies/snapshotsView/diff");
        assertThat(diff.path("removedNotice").asText()).isEqualTo(
            "{n} Ihrer aktuellen Begriffe würden beim Rollback soft-gelöscht. Innerhalb von 90 Tagen wiederherstellbar.");
        assertThat(diff.path("loading").asText()).isEqualTo("Snapshot wird geladen…");
    }

    /** 非正式 du 形式：代词/物主代词，以及站内曾出现过的 du 祈使句动词开头。 */
    private static final Pattern INFORMAL = Pattern.compile(
        "\\b(?:[Dd]u|[Dd]ein(?:e|em|en|er|es)?|[Dd]ich|[Dd]ir|[Ee]uch|[Ee]ure?)\\b"
            + "|^(?:Wähle|Lade|Binde|Verwalte|Verwende|Füge|Gib|Klicke|Prüfe|Erstelle|Lösche|Speichere|Öffne) ");

    private static void collectInformal(String path, JsonNode node, List<String> hits) {
        if (node.isTextual()) {
            if (INFORMAL.matcher(node.asText()).find()) {
                hits.add(path + " = " + node.asText());
            }
            return;
        }
        node.fields().forEachRemaining(e -> collectInformal(path + "/" + e.getKey(), e.getValue(), hits));
        for (int i = 0; i < node.size() && node.isArray(); i++) {
            collectInformal(path + "/" + i, node.get(i), hits);
        }
    }

    @Test
    @DisplayName("全站统一 Sie 形式：任何文案都不得出现 du/dein/dich/dir 或 du 祈使句（#116）")
    void noInformalDuForms() throws Exception {
        List<String> hits = new ArrayList<>();
        collectInformal("", load(), hits);
        assertThat(hits).as("以下 de-DE 文案仍是非正式 du 形式").isEmpty();
    }
}
