package aster.lang.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("诊断 overlay 一致性检查")
class DiagnosticOverlaysTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, DiagnosticOverlays.English> ENGLISH = new LinkedHashMap<>();

    static {
        ENGLISH.put("E001", new DiagnosticOverlays.English("Type mismatch: expected {expected}, got {actual}", "Check types."));
        ENGLISH.put("E027", new DiagnosticOverlays.English("Step '{step}' uses {capability}", "Declare {capability}."));
    }

    private static JsonNode json(String text) throws Exception {
        return MAPPER.readTree(text);
    }

    @Test
    @DisplayName("英文码表副本含 95 个错误码，且包含 ADR 0051 的 E800–E807 / W800–W801")
    void baselineHasAllCodes() {
        Map<String, DiagnosticOverlays.English> baseline = DiagnosticOverlays.baseline();
        assertThat(baseline).hasSize(95);
        assertThat(baseline).containsKeys("E800", "E801", "E802", "E803", "E804", "E805", "E806", "E807", "W800", "W801");
    }

    @Test
    @DisplayName("完整且占位符一致时无问题（占位符重复出现与顺序不同都允许）")
    void completeOverlayPasses() throws Exception {
        JsonNode messages = json("{\"E001\": \"实际 {actual}，期望 {expected}\", \"E027\": \"步骤 '{step}' 用了 {capability}\"}");
        assertThat(DiagnosticOverlays.problems(ENGLISH, messages, false)).isEmpty();
    }

    @Test
    @DisplayName("缺码、空译文、多余码都会报告")
    void missingBlankAndExtraCodesAreReported() throws Exception {
        JsonNode messages = json("{\"E001\": \"  \", \"E999\": \"孤儿\"}");
        assertThat(DiagnosticOverlays.problems(ENGLISH, messages, false)).containsExactly(
                "E001: 译文为空",
                "E027: 缺少译文",
                "E999: 码表中不存在该错误码");
    }

    @Test
    @DisplayName("占位符缺失或多出都会报告")
    void placeholderMismatchIsReported() throws Exception {
        JsonNode messages = json("{\"E001\": \"期望 {expected}\", \"E027\": \"步骤 '{step}' 用了 {capability} {extra}\"}");
        assertThat(DiagnosticOverlays.problems(ENGLISH, messages, false)).containsExactly(
                "E001: 占位符 [expected] 与英文 [actual, expected] 不一致",
                "E027: 占位符 [capability, extra, step] 与英文 [capability, step] 不一致");
    }

    @Test
    @DisplayName("help 与英文 help 比对占位符，而不是与 message 比对")
    void helpComparesAgainstEnglishHelp() throws Exception {
        JsonNode help = json("{\"E001\": \"检查类型。\", \"E027\": \"声明 {capability}。\"}");
        assertThat(DiagnosticOverlays.problems(ENGLISH, help, true)).isEmpty();
        assertThat(DiagnosticOverlays.problems(ENGLISH, help, false)).containsExactly(
                "E001: 占位符 [] 与英文 [actual, expected] 不一致",
                "E027: 占位符 [capability] 与英文 [capability, step] 不一致");
    }

    @Test
    @DisplayName("译文节点缺失或类型错误时报告而不是抛异常")
    void missingTextsNodeIsReported() throws Exception {
        assertThat(DiagnosticOverlays.problems(ENGLISH, null, false)).containsExactly("译文节点缺失或不是对象");
        assertThat(DiagnosticOverlays.problems(ENGLISH, json("[]"), false)).containsExactly("译文节点缺失或不是对象");
    }
}
