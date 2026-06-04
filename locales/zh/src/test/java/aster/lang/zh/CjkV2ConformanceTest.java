package aster.lang.zh;

import aster.core.canonicalizer.Canonicalizer;
import aster.core.lexicon.LexiconRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 跨实现 conformance 测试（Java 端）。
 * <p>
 * 对 src/test/resources/conformance/cjk-v2/*.aster 的每个文件，断言 Java
 * canonicalizer 输出与同目录下的 .expected.txt 字节相等。
 * <p>
 * 配套 TS 端测试位于：
 * aster-lang-ts/test/unit/canonicalizer/conformance-cjk-v2.test.ts
 * <p>
 * 二者必须产生 byte-identical 输出。任何 drift 都是 P0 阻塞。
 * 见 ADR-0008。
 */
@DisplayName("CJK v2 Cross-Impl Conformance（Java 端）")
class CjkV2ConformanceTest {

    private Canonicalizer canonicalizer;

    @BeforeEach
    void setUp() {
        canonicalizer = new Canonicalizer(
                LexiconRegistry.getInstance().getOrThrow("zh-CN")
        );
    }

    @TestFactory
    @DisplayName("每个 .aster 输入应与 .expected.txt 字节相等")
    Stream<DynamicTest> conformanceCases() throws IOException {
        // 测试用例清单（与 TS 端保持同步；新增用例需同时更新两端）
        List<String> cases = List.of(
                "01-punctuation-basic",
                "02-string-preservation",
                "03-v2-keywords-all",
                "04-identifier-no-collision"
        );

        List<DynamicTest> tests = new ArrayList<>();
        for (String name : cases) {
            tests.add(DynamicTest.dynamicTest(name + ".aster", () -> {
                String source = readResource("/conformance/cjk-v2/" + name + ".aster");
                String expected = readResource("/conformance/cjk-v2/" + name + ".expected.txt");
                assertNotNull(source, name + ".aster 应存在于 classpath");
                assertNotNull(expected, name + ".expected.txt 应存在于 classpath");

                // 仅验证 v2 新增的 CJK 标点归一化层——这是 Java/TS 字节等价
                // 的唯一可达层面。整 canonicalize 因关键字翻译策略不同
                // 不可能 byte-identical（见 ADR-0008 "范围之外"）。
                String actual = Canonicalizer.normalizeCJKPunctuationOnly(source);

                assertEquals(
                        expected,
                        actual,
                        String.format(
                                "CJK 归一化 drift in %s.aster:%n--- expected ---%n%s%n--- actual ---%n%s%n",
                                name, expected, actual
                        )
                );
            }));
        }
        return tests.stream();
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = CjkV2ConformanceTest.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
