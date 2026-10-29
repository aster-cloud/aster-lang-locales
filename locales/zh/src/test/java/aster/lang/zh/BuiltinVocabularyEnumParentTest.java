package aster.lang.zh;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static aster.lang.testsupport.BuiltinVocabularies.FINANCE_LOAN_PARENTS;
import static aster.lang.testsupport.BuiltinVocabularies.INSURANCE_AUTO_PARENTS;
import static aster.lang.testsupport.BuiltinVocabularies.parentsByCanonical;
import static aster.lang.testsupport.BuiltinVocabularies.resolve;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置领域词汇的枚举值必须声明所属枚举（ADR 0051）：
 * 无 parent 的枚举值会被引擎跳过，导致内置枚举本地化静默失效。
 */
@DisplayName("zh 内置词汇枚举值所属枚举")
class BuiltinVocabularyEnumParentTest {

    private static JsonNode load(String file) {
        return aster.lang.testsupport.BuiltinVocabularies.load(BuiltinVocabularyEnumParentTest.class, file);
    }

    @Test
    @DisplayName("finance-loan 的 8 个枚举值都有所属枚举")
    void financeLoanParents() {
        assertThat(parentsByCanonical(load("finance-loan-zh-CN.json"))).isEqualTo(FINANCE_LOAN_PARENTS);
    }

    @Test
    @DisplayName("insurance-auto 的 6 个枚举值都有所属枚举")
    void insuranceAutoParents() {
        assertThat(parentsByCanonical(load("insurance-auto-zh-CN.json"))).isEqualTo(INSURANCE_AUTO_PARENTS);
    }

    @Test
    @DisplayName("Define CreditLevel 的本地化拼写与别名解析为规范成员")
    void localizedSpellingResolvesToCanonicalMember() {
        JsonNode vocab = load("finance-loan-zh-CN.json");
        assertThat(resolve(vocab, "CreditLevel", "优秀")).isEqualTo("Excellent");
        assertThat(resolve(vocab, "CreditLevel", "优")).isEqualTo("Excellent");
        assertThat(resolve(vocab, "LoanType", "优秀")).isNull();
    }
}
