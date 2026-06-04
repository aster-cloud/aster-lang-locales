package aster.lang.zh;

import aster.core.canonicalizer.Canonicalizer;
import aster.core.lexicon.LexiconRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 中文特定规范化测试。
 * <p>
 * 从 aster-lang-core CanonicalizerTest 迁移而来，验证中文独有的规范化规则。
 */
@DisplayName("中文规范化测试")
class ZhCnCanonicalizerTest {

    private Canonicalizer zhCanonicalizer;

    @BeforeEach
    void setUp() {
        zhCanonicalizer = new Canonicalizer(LexiconRegistry.getInstance().getOrThrow("zh-CN"));
    }

    // ============================================================
    // 中文关键词翻译测试
    // ============================================================

    @Nested
    @DisplayName("关键词翻译")
    class KeywordTranslationTests {

        @Test
        void testChineseKeywordTranslation_ControlFlow() {
            String input = "如果 条件";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("If"),
                    "中文'如果'应翻译为'If'，实际结果: " + result);
        }

        @Test
        void testChineseKeywordTranslation_Return() {
            String input = "返回 值";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("Return"),
                    "中文'返回'应翻译为'Return'，实际结果: " + result);
        }

        @Test
        void testChineseKeywordTranslation_Boolean() {
            // v2 关键字：真值/假值/或者/并且/不是
            String input = "真值 并且 假值 或者 不是";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("true"), "中文'真值'应翻译为'true'");
            assertTrue(result.contains("and"), "中文'并且'应翻译为'and'");
            assertTrue(result.contains("false"), "中文'假值'应翻译为'false'");
            assertTrue(result.contains("or"), "中文'或者'应翻译为'or'");
            assertTrue(result.contains("not"), "中文'不是'应翻译为'not'");
        }

        @Test
        void testChineseKeywordTranslation_ModuleDecl() {
            String input = "模块 测试";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("Module"),
                    "中文'模块'应翻译为'Module'，实际结果: " + result);
        }

        @Test
        void testChineseKeywordTranslation_Variable() {
            // v2 关键字：'定义为' 替代 v1 的 '为'（BE）
            String input = "令 变量 定义为 10";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("Let"), "中文'令'应翻译为'Let'");
            assertTrue(result.contains("be"), "中文'令...定义为'中的'定义为'应翻译为'be'");
        }

        @Test
        void testChineseKeywordTranslation_PreserveIdentifiers() {
            String input = "令 中文变量名 为 10";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("中文变量名"),
                    "中文标识符应保留，实际结果: " + result);
        }

        @Test
        void testChineseKeywordTranslation_PreserveStrings() {
            // 字符串内的 v1/v2 关键字字符（如'匹配于'）都应保留不变
            String input = "返回 \"匹配于 这是字符串\"";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("\"匹配于 这是字符串\""),
                    "字符串内的中文应保留，实际结果: " + result);
            assertTrue(result.contains("Return"), "'返回'应翻译为'Return'");
        }
    }

    // ============================================================
    // 令...为 与 若...为 歧义消解测试
    // ============================================================

    @Nested
    @DisplayName("为 歧义消解")
    class AmbiguityDisambiguationTests {

        @Test
        @DisplayName("令...定义为 + 匹配于...当 混合使用时正确翻译（v2 关键字）")
        void testLetBeAndMatchWhenCombined() {
            // v2 关键字：BE='定义为'，MATCH='匹配于'，WHEN='当'
            // 不再依赖原 v1 的单字 '为' 同义共享导致的歧义消解，
            // 因此本测试结构上简化了
            String input = "令 额度 定义为 100。\n匹配于 x：\n  当 1，返回 额度。\n  当 2，返回 200。\n";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("Let") && result.contains("be"),
                    "'令...定义为'应翻译为'Let...be'，实际结果: " + result);
            assertTrue(result.contains("When"),
                    "'匹配于...当'中的'当'应翻译为'When'，实际结果: " + result);
        }
    }

    // ============================================================
    // 中文标识符保护测试（词边界检测）
    // ============================================================

    @Nested
    @DisplayName("标识符保护")
    class IdentifierProtectionTests {

        @Test
        void testChineseIdentifier_NotBreakByKeyword() {
            // v2 关键字：MATCH 从 '若' 升级到 '匹配于'，标识符 '若何' 不再可能与
            // MATCH 冲突。本测试现在验证 BE='定义为' 与标识符的边界。
            String input = "令 若何 定义为 10";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("若何"),
                    "标识符'若何'应保留，实际结果: " + result);
            assertTrue(result.contains("Let"), "'令'应翻译为'Let'");
            assertTrue(result.contains("be"), "'定义为'应翻译为'be'");
        }

        @Test
        void testChineseIdentifier_KeywordAtBoundary() {
            String input = "如果 条件成立";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("If"),
                    "独立的'如果'关键字应翻译为'If'，实际结果: " + result);
            assertTrue(result.contains("条件成立"), "'条件成立'标识符应保留");
        }

        @Test
        void testChineseIdentifier_MultipleKeywordsInIdentifier() {
            // v2 关键字下，'若干' 不再与 MATCH（v2='匹配于'）冲突
            String input = "令 返回值 定义为 若干";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("返回值"),
                    "标识符'返回值'不应被'返回'关键词破坏，实际结果: " + result);
            assertTrue(result.contains("若干"),
                    "标识符'若干'应保留（v2 下 MATCH 已升级为'匹配于'）");
        }

        @Test
        void testChineseIdentifier_KeywordFollowedByPunctuation() {
            String input = "如果:";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("If"),
                    "关键字后面是标点时应翻译，实际结果: " + result);
        }
    }

    // ============================================================
    // 混合脚本标识符测试（ASCII + 中文）
    // ============================================================

    @Nested
    @DisplayName("混合脚本标识符")
    class MixedScriptTests {

        @Test
        void testMixedScript_ChineseAndAscii() {
            String input = "令 变量若value 为 10";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("变量若value"),
                    "混合脚本标识符不应被关键词替换破坏，实际结果: " + result);
            assertTrue(result.contains("Let"), "'令'应翻译为'Let'");
        }

        @Test
        void testMixedScript_UnderscoreIdentifier() {
            String input = "令 若_identifier 为 10";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("若_identifier"),
                    "下划线标识符不应被关键词替换破坏，实际结果: " + result);
        }

        @Test
        void testMixedScript_AsciiSurrounding() {
            String input = "令 A若B 为 10";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("A若B"),
                    "ASCII包围的标识符不应被关键词替换破坏，实际结果: " + result);
        }

        @Test
        void testMixedScript_NumberSuffix() {
            String input = "令 若123 为 10";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("若123"),
                    "数字后缀标识符不应被关键词替换破坏，实际结果: " + result);
        }
    }

    // ============================================================
    // 独立关键词（前后有空格）
    // ============================================================

    @Test
    @DisplayName("独立关键词（前后有空格）")
    void testKeyword_StandaloneWithSpace() {
        String input = "如果 条件 返回 值";
        String result = zhCanonicalizer.canonicalize(input);
        assertTrue(result.contains("If"), "独立的'如果'应翻译为'If'");
        assertTrue(result.contains("Return"), "独立的'返回'应翻译为'Return'");
    }

    // ============================================================
    // 函数调用内字符串参数测试
    // ============================================================

    @Nested
    @DisplayName("函数调用与字符串参数")
    class FunctionCallStringArgTests {

        @Test
        void testChineseReturn_WithFunctionCallAndChineseString() {
            String input = "返回 len(「hello」)。";
            String result = zhCanonicalizer.canonicalize(input);

            assertTrue(result.contains("Return"),
                    "'返回'应翻译为'Return'，实际结果: " + result);
            assertTrue(result.contains("len(\"hello\")"),
                    "函数调用应保持完整，实际结果: " + result);
            assertFalse(result.contains("len(\"hello\")..)"), "不应在括号后有双句号");
            assertFalse(result.contains("len(\"hello\".)"), "不应在括号内有句号");

            String trimmed = result.trim();
            assertTrue(trimmed.endsWith("."), "应以单个句号结尾，实际结果: " + result);
            assertFalse(trimmed.endsWith(".."), "不应以双句号结尾，实际结果: " + result);
        }

        @Test
        void testChineseReturn_WithNestedFunctionCall() {
            String input = "返回 upper(trim(「 hello 」))。";
            String result = zhCanonicalizer.canonicalize(input);

            assertTrue(result.contains("Return"), "'返回'应翻译为'Return'");
            assertTrue(result.contains("upper(trim("), "嵌套函数调用结构应保持完整");
        }

        @Test
        void testChineseReturn_WithMultipleStringArgs() {
            String input = "返回 concat(「hello」, 「world」)。";
            String result = zhCanonicalizer.canonicalize(input);

            assertTrue(result.contains("Return"), "'返回'应翻译为'Return'");
            assertTrue(result.contains("concat("), "函数名应保留");
            assertTrue(result.contains("\"hello\"") && result.contains("\"world\""),
                    "字符串参数应正确转换");
        }
    }

    // ============================================================
    // 中文"的"无空格模式测试
    // ============================================================

    @Nested
    @DisplayName("中文'的'所有格")
    class ChinesePossessiveTests {

        @Test
        @DisplayName("无空格模式：用户的名字 -> 用户.名字")
        void testChinesePossessive_NoSpace() {
            String input = "用户的名字";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("用户.名字"),
                    "无空格模式 '用户的名字' 应转换为 '用户.名字'，实际结果: " + result);
        }

        @Test
        @DisplayName("单字前缀不触发（保护复合标识符）")
        void testChinesePossessive_NoSpace_SingleCharPrefix() {
            String input = "我的结构体";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("我的结构体"),
                    "单字前缀的'的'不应触发成员访问转换，实际结果: " + result);
        }

        @Test
        @DisplayName("保护字符串字面量")
        void testChinesePossessive_NoSpace_PreserveStrings() {
            String input = "返回 \"用户的名字\"";
            String result = zhCanonicalizer.canonicalize(input);
            assertTrue(result.contains("\"用户的名字\""),
                    "字符串内的'的'不应转换，实际结果: " + result);
        }
    }
}
