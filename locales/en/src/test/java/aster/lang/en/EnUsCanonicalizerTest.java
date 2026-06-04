package aster.lang.en;

import aster.core.canonicalizer.Canonicalizer;
import aster.core.lexicon.LexiconRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 英语特定规范化测试。
 * <p>
 * 从 aster-lang-core CanonicalizerTest 迁移而来，验证英语独有的规范化规则。
 */
@DisplayName("英语规范化测试")
class EnUsCanonicalizerTest {

    private Canonicalizer canonicalizer;

    @BeforeEach
    void setUp() {
        canonicalizer = new Canonicalizer();
    }

    // ============================================================
    // 多词关键字大小写规范化测试
    // ============================================================

    @Nested
    @DisplayName("多词关键字规范化")
    class MultiWordKeywordTests {

        @Test
        void testNormalizeMultiWordKeywords_ModuleIs() {
            String input = "Module app.";
            String expected = "Module app.";
            assertEquals(expected, canonicalizer.canonicalize(input));
        }

        @Test
        void testNormalizeMultiWordKeywords_OneOf() {
            String input = "As One Of the options.";
            String expected = "as one of options.";
            assertEquals(expected, canonicalizer.canonicalize(input));
        }

        @Test
        void testNormalizeMultiWordKeywords_WaitFor() {
            String input = "Wait For the result.";
            String expected = "wait for result.";
            assertEquals(expected, canonicalizer.canonicalize(input));
        }
    }

    // ============================================================
    // 冠词移除测试
    // ============================================================

    @Nested
    @DisplayName("冠词移除")
    class ArticleRemovalTests {

        @Test
        void testRemoveArticles_Basic() {
            String input = "define the function to return a value";
            String expected = "define function to return value";
            assertEquals(expected, canonicalizer.canonicalize(input));
        }

        @Test
        void testRemoveArticles_PreserveInStrings() {
            String input = "print \"the quick brown fox\"";
            String expected = "print \"the quick brown fox\"";
            assertEquals(expected, canonicalizer.canonicalize(input));
        }

        @Test
        void testRemoveArticles_MixedContext() {
            String input = "call the function with \"the parameter\"";
            String expected = "call function with \"the parameter\"";
            assertEquals(expected, canonicalizer.canonicalize(input));
        }

        @Test
        void testRemoveArticles_AllArticleTypes() {
            String input = "a function takes an input and returns the result";
            String expected = "function takes input and returns result";
            assertEquals(expected, canonicalizer.canonicalize(input));
        }

        @Test
        void testRemoveArticles_OnlyWithTrailingSpace() {
            String input = "the function with parameter";
            String expected = "function with parameter";
            assertEquals(expected, canonicalizer.canonicalize(input));
        }
    }

    // ============================================================
    // 英语属格 's -> . 转换测试
    // ============================================================

    @Nested
    @DisplayName("英语属格转换")
    class PossessiveTests {

        @Test
        void testEnglishPossessive_Basic() {
            String input = "driver's age";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("driver.age"),
                    "driver's age 应转换为 driver.age，实际结果: " + result);
        }

        @Test
        void testEnglishPossessive_Multiple() {
            String input = "driver's accidents";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("driver.accidents"),
                    "driver's accidents 应转换为 driver.accidents");
        }

        @Test
        void testEnglishPossessive_PreserveInStrings() {
            String input = "Return \"driver's license\".";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("\"driver's license\""),
                    "字符串内的 's 不应转换");
        }

        @Test
        @DisplayName("Unicode 标识符的 's 也能正确转换")
        void testEnglishPossessive_Unicode() {
            String input = "Müller's score";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("Müller.score"),
                    "Unicode 标识符 Müller's score 应转换为 Müller.score，实际结果: " + result);
        }
    }

    // ============================================================
    // "The result is X" -> "Return X" 重写测试
    // ============================================================

    @Nested
    @DisplayName("Result Is 重写")
    class ResultIsTests {

        @Test
        void testResultIs_Basic() {
            String input = "The result is 42.";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("Return 42."),
                    "The result is 42 应重写为 Return 42，实际结果: " + result);
        }

        @Test
        void testResultIs_WithExpression() {
            String input = "  The result is Quote with approved = true.";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("Return Quote with approved = true."),
                    "'The result is' 应重写为 'Return'，实际结果: " + result);
        }

        @Test
        void testResultIs_CaseInsensitive() {
            String input = "the result is 42.";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("Return 42."),
                    "小写 'the result is' 也应重写");
        }
    }

    // ============================================================
    // "Set X to Y" -> "Let X be Y" 重写测试
    // ============================================================

    @Nested
    @DisplayName("Set To 重写")
    class SetToTests {

        @Test
        void testSetTo_Basic() {
            String input = "Set x to 42.";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("Let x be 42."),
                    "Set x to 42 应重写为 Let x be 42，实际结果: " + result);
        }

        @Test
        void testSetTo_WithExpression() {
            String input = "  Set basePremium to calculateBase with driver, vehicle.";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("Let basePremium be calculateBase with driver, vehicle."),
                    "'Set X to Y' 应重写为 'Let X be Y'，实际结果: " + result);
        }
    }

    // ============================================================
    // 比较运算同义词测试
    // ============================================================

    @Nested
    @DisplayName("比较运算同义词")
    class ComparisonSynonymTests {

        @Test
        void testComparisonSynonym_Under() {
            String input = "x under 18";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains("<"),
                    "'under' 应翻译为 '<'，实际结果: " + result);
        }

        @Test
        void testComparisonSynonym_Over() {
            String input = "x over 3";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains(">"),
                    "'over' 应翻译为 '>'，实际结果: " + result);
        }

        @Test
        void testComparisonSynonym_MoreThan() {
            String input = "x more than 3";
            String result = canonicalizer.canonicalize(input);
            assertTrue(result.contains(">"),
                    "'more than' 应翻译为 '>'，实际结果: " + result);
        }
    }

    // ============================================================
    // 英语 Lexicon 无翻译测试
    // ============================================================

    @Test
    @DisplayName("英文 Lexicon 不应进行翻译")
    void testEnglishLexiconNoTranslation() {
        var enCanonicalizer = new Canonicalizer(LexiconRegistry.getInstance().getOrThrow("en-US"));
        String input = "if condition return true";
        String result = enCanonicalizer.canonicalize(input);
        assertEquals("if condition return true", result);
    }

    // ============================================================
    // 多词关键词不修改字符串字面量
    // ============================================================

    @Test
    @DisplayName("多词关键词规范化不应修改字符串字面量")
    void testMultiWordKeyword_NotModifyStrings() {
        String input = "Return \"This Module Is test\".";
        String result = new Canonicalizer().canonicalize(input);
        assertTrue(result.contains("\"This Module Is test\""),
                "字符串字面量内的多词关键词不应被修改，实际结果: " + result);
    }
}
