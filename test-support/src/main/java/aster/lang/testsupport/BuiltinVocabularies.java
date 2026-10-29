package aster.lang.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置领域词汇（resources/vocabularies/*.json）的读取与解析工具，供各语言包测试共用。
 *
 * <p>ADR 0051 要求内置词汇的枚举值声明所属枚举：无 parent 的枚举值会被引擎跳过，
 * 导致内置枚举本地化静默失效。三个语言包对同一组规范枚举做同样的断言，只有词汇文件名
 * 与本地化拼写不同，故解析逻辑与期望的规范归属集中在这里。
 */
public final class BuiltinVocabularies {

    /** finance-loan 词汇中规范枚举值 → 所属枚举。 */
    public static final Map<String, String> FINANCE_LOAN_PARENTS = Map.of(
            "Excellent", "CreditLevel", "Good", "CreditLevel", "Fair", "CreditLevel", "Poor", "CreditLevel",
            "Personal", "LoanType", "Mortgage", "LoanType", "Business", "LoanType", "Auto", "LoanType");

    /** insurance-auto 词汇中规范枚举值 → 所属枚举。 */
    public static final Map<String, String> INSURANCE_AUTO_PARENTS = Map.of(
            "Approved", "QuoteStatus", "Rejected", "QuoteStatus", "Pending", "QuoteStatus",
            "HighRisk", "RiskLevel", "MediumRisk", "RiskLevel", "LowRisk", "RiskLevel");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BuiltinVocabularies() {
    }

    /**
     * 从调用方模块的 classpath 读取 {@code /vocabularies/<file>}。
     *
     * @param anchor 用于定位资源的类（取其所在模块的 classpath）
     * @throws IllegalStateException 资源不存在
     */
    public static JsonNode load(Class<?> anchor, String file) {
        String path = "/vocabularies/" + file;
        try (InputStream in = anchor.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("词汇资源不存在: " + path);
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("无法读取词汇资源: " + path, e);
        }
    }

    /** 规范枚举值 → 所属枚举（未声明 parent 时为 null），按文件中的顺序。 */
    public static Map<String, String> parentsByCanonical(JsonNode vocab) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode e : vocab.get("enumValues")) {
            JsonNode parent = e.get("parent");
            result.put(e.get("canonical").asText(), parent == null ? null : parent.asText());
        }
        return result;
    }

    /**
     * 按（所属枚举, 本地化拼写或别名）解析规范成员，与引擎按父枚举索引的方式一致。
     *
     * @return 规范成员名；该枚举下没有这个拼写时为 null
     */
    public static String resolve(JsonNode vocab, String parent, String spelling) {
        for (JsonNode e : vocab.get("enumValues")) {
            if (!parent.equals(e.path("parent").asText(null))) continue;
            if (spelling.equals(e.get("localized").asText())) return e.get("canonical").asText();
            for (JsonNode alias : e.path("aliases")) {
                if (spelling.equals(alias.asText())) return e.get("canonical").asText();
            }
        }
        return null;
    }
}
