package com.treelearn.web.api.dto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 共享契约 fixtures 定位（web/contracts/fixtures/）。fixtures 是唯一共享样例来源，
 * 测试工作目录为 web/backend，故根目录按 {@code ../contracts/fixtures} 解析。
 *
 * <p>目录缺失即抛出，绝不静默跳过：路径被移动时测试必须响亮失败，而不是假绿。
 */
final class ContractFixtures {

    static final Path ROOT = Path.of("..", "contracts", "fixtures").toAbsolutePath().normalize();

    private ContractFixtures() {
    }

    /** 取某个 fixtures 子目录，缺失即失败。 */
    static Path dir(String name) {
        Path dir = ROOT.resolve(name);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("fixtures directory must exist: " + dir);
        }
        return dir;
    }

    /** 列出子目录内全部 {@code *.json}，按文件名排序。 */
    static List<Path> jsonFiles(String name) throws IOException {
        try (Stream<Path> stream = Files.list(dir(name))) {
            return stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    /** 去掉 {@code .json} 后缀的文件基名，用于 fixtures 名 → DTO 类型映射。 */
    static String baseName(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }
}
