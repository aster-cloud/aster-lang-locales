package aster.lang.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("内置词汇解析工具")
class BuiltinVocabulariesTest {

    private static final JsonNode VOCAB;

    static {
        try {
            VOCAB = new ObjectMapper().readTree("""
                    {"enumValues": [
                      {"canonical": "High", "localized": "Hoch", "parent": "Level", "aliases": ["Oben"]},
                      {"canonical": "Low", "localized": "Niedrig", "parent": "Level"},
                      {"canonical": "Orphan", "localized": "Waise"}
                    ]}""");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    @DisplayName("本地化拼写与别名都解析为规范成员")
    void resolvesLocalizedSpellingAndAlias() {
        assertThat(BuiltinVocabularies.resolve(VOCAB, "Level", "Hoch")).isEqualTo("High");
        assertThat(BuiltinVocabularies.resolve(VOCAB, "Level", "Oben")).isEqualTo("High");
    }

    @Test
    @DisplayName("所属枚举不符或拼写未知时为 null；无 parent 的枚举值不参与解析")
    void unknownParentOrSpellingResolvesToNull() {
        assertThat(BuiltinVocabularies.resolve(VOCAB, "Other", "Hoch")).isNull();
        assertThat(BuiltinVocabularies.resolve(VOCAB, "Level", "Mittel")).isNull();
        assertThat(BuiltinVocabularies.resolve(VOCAB, "Level", "Waise")).isNull();
    }

    @Test
    @DisplayName("parentsByCanonical 保留文件顺序，缺 parent 记为 null")
    void parentsByCanonicalKeepsOrderAndNulls() {
        assertThat(BuiltinVocabularies.parentsByCanonical(VOCAB))
                .containsExactly(
                        java.util.Map.entry("High", "Level"),
                        java.util.Map.entry("Low", "Level"),
                        new java.util.AbstractMap.SimpleEntry<>("Orphan", null));
    }

    @Test
    @DisplayName("资源不存在时抛出带路径的异常")
    void missingResourceFails() {
        assertThatThrownBy(() -> BuiltinVocabularies.load(BuiltinVocabulariesTest.class, "nope.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/vocabularies/nope.json");
    }
}
