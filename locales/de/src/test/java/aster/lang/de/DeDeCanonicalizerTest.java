package aster.lang.de;

import aster.core.canonicalizer.Canonicalizer;
import aster.core.lexicon.LexiconRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 德语特定规范化测试。
 * <p>
 * 从 aster-lang-core CanonicalizerTest 迁移而来，验证德语独有的规范化规则。
 */
@DisplayName("德语规范化测试")
class DeDeCanonicalizerTest {

    private Canonicalizer deCanonicalizer;

    @BeforeEach
    void setUp() {
        deCanonicalizer = new Canonicalizer(LexiconRegistry.getInstance().getOrThrow("de-DE"));
    }

    @Test
    @DisplayName("customRules 执行：ASCII umlaut → Unicode umlaut")
    void customRules_UmlautNormalization() {
        String input = "groesser als 10";
        String result = deCanonicalizer.canonicalize(input);
        assertTrue(result.contains("größer") || result.contains(">"),
                "groesser 应规范化为 größer（然后翻译为 >），实际结果: " + result);
    }

    @Test
    @DisplayName("customRules 执行：ue → ü")
    void customRules_UeToUe() {
        String input = "gib zurueck 42.";
        String result = deCanonicalizer.canonicalize(input);
        assertTrue(result.contains("Return"),
                "德语 'gib zurück'（经 umlaut 规范化后）应翻译为 'Return'，实际结果: " + result);
    }

    @Test
    @DisplayName("德语关键词翻译：Modul → Module")
    void keywordTranslation_Module() {
        String input = "Modul test.simple.";
        String result = deCanonicalizer.canonicalize(input);
        assertTrue(result.contains("Module"),
                "德语 'Modul' 应翻译为 'Module'，实际结果: " + result);
    }

    @Test
    @DisplayName("跨模块钉版本：verwende/version/als → use/version/as（ADR 0015）")
    void keywordTranslation_ImportVersion() {
        // verwende X version N als Y → use X version N as Y（Java grammar 可解析形式）
        String result = deCanonicalizer.canonicalize("verwende risk.Scoring version 2 als Score.");
        assertTrue(result.contains("use"), "'verwende' 应翻译为 'use'，实际: " + result);
        assertTrue(result.contains("version 2"), "'version 2' 应保留为 'version 2'，实际: " + result);
        assertTrue(result.contains("as Score"), "'als Score' 应翻译为 'as Score'，实际: " + result);
    }

    @Test
    @DisplayName("德语关键词翻译：Regel → Rule")
    void keywordTranslation_Rule() {
        String input = "Regel main:";
        String result = deCanonicalizer.canonicalize(input);
        assertTrue(result.contains("Rule"),
                "德语 'Regel' 应翻译为 'Rule'，实际结果: " + result);
    }

    @Test
    @DisplayName("德语关键词翻译：Ergebnis ist → the result is → Return")
    void keywordTranslation_ResultIs() {
        String input = "Ergebnis ist 42.";
        String result = deCanonicalizer.canonicalize(input);
        assertTrue(result.contains("Return"),
                "德语 'Ergebnis ist' 应最终转换为 'Return'，实际结果: " + result);
    }

    @Test
    @DisplayName("德语冠词移除")
    void articleRemoval() {
        String input = "der Wert ist ein Text";
        String result = deCanonicalizer.canonicalize(input);
        assertFalse(result.contains(" der "), "冠词 'der' 应被移除");
        assertFalse(result.contains(" ein "), "冠词 'ein' 应被移除");
    }

    @Test
    @DisplayName("德语 customRules 保护字符串字面量")
    void customRules_PreserveStrings() {
        String input = "Return \"groesser Fehler\".";
        String result = deCanonicalizer.canonicalize(input);
        assertTrue(result.contains("\"groesser Fehler\""),
                "字符串内的文本不应被 customRules 修改，实际结果: " + result);
    }

    @Test
    @DisplayName("德语完整示例")
    void completeExample() {
        String input = """
                Modul test.deutsch.
                Regel bewerten gegeben alter: Int:
                  wenn alter unter 18:
                    gib zurueck "minderjaehrig".
                  sonst:
                    gib zurueck "erwachsen".
                """;
        String result = deCanonicalizer.canonicalize(input);
        assertTrue(result.contains("Module"), "Modul → Module");
        assertTrue(result.contains("Rule"), "Regel → Rule");
        assertTrue(result.contains("given"), "gegeben → given");
        assertTrue(result.contains("If"), "wenn → If");
        assertTrue(result.contains("Return"), "gib zurück → Return");
        assertTrue(result.contains("Otherwise"), "sonst → Otherwise");
    }
}
