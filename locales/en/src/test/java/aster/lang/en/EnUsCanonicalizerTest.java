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

        @Test
        @DisplayName("不吞模块路径里的单字母大写段（risk.A 不应变 risk.）")
        void testRemoveArticles_PreservesDottedModulePathSegment() {
            // ADR 0015：单字母大写标识符段（如 risk.A、A.a）此前被 CASE_INSENSITIVE
            // 冠词正则误判为冠词 'a' 而吞掉。冠词移除应排除 dotted 上下文。
            assertEquals("Use risk.A version 1 as Score.",
                canonicalizer.canonicalize("Use risk.A version 1 as Score."));
            assertEquals("Return A.a(amount).",
                canonicalizer.canonicalize("Return A.a(amount)."));
        }

        @Test
        @DisplayName("真冠词仍移除（修复未误伤正常冠词）")
        void testRemoveArticles_RealArticlesStillRemoved() {
            assertEquals("Use risk.Scoring version 1 as Score.",
                canonicalizer.canonicalize("Use risk.Scoring version 1 as Score."));
            // 句中真冠词 a/the 仍被移除
            assertEquals("define function to return value",
                canonicalizer.canonicalize("define the function to return a value"));
        }

        // ============================================================
        // 标识符保护：a/an/the 当参数名/变量名时不应被当冠词吞掉。
        // 判据：冠词后紧跟声明关键字 as、列表分隔符逗号、运算符词或句末/标点
        // 时，它是标识符（其后没有被它修饰的名词），必须保留。
        // ============================================================

        @Test
        @DisplayName("a 作参数名（后跟 as）不吞")
        void testArticleAsIdentifier_BeforeAs() {
            assertEquals("Rule add given a as Int, b as Int, produce Int:",
                canonicalizer.canonicalize("Rule add given a as Int, b as Int, produce Int:"));
        }

        @Test
        @DisplayName("a 在参数列表（后跟逗号）不吞")
        void testArticleAsIdentifier_BeforeComma() {
            assertEquals("given a, b, c",
                canonicalizer.canonicalize("given a, b, c"));
        }

        @Test
        @DisplayName("a 作操作数（后跟运算符）不吞——运算符词翻译成符号是预期的")
        void testArticleAsIdentifier_BeforeOperator() {
            // plus → + 是正常的运算符翻译；关键是标识符 a 被保留（不再变 `Return + b`）
            assertEquals("Return a + b.",
                canonicalizer.canonicalize("Return a plus b."));
            // equals to → ==；逻辑 and/or 保持词形。标识符 a 保留
            assertEquals("Return a == 1 or b == 2 and c == 3.",
                canonicalizer.canonicalize(
                    "Return a equals to 1 or b equals to 2 and c equals to 3."));
        }

        @Test
        @DisplayName("the/an 作标识符（后跟 as / 逗号 / 运算符）不吞")
        void testArticleAsIdentifier_TheAndAn() {
            assertEquals("given the as Int, an as Text",
                canonicalizer.canonicalize("given the as Int, an as Text"));
            // the/an 标识符保留；plus → +
            assertEquals("Return the + an.",
                canonicalizer.canonicalize("Return the plus an."));
        }

        @Test
        @DisplayName("冠词后紧跟句末/冒号（无修饰名词）不吞")
        void testArticleAsIdentifier_BeforeTerminator() {
            assertEquals("Return a.",
                canonicalizer.canonicalize("Return a."));
        }

        @Test
        @DisplayName("行末孤立标识符（无句末点）不吞——\\n 锚点与 EOF 哨兵")
        void testArticleAsIdentifier_AtLineEnd() {
            // 多行：a 在行末后跟 \n
            assertEquals("Let a be 1\nReturn a",
                canonicalizer.canonicalize("Let a be 1\nReturn a"));
            // EOF：整个输入末尾无换行（哨兵换行兜底）
            assertEquals("Return a",
                canonicalizer.canonicalize("Return a"));
            // 行末是 the / an 同样保留
            assertEquals("Return the\nReturn an",
                canonicalizer.canonicalize("Return the\nReturn an"));
        }

        @Test
        @DisplayName("行末标识符保护不误伤字符串前的真冠词")
        void testArticleAtLineEnd_DoesNotBreakStringArticle() {
            // the "first" 的 the 落在段末，但后面不是 \n（是引号），仍被当真冠词移除
            assertEquals("\"first\" and \"second\"",
                canonicalizer.canonicalize("the \"first\" and the \"second\""));
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
