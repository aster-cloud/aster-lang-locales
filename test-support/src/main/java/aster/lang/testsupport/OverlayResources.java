package aster.lang.testsupport;

import aster.core.lexicon.LexiconPlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 语言包 overlay 资源路径的检查，供各语言包的 overlay 测试共用。
 *
 * <p>overlay 按 classpath 资源路径加载（core LexiconExporter 用插件的类加载器
 * {@code getResourceAsStream}）。多个语言包 jar 同处一个 classpath 时，相同路径只会命中
 * 第一个 jar，其余语言的 overlay 被静默遮蔽。因此每个语言包的 overlay 必须放在
 * {@code overlays/<语言 id>/} 下，且路径在 classpath 上只有一份。
 */
public final class OverlayResources {

    private OverlayResources() {
    }

    /** overlay 资源应在的目录前缀，如 {@code overlays/de-DE/}。 */
    public static String prefixOf(String lexiconId) {
        return "overlays/" + lexiconId + "/";
    }

    /**
     * 返回违反规则的问题：路径不在 {@code overlays/<id>/} 下，或 classpath 上同一路径不止一份。
     */
    public static List<String> problems(LexiconPlugin plugin, String lexiconId) {
        List<String> problems = new ArrayList<>();
        ClassLoader loader = plugin.getClass().getClassLoader();
        for (Map.Entry<String, String> e : plugin.getOverlayResources().entrySet()) {
            String path = e.getValue();
            if (!path.startsWith(prefixOf(lexiconId))) {
                problems.add(e.getKey() + ": " + path + " 不在 " + prefixOf(lexiconId) + " 下");
            }
            List<URL> hits;
            try {
                hits = Collections.list(loader.getResources(path));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            if (hits.size() != 1) {
                problems.add(e.getKey() + ": classpath 上有 " + hits.size() + " 份 " + path + " " + hits);
            }
        }
        return problems;
    }

    /**
     * 磁盘上（模块的 src/main/resources 下）存在却没有注册的 overlay 文件。
     * 测试以模块目录为工作目录运行，故用相对路径。
     */
    public static List<String> unregisteredOnDisk(LexiconPlugin plugin) {
        Set<String> registered = new HashSet<>(plugin.getOverlayResources().values());
        Path resources = Path.of("src/main/resources");
        Path overlays = resources.resolve("overlays");
        if (!Files.isDirectory(overlays)) {
            throw new IllegalStateException("overlays 目录不存在: " + overlays.toAbsolutePath());
        }
        List<String> unregistered = new ArrayList<>();
        try (Stream<Path> files = Files.walk(overlays)) {
            files.filter(f -> f.toString().endsWith(".json"))
                    .map(f -> resources.relativize(f).toString().replace('\\', '/'))
                    .filter(rel -> !registered.contains(rel))
                    .sorted()
                    .forEach(unregistered::add);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return unregistered;
    }
}
