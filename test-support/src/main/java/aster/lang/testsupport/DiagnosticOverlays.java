package aster.lang.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 诊断 overlay（diagnostic-messages.json / diagnostic-help.json）与英文码表的一致性检查。
 *
 * <p>英文码表是 aster-lang-ts 的 shared/error_codes.json（本模块带一份逐字节副本，
 * 由 verifyErrorCodesBaseline 守护）。每个语言的 overlay 必须：
 * <ul>
 *   <li>覆盖码表中的全部错误码，且不含码表之外的码（防孤儿码与旧语义残留）；</li>
 *   <li>每条译文非空，占位符集合（{@code {name}}）与英文原文完全相同——
 *       多出的占位符渲染时会原样露出，缺少的占位符会丢失信息。</li>
 * </ul>
 */
public final class DiagnosticOverlays {

    /** 占位符写法与 aster-lang-ts DiagnosticBuilder 的 formatMessage 一致。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiagnosticOverlays() {
    }

    /** 英文基准：错误码 → {message, help}，按码表顺序。 */
    public record English(String message, String help) {
    }

    /** 读取本模块携带的英文码表副本。 */
    public static Map<String, English> baseline() {
        try (InputStream in = DiagnosticOverlays.class.getResourceAsStream("/diagnostics/error_codes.json")) {
            if (in == null) {
                throw new IllegalStateException("缺少英文码表副本 /diagnostics/error_codes.json");
            }
            return baselineOf(MAPPER.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("无法读取英文码表副本", e);
        }
    }

    /** 把码表 JSON（名称 → {code, message, help, ...}）转为 错误码 → 英文文案。 */
    public static Map<String, English> baselineOf(JsonNode table) {
        Map<String, English> result = new LinkedHashMap<>();
        for (JsonNode entry : table) {
            result.put(entry.get("code").asText(),
                    new English(entry.get("message").asText(), entry.get("help").asText()));
        }
        return result;
    }

    /**
     * 检查一份 overlay 的译文表。
     *
     * @param english  英文基准
     * @param texts    overlay 中的译文对象（messages 或 help 节点）
     * @param useHelp  true 时与英文 help 比对占位符，否则与英文 message 比对
     * @return 问题列表（空表示通过），每条形如「E001: 缺少译文」
     */
    public static List<String> problems(Map<String, English> english, JsonNode texts, boolean useHelp) {
        List<String> problems = new ArrayList<>();
        if (texts == null || !texts.isObject()) {
            problems.add("译文节点缺失或不是对象");
            return problems;
        }
        for (Map.Entry<String, English> e : english.entrySet()) {
            String code = e.getKey();
            JsonNode node = texts.get(code);
            if (node == null) {
                problems.add(code + ": 缺少译文");
                continue;
            }
            String text = node.asText();
            if (!node.isTextual() || text.isBlank()) {
                problems.add(code + ": 译文为空");
                continue;
            }
            String source = useHelp ? e.getValue().help() : e.getValue().message();
            Set<String> expected = placeholders(source);
            Set<String> actual = placeholders(text);
            if (!expected.equals(actual)) {
                problems.add(code + ": 占位符 " + actual + " 与英文 " + expected + " 不一致");
            }
        }
        for (Iterator<String> it = texts.fieldNames(); it.hasNext(); ) {
            String code = it.next();
            if (!english.containsKey(code)) {
                problems.add(code + ": 码表中不存在该错误码");
            }
        }
        return problems;
    }

    /** 文本中出现的占位符名集合（有序，便于报错时比对）。 */
    public static Set<String> placeholders(String text) {
        Set<String> names = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
