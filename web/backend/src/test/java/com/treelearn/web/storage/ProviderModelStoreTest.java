package com.treelearn.web.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.GenerationModel;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-012: Provider/Model configuration persistence on the real sqlite-jdbc engine.
 *
 * <p>语义对照 Android：Provider 侧镜像 {@code com.treelearn.data.db.dao.ProviderDao}
 * （{@code listByCreatedAt}/{@code findById}/{@code findByName}/{@code insert}/{@code updateConfig} +
 * {@code setEnabled}/{@code delete}），Model 侧镜像 {@code ModelDao}（{@code findByProvider}/
 * {@code mergeRemote} 的「只增不删、大小写精确去重」）。用例跑在真实引擎且 {@code foreign_keys=ON} 上，
 * 所以名称唯一、删除级联模型、大小写精确唯一都由数据库背书，而不是本测试自己实现。
 */
class ProviderModelStoreTest extends AbstractStoreTest {

    private ProviderStore providers;
    private ModelStore models;

    @BeforeEach
    void openConfigurationStores() {
        providers = new ProviderStore(jdbc);
        models = new ModelStore(jdbc);
    }

    private static ProviderStore.Provider provider(String id, String name) {
        return new ProviderStore.Provider(id, name, "https://example.test", true, false, T0, T0);
    }

    @Test
    void aDuplicateProviderNameIsRefusedByTheStore() {
        providers.insert(provider(UUID.randomUUID().toString(), "阿里云"));
        StorageException refused = assertThrows(StorageException.class,
                () -> providers.insert(provider(UUID.randomUUID().toString(), "阿里云")));
        assertTrue(refused.getMessage().toLowerCase().contains("providers"),
                "名称唯一冲突必须由数据库唯一索引拒绝，并带上失败的 SQL：" + refused.getMessage());
        assertEquals(2, providers.list().size(),
                "被拒绝的重复名不得留下第二行；内置种子 1 + 成功新增 1");
    }

    @Test
    void updatingNameBaseUrlAndEnabledReadsBackConsistently() {
        String id = UUID.randomUUID().toString();
        providers.insert(provider(id, "旧名"));

        providers.update(id, "新名", "https://new.example.test", false, T0 + 1);
        ProviderStore.Provider afterDisable = providers.get(id).orElseThrow();
        assertEquals("新名", afterDisable.name);
        assertEquals("https://new.example.test", afterDisable.baseUrl);
        assertFalse(afterDisable.enabled);
        assertEquals(T0 + 1, afterDisable.updatedAt);
        assertEquals(T0, afterDisable.createdAt, "创建时间不因更新改变");

        providers.update(id, "新名", "https://new.example.test", true, T0 + 2);
        ProviderStore.Provider afterEnable = providers.get(id).orElseThrow();
        assertTrue(afterEnable.enabled, "启停切换必须读回一致");
        assertEquals(T0 + 2, afterEnable.updatedAt);
    }

    @Test
    void deletingAProviderCascadesToItsModels() {
        String id = UUID.randomUUID().toString();
        providers.insert(provider(id, "级联提供商"));
        models.upsertMany(id, List.of("deepseek-chat", "deepseek-reasoner"),
                () -> UUID.randomUUID().toString(), T0);
        assertEquals(2, models.listForProvider(id).size());
        assertEquals(3, models.listAll().size(), "内置 FLASH 种子 1 + 本次 2");

        providers.delete(id);

        assertFalse(providers.get(id).isPresent());
        assertTrue(models.listForProvider(id).isEmpty(),
                "删除 Provider 必须经 ON DELETE CASCADE 一并删掉它的模型行");
        assertEquals(1, models.listAll().size(), "级联后只剩内置种子模型");
    }

    @Test
    void upsertManyOnlyAddsAndDedupsCaseExactly() {
        String id = UUID.randomUUID().toString();
        providers.insert(provider(id, "大小写提供商"));

        int added = models.upsertMany(id, List.of("Model-X", "model-x", "Model-X"),
                () -> UUID.randomUUID().toString(), T0);
        assertEquals(2, added, "Model-X 与 model-x 大小写不同可共存；批内重复的 Model-X 只入一行");

        int again = models.upsertMany(id, List.of("Model-X", "model-x"),
                () -> UUID.randomUUID().toString(), T0 + 1);
        assertEquals(0, again, "已存在的项不再新增（只增不删）");

        List<String> storedIds = models.listForProvider(id).stream()
                .map(row -> row.modelId).sorted().toList();
        assertEquals(List.of("Model-X", "model-x"), storedIds);
    }

    @Test
    void deletingOneModelRowKeepsItsSiblingsAndItsProvider() {
        String id = UUID.randomUUID().toString();
        providers.insert(provider(id, "逐项删除提供商"));
        models.upsertMany(id, List.of("keep-me", "delete-me"),
                () -> UUID.randomUUID().toString(), T0);

        String doomed = models.listForProvider(id).stream()
                .filter(row -> row.modelId.equals("delete-me")).findFirst().orElseThrow().id;
        models.delete(doomed);

        assertEquals(List.of("keep-me"),
                models.listForProvider(id).stream().map(row -> row.modelId).toList(),
                "只删被点名的那一行，同 Provider 的其它模型保留");
        assertTrue(providers.get(id).isPresent(), "删一个模型不动 Provider 行");
        assertEquals(2, models.listAll().size(), "内置种子 1 + 留下的 1");
    }

    @Test
    void deletingAModelRowThatDoesNotExistIsRefusedLikeAndroid() {
        String id = UUID.randomUUID().toString();
        providers.insert(provider(id, "缺行提供商"));
        models.upsertMany(id, List.of("only-one"), () -> UUID.randomUUID().toString(), T0);

        assertThrows(StorageException.class, () -> models.delete(UUID.randomUUID().toString()),
                "对照 ModelDao.delete：必须恰好命中自己那一行，缺行抛错而非静默成功");
        assertEquals(1, models.listForProvider(id).size(), "被拒的删除不得动到任何行");
    }

    @Test
    void deletingByRowIdentityNeverTouchesAnotherProvidersSameWireId() {
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        providers.insert(provider(first, "甲提供商"));
        providers.insert(provider(second, "乙提供商"));
        models.upsertMany(first, List.of("shared-wire-id"), () -> UUID.randomUUID().toString(), T0);
        models.upsertMany(second, List.of("shared-wire-id"), () -> UUID.randomUUID().toString(), T0);

        models.delete(models.listForProvider(first).get(0).id);

        assertTrue(models.listForProvider(first).isEmpty(), "甲的行被删");
        assertEquals(List.of("shared-wire-id"),
                models.listForProvider(second).stream().map(row -> row.modelId).toList(),
                "同名 wire id 属于另一 Provider，必须原样保留——删除按行身份而非 wire id");
    }

    @Test
    void theBuiltinSeedIsReadableAndSurvivesAMerge() {
        ProviderStore.Provider seed = providers
                .findByName(DesktopSchema.BUILTIN_DEEPSEEK_NAME).orElseThrow();
        assertEquals(DesktopSchema.BUILTIN_DEEPSEEK_PROVIDER_ID, seed.id);
        assertTrue(seed.builtin, "内置 DeepSeek 种子行必须被标记为内置");
        assertTrue(seed.enabled);
        assertEquals(DesktopSchema.BUILTIN_SEED_EPOCH_MILLIS, seed.createdAt);

        List<String> seededModels = models.listForProvider(seed.id).stream()
                .map(row -> row.modelId).toList();
        assertTrue(seededModels.contains(GenerationModel.FLASH.wireId), "内置 FLASH 模型种子必须可读");

        int added = models.upsertMany(seed.id,
                List.of(GenerationModel.FLASH.wireId, "brand-new"),
                () -> UUID.randomUUID().toString(), T0);
        assertEquals(1, added, "已存在的内置模型不被重写，只新增缺失项");

        assertEquals(DesktopSchema.BUILTIN_SEED_EPOCH_MILLIS,
                providers.get(seed.id).orElseThrow().createdAt, "合并不得改动 Provider 行");
        assertTrue(models.listForProvider(seed.id).stream()
                .anyMatch(row -> row.modelId.equals(GenerationModel.FLASH.wireId)));
    }
}
