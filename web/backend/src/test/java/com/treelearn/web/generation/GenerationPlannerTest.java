package com.treelearn.web.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.generation.GenerationPlanner.Assignment;
import com.treelearn.web.generation.GenerationPlanner.Budget;
import com.treelearn.web.generation.GenerationPlanner.Choice;
import com.treelearn.web.generation.GenerationPlanner.Fallback;
import com.treelearn.web.generation.GenerationPlanner.Outcome;
import com.treelearn.web.generation.GenerationPlanner.Plan;
import com.treelearn.web.generation.GenerationPlanner.Refusal;
import com.treelearn.web.generation.GenerationPlanner.Role;
import com.treelearn.web.storage.DatabaseMigrator;
import com.treelearn.web.storage.DesktopDatabase;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.ModelStore;
import com.treelearn.web.storage.ProviderStore;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-2026-10-10-023: role resolution and its stable refusals (contract §5.12 / §3.3) on the real
 * sqlite-jdbc provider/model tables. The four cases the task names — no provider, no key, no model
 * and fallback — are each driven end to end; the refusal tokens are the contract's, never new ones.
 */
class GenerationPlannerTest {

    private static final long T0 = 1_752_000_000_000L;

    @TempDir
    Path dataDir;

    JdbcSupport jdbc;
    ProviderStore providers;
    ModelStore models;
    FakeSecrets secrets;
    GenerationPlanner planner;

    @BeforeEach
    void setUp() {
        new DatabaseMigrator().migrate(dataDir.resolve("treelearn.db"));
        jdbc = new JdbcSupport(new DesktopDatabase(dataDir));
        providers = new ProviderStore(jdbc);
        models = new ModelStore(jdbc);
        secrets = new FakeSecrets();
        // The migration seeds one builtin DeepSeek provider; each case starts from a clean config so
        // the four resolution branches are decided by the rows the case wrote, not by the seed.
        jdbc.update("DELETE FROM providers", JdbcSupport.NO_PARAMS);
        planner = new GenerationPlanner(providers, models, secrets);
    }

    /** An in-memory credential port: only provider ids with a saved key report present. */
    private static final class FakeSecrets implements SecretPort {

        private final Set<String> keyed = new HashSet<>();

        void saveKey(String providerId) {
            keyed.add(SecretPort.providerTarget(providerId));
        }

        @Override
        public void save(String target, char[] secret) {
            keyed.add(target);
        }

        @Override
        public boolean has(String target) {
            return keyed.contains(target);
        }

        @Override
        public Optional<char[]> read(String target) {
            return Optional.empty();
        }

        @Override
        public void delete(String target) {
            keyed.remove(target);
        }
    }

    private String provider(boolean enabled, boolean builtin) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO providers(id, name, base_url, enabled, is_builtin, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, id);
                    statement.setString(2, "p-" + id);
                    statement.setString(3, "https://api.deepseek.com");
                    statement.setInt(4, enabled ? 1 : 0);
                    statement.setInt(5, builtin ? 1 : 0);
                    statement.setLong(6, T0);
                    statement.setLong(7, T0);
                });
        return id;
    }

    private void model(String providerId, String modelId) {
        jdbc.update("INSERT INTO models(id, provider_id, model_id, display_name, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setString(1, UUID.randomUUID().toString());
                    statement.setString(2, providerId);
                    statement.setString(3, modelId);
                    statement.setString(4, modelId);
                    statement.setLong(5, T0);
                });
    }

    @Test
    void aConfiguredAvailableModelResolvesWithNoneAndNoRefusal() {
        String provider = provider(true, false);
        model(provider, GenerationModel.PRO.wireId);
        secrets.saveKey(provider);

        Plan plan = planner.plan(new Choice(provider, GenerationModel.PRO.wireId), null, null,
                Budget.AUTO);

        Outcome chat = plan.roles.get(Role.CHAT);
        assertEquals(provider, chat.assignment.providerId);
        assertEquals(GenerationModel.PRO.wireId, chat.assignment.modelId);
        assertEquals(Fallback.NONE, chat.assignment.fallback);
        assertNull(chat.refusal, "a configured, keyed, available model has nothing to refuse");
        assertEquals(Budget.AUTO, plan.budget);
    }

    @Test
    void noEnabledProviderYieldsEmptyAndNoModels() {
        Plan plan = planner.plan(new Choice("11111111-1111-1111-1111-111111111111", "deepseek"),
                null, null, Budget.OFF);

        for (Role role : Role.values()) {
            Outcome outcome = plan.roles.get(role);
            assertEquals(Fallback.EMPTY, outcome.assignment.fallback);
            assertEquals(Refusal.NO_MODELS, outcome.refusal);
        }
    }

    @Test
    void anEnabledProviderWithoutAKeyIsRefusedWithKeyMissing() {
        String provider = provider(true, false);
        model(provider, GenerationModel.FLASH.wireId);
        // No key saved on purpose.

        Outcome chat = planner.plan(new Choice(provider, GenerationModel.FLASH.wireId), null,
                null, Budget.AUTO).roles.get(Role.CHAT);

        assertEquals(Fallback.NONE, chat.assignment.fallback, "the model itself resolved");
        assertEquals(Refusal.KEY_MISSING, chat.refusal,
                "a keyless provider must not be called, whatever else resolved");
    }

    @Test
    void aMissingConfiguredPairFallsBackToTheBuiltinThenTheEarliest() {
        String builtin = provider(true, true);
        model(builtin, GenerationModel.FLASH.wireId);
        String other = provider(true, false);
        model(other, GenerationModel.PRO.wireId);
        secrets.saveKey(builtin);
        secrets.saveKey(other);
        // The role is configured for a pair that no longer exists.
        Choice gone = new Choice("22222222-2222-2222-2222-222222222222", "gone");

        Outcome builtinFallback = planner.plan(gone, null, null, Budget.AUTO).roles.get(Role.CHAT);
        assertEquals(Fallback.BUILTIN, builtinFallback.assignment.fallback);
        assertEquals(builtin, builtinFallback.assignment.providerId);
        assertNull(builtinFallback.refusal, "the builtin default is keyed, so it can be called");
    }

    @Test
    void aDisabledProviderContributesNoModelAndFallsBack() {
        String disabled = provider(false, false);
        model(disabled, GenerationModel.FLASH.wireId);
        String enabled = provider(true, false);
        model(enabled, GenerationModel.PRO.wireId);
        secrets.saveKey(enabled);

        Outcome chat = planner.plan(new Choice(disabled, GenerationModel.FLASH.wireId), null, null,
                Budget.AUTO).roles.get(Role.CHAT);

        assertEquals(Fallback.EARLIEST, chat.assignment.fallback,
                "a disabled provider is not an option, so its configured pair is unavailable");
        assertEquals(enabled, chat.assignment.providerId);
        assertNull(chat.refusal);
    }

    @Test
    void withoutABuiltinTheEarliestAvailableModelIsUsed() {
        String first = provider(true, false);
        model(first, GenerationModel.FLASH.wireId);
        secrets.saveKey(first);
        Choice gone = new Choice("33333333-3333-3333-3333-333333333333", "gone");

        Outcome resolved = planner.plan(gone, null, null, Budget.MID).roles.get(Role.CHAT);
        assertEquals(Fallback.EARLIEST, resolved.assignment.fallback);
        assertEquals(first, resolved.assignment.providerId);
        assertNull(resolved.refusal);
    }

    @Test
    void aPurposeMapsToItsRole() {
        assertEquals(Role.CHAT, GenerationPlanner.roleFor(PromptPurpose.LEARNING_ANSWER));
        assertEquals(Role.TRANSLATION, GenerationPlanner.roleFor(PromptPurpose.MESSAGE_TRANSLATION));
        assertEquals(Role.FAST, GenerationPlanner.roleFor(PromptPurpose.CONTEXT_COMPRESSION));
        assertEquals(Role.FAST, GenerationPlanner.roleFor(PromptPurpose.BRANCH_SUMMARY));
    }

    @Test
    void anUnconfiguredRoleResolvesThroughTheSameFallbackChain() {
        String only = provider(true, false);
        model(only, GenerationModel.FLASH.wireId);
        secrets.saveKey(only);

        Plan plan = planner.plan(null, null, null, Budget.LOW);
        Assignment fast = plan.roles.get(Role.FAST).assignment;
        assertEquals(Fallback.EARLIEST, fast.fallback,
                "with no configured pair and no builtin the earliest available option is used");
        assertEquals(only, fast.providerId);
    }
}
