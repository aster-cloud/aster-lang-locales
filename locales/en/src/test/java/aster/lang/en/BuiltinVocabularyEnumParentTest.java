package aster.lang.en;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置领域词汇的枚举值必须声明所属枚举（ADR 0051）：
 * 无 parent 的枚举值会被引擎跳过，导致内置枚举本地化静默失效。
 */
@DisplayName("en 内置词汇枚举值所属枚举")
class BuiltinVocabularyEnumParentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, String> FINANCE_LOAN = Map.of(
            "Excellent", "CreditLevel", "Good", "CreditLevel", "Fair", "CreditLevel", "Poor", "CreditLevel",
            "Personal", "LoanType", "Mortgage", "LoanType", "Business", "LoanType", "Auto", "LoanType");

    private static final Map<String, String> INSURANCE_AUTO = Map.of(
            "Approved", "QuoteStatus", "Rejected", "QuoteStatus", "Pending", "QuoteStatus",
            "HighRisk", "RiskLevel", "MediumRisk", "RiskLevel", "LowRisk", "RiskLevel");

    private static JsonNode load(String file) throws Exception {
        try (InputStream in = BuiltinVocabularyEnumParentTest.class.getResourceAsStream("/vocabularies/" + file)) {
            assertThat(in).as("词汇资源 %s", file).isNotNull();
            return MAPPER.readTree(in);
        }
    }

    private static Map<String, String> parentsByCanonical(JsonNode vocab) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode e : vocab.get("enumValues")) {
            JsonNode parent = e.get("parent");
            result.put(e.get("canonical").asText(), parent == null ? null : parent.asText());
        }
        return result;
    }

    /** 按（所属枚举, 本地化拼写或别名）解析规范成员，与引擎按父枚举索引的方式一致。 */
    private static String resolve(JsonNode vocab, String parent, String spelling) {
        for (JsonNode e : vocab.get("enumValues")) {
            if (!parent.equals(e.path("parent").asText(null))) continue;
            if (spelling.equals(e.get("localized").asText())) return e.get("canonical").asText();
            for (JsonNode alias : e.path("aliases")) {
                if (spelling.equals(alias.asText())) return e.get("canonical").asText();
            }
        }
        return null;
    }

    @Test
    @DisplayName("finance-loan 的 8 个枚举值都有所属枚举")
    void financeLoanParents() throws Exception {
        assertThat(parentsByCanonical(load("finance-loan-en-US.json"))).isEqualTo(FINANCE_LOAN);
    }

    @Test
    @DisplayName("insurance-auto 的 6 个枚举值都有所属枚举")
    void insuranceAutoParents() throws Exception {
        assertThat(parentsByCanonical(load("insurance-auto-en-US.json"))).isEqualTo(INSURANCE_AUTO);
    }

    @Test
    @DisplayName("Define CreditLevel 的本地化拼写与别名解析为规范成员")
    void localizedSpellingResolvesToCanonicalMember() throws Exception {
        JsonNode vocab = load("finance-loan-en-US.json");
        assertThat(resolve(vocab, "CreditLevel", "Excellent")).isEqualTo("Excellent");
        assertThat(resolve(vocab, "CreditLevel", "Fair")).isEqualTo("Fair");
        assertThat(resolve(vocab, "LoanType", "Excellent")).isNull();
    }
}
