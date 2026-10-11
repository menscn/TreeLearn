package com.treelearn.web.generation;

import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.PromptPurpose;
import com.treelearn.web.credential.SecretPort;
import com.treelearn.web.storage.ModelStore;
import com.treelearn.web.storage.ProviderStore;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Desktop model/provider planning: which provider and model serve a role, whether that target can be
 * called, and the stable refusal when it cannot (task T-2026-10-10-023, contract §5.12 / §3.3).
 *
 * <p>It mirrors Android {@code com.treelearn.app.ui.ModelSettingsPresenter.resolve(providerId, modelId,
 * options, builtinDefault, earliest)}: the configured pair when it is still available
 * ({@link Fallback#NONE}), else the builtin default ({@link Fallback#BUILTIN}), else the earliest
 * available pair ({@link Fallback#EARLIEST}), else nothing ({@link Fallback#EMPTY}). Provider
 * enable/disable is honoured by building {@code options} from enabled providers only — a disabled
 * provider contributes no model and therefore falls back exactly like a removed one.
 *
 * <p><strong>Refusal tokens are the contract's, never new ones.</strong> A role that resolved to
 * {@link Fallback#EMPTY} has no usable model and is refused with {@link Refusal#NO_MODELS}
 * (contract §3.3, Android {@code HomeViewState.Notice.NO_MODELS}); a role whose resolved provider has
 * no stored key is refused with {@link Refusal#KEY_MISSING} (Android {@code AiGatewayError.keyMissing},
 * "no usable Key, the request must not be sent"). {@code PROVIDER_UNAVAILABLE} is deliberately not
 * produced here: the contract scopes it to a call-time provider 5xx / in-band error (a retryable 503),
 * not to a configuration gap, so configuration gaps use {@code NO_MODELS} rather than misusing it.
 */
public final class GenerationPlanner {

    /** Contract §5.12: the fixed role keys. */
    public enum Role {
        CHAT,
        FAST,
        TRANSLATION
    }

    /** Contract §5.12: the fallback token of one resolved role. */
    public enum Fallback {
        NONE,
        BUILTIN,
        EARLIEST,
        EMPTY
    }

    /** Contract §5.12: the reasoning-budget / suggestion settings carried alongside the resolution. */
    public enum Budget {
        OFF,
        AUTO,
        LOW,
        MID,
        HIGH
    }

    /** Contract §3.3: the only two resolution-stage codes; both are non-retryable 400s. */
    public enum Refusal {
        KEY_MISSING,
        NO_MODELS
    }

    /** One stored role setting: the provider/model pair a role was configured with; both may be null. */
    public static final class Choice {

        public final String providerId;
        public final String modelId;

        public Choice(String providerId, String modelId) {
            this.providerId = providerId;
            this.modelId = modelId;
        }
    }

    /** One available pair: an enabled provider's stored model, carrying its builtin flag. */
    static final class Option {

        final String providerId;
        final String modelId;
        final boolean builtin;

        Option(String providerId, String modelId, boolean builtin) {
            this.providerId = providerId;
            this.modelId = modelId;
            this.builtin = builtin;
        }
    }

    /** One role's resolved target. {@link Fallback#EMPTY} carries a null provider and model. */
    public static final class Assignment {

        public static final Assignment EMPTY = new Assignment(null, null, Fallback.EMPTY);

        public final String providerId;
        public final String modelId;
        public final Fallback fallback;

        public Assignment(String providerId, String modelId, Fallback fallback) {
            this.providerId = providerId;
            this.modelId = modelId;
            this.fallback = Guard.object(fallback, "fallback");
        }
    }

    /**
     * One role's outcome: the resolved {@link Assignment} and, when the target cannot be called, the
     * refusal. {@code refusal} is null when the target is usable.
     */
    public static final class Outcome {

        public final Assignment assignment;
        public final Refusal refusal;

        Outcome(Assignment assignment, Refusal refusal) {
            this.assignment = assignment;
            this.refusal = refusal;
        }
    }

    /** The full planning result: one outcome per role plus the carried budget setting. */
    public static final class Plan {

        public final Map<Role, Outcome> roles;
        public final Budget budget;

        Plan(Map<Role, Outcome> roles, Budget budget) {
            this.roles = roles;
            this.budget = budget;
        }
    }

    private final ProviderStore providers;
    private final ModelStore models;
    private final SecretPort secrets;

    public GenerationPlanner(ProviderStore providers, ModelStore models, SecretPort secrets) {
        this.providers = Guard.object(providers, "providers");
        this.models = Guard.object(models, "models");
        this.secrets = Guard.object(secrets, "secrets");
    }

    /** The role a usage runs under; a translation is its own role, the tutor is {@code CHAT}. */
    public static Role roleFor(PromptPurpose purpose) {
        Guard.object(purpose, "purpose");
        if (purpose == PromptPurpose.MESSAGE_TRANSLATION) {
            return Role.TRANSLATION;
        }
        if (purpose == PromptPurpose.LEARNING_ANSWER) {
            return Role.CHAT;
        }
        return Role.FAST;
    }

    /** Resolves all three roles against the stored configuration. */
    public Plan plan(Choice chat, Choice fast, Choice translation, Budget budget) {
        Guard.object(budget, "budget");
        Map<Role, Outcome> roles = new EnumMap<>(Role.class);
        resolveAll(chat, fast, translation).forEach((role, assignment) ->
                roles.put(role, outcome(assignment)));
        return new Plan(roles, budget);
    }

    /**
     * The assignment-only resolution for the three roles — exactly the chain {@link #plan} uses, but
     * without the call-site refusal, so a settings read (contract §5.12 {@code ModelResolution}) never
     * touches the credential port. Each role is resolved independently through the same
     * {@link #resolve} helper; no second fallback rule exists here.
     */
    public Map<Role, Assignment> resolveAll(Choice chat, Choice fast, Choice translation) {
        List<Option> options = options();
        Option builtin = firstBuiltin(options);
        Option earliest = options.isEmpty() ? null : options.get(0);
        Map<Role, Assignment> roles = new EnumMap<>(Role.class);
        roles.put(Role.CHAT, resolve(chat, options, builtin, earliest));
        roles.put(Role.FAST, resolve(fast, options, builtin, earliest));
        roles.put(Role.TRANSLATION, resolve(translation, options, builtin, earliest));
        return roles;
    }

    /** The Android fallback chain, verbatim. */
    static Assignment resolve(Choice choice, List<Option> options, Option builtin, Option earliest) {
        Option kept = find(options, choice);
        if (kept != null) {
            return new Assignment(kept.providerId, kept.modelId, Fallback.NONE);
        }
        if (builtin != null) {
            return new Assignment(builtin.providerId, builtin.modelId, Fallback.BUILTIN);
        }
        if (earliest != null) {
            return new Assignment(earliest.providerId, earliest.modelId, Fallback.EARLIEST);
        }
        return Assignment.EMPTY;
    }

    private Outcome outcome(Assignment assignment) {
        if (assignment.fallback == Fallback.EMPTY) {
            return new Outcome(assignment, Refusal.NO_MODELS);
        }
        boolean keyed = secrets.has(SecretPort.providerTarget(assignment.providerId));
        return new Outcome(assignment, keyed ? null : Refusal.KEY_MISSING);
    }

    private static Option find(List<Option> options, Choice choice) {
        if (choice == null || choice.providerId == null || choice.modelId == null) {
            return null;
        }
        for (Option option : options) {
            if (option.providerId.equals(choice.providerId) && option.modelId.equals(choice.modelId)) {
                return option;
            }
        }
        return null;
    }

    /** Every enabled provider's stored models, in the stores' stable order (creation order, id tiebreak). */
    private List<Option> options() {
        List<Option> options = new ArrayList<>();
        for (ProviderStore.Provider provider : providers.list()) {
            if (!provider.enabled) {
                continue;
            }
            for (ModelStore.Model model : models.listForProvider(provider.id)) {
                options.add(new Option(provider.id, model.modelId, provider.builtin));
            }
        }
        return options;
    }

    private static Option firstBuiltin(List<Option> options) {
        for (Option option : options) {
            if (option.builtin) {
                return option;
            }
        }
        return null;
    }
}
