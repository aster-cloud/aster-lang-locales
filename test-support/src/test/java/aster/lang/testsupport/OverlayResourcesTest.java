package aster.lang.testsupport;

import aster.core.lexicon.Lexicon;
import aster.core.lexicon.LexiconPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("overlay 资源路径检查")
class OverlayResourcesTest {

    /** 只提供 overlay 映射的插件桩；createLexicon 不会被路径检查调用。 */
    private static LexiconPlugin pluginWith(Map<String, String> overlays) {
        return new LexiconPlugin() {
            @Override
            public Lexicon createLexicon() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<String, String> getOverlayResources() {
                return overlays;
            }
        };
    }

    @Test
    @DisplayName("位于 overlays/<id>/ 下且 classpath 上恰好一份时无问题")
    void namespacedUniquePathPasses() {
        assertThat(OverlayResources.problems(pluginWith(Map.of("k", "overlays/xx-XX/sample.json")), "xx-XX")).isEmpty();
    }

    @Test
    @DisplayName("不在语言目录下、或 classpath 上找不到都会报告")
    void wrongPrefixAndMissingResourceAreReported() {
        assertThat(OverlayResources.problems(pluginWith(Map.of("k", "overlays/missing.json")), "xx-XX"))
                .containsExactly(
                        "k: overlays/missing.json 不在 overlays/xx-XX/ 下",
                        "k: classpath 上有 0 份 overlays/missing.json []");
    }
}
