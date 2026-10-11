package com.treelearn.web.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * 数据目录下的非敏感偏好文件（任务 T-2026-10-10-036），承载模型分工与预算/建议开关。
 *
 * <p>语义对照 Android {@code com.treelearn.data.settings.SettingsRepository}（{@code treelearn_settings}
 * SharedPreferences，<strong>非 SQLite 表</strong>）：同一批事实——`role_chat_model`/`role_fast_model`/
 * `role_translation_model`（值形如 `providerId\u001FmodelId`）、`thinking_budget`、`answer_suggestions`。
 * 桌面十二张准入表没有分工表，故用等价的非敏感偏好文件承载；不新增永久表、不改 {@link DesktopSchema}。
 *
 * <p>只保存非敏感值：本文件绝不含 API Key（Key 只经 {@code SecretPort} 落 Windows 凭据管理器）。读写整体
 * 加载/覆盖保存；单机本地单人使用，不做跨进程并发假设。
 */
public final class DesktopPreferences {

    private final Path file;

    public DesktopPreferences(Path file) {
        this.file = file;
    }

    public synchronized Optional<String> get(String key) {
        return Optional.ofNullable(load().getProperty(key));
    }

    public synchronized void put(String key, String value) {
        Properties properties = load();
        properties.setProperty(key, value);
        store(properties);
    }

    public synchronized boolean getBoolean(String key, boolean fallback) {
        String value = load().getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public synchronized void putBoolean(String key, boolean value) {
        put(key, Boolean.toString(value));
    }

    private Properties load() {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException failure) {
                throw new StorageException("reading the local settings file failed: " + file,
                        failure);
            }
        }
        return properties;
    }

    private void store(Properties properties) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                properties.store(out, "TreeLearn local web preferences (non-sensitive)");
            }
        } catch (IOException failure) {
            throw new StorageException("writing the local settings file failed: " + file, failure);
        }
    }
}
