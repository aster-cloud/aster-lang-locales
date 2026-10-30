package aster.lang.de;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

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
}
