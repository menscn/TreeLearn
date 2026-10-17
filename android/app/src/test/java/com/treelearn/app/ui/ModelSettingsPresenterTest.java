package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.treelearn.app.ui.ModelSettingsPresenter.Assignment;
import com.treelearn.app.ui.ModelSettingsPresenter.Budget;
import com.treelearn.app.ui.ModelSettingsPresenter.Fallback;
import com.treelearn.app.ui.ModelSettingsPresenter.Notice;
import com.treelearn.app.ui.ModelSettingsPresenter.Option;
import com.treelearn.app.ui.ModelSettingsPresenter.Role;
import com.treelearn.app.ui.ModelSettingsPresenter.State;
import com.treelearn.app.ui.TestDoubles.InlineExecutor;
import com.treelearn.app.ui.TestDoubles.InlineUi;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The model settings presenter's state machine (T-088): the three role assignments, the five
 * thinking-budget tiers, the answer-suggestions switch and the read-time fallback rule — the
 * acceptance walk for the model settings page, on fakes, off the UI thread. The fallback rule
 * itself is a pure function, so both its priority order and its validity check are pinned here.
 */
public final class ModelSettingsPresenterTest {

    private RecordingSink sink;
    private InlineExecutor background;
    private FakeStore store;
    private ModelSettingsPresenter presenter;

    @Before
    public void setUp() {
        sink = new RecordingSink();
        background = new InlineExecutor();
        store = new FakeStore();
        presenter = new ModelSettingsPresenter(sink, new InlineUi(), background, store);
    }

    @Test
    public void startingReadsTheWholeStateOffTheUiThread() {
        store.options.add(option("p1", "DeepSeek", "deepseek-chat"));
        store.chat = assignment("p1", "deepseek-chat", Fallback.NONE);

        presenter.start();

        assertEquals(1, background.runs);
        State state = sink.last();
        assertEquals(1, state.options.size());
        assertSame(store.chat, state.chat);
        assertEquals(Budget.AUTO, state.budget);
        assertTrue(state.suggestions);
        assertEquals(Notice.NONE, state.notice);
    }

    @Test
    public void selectingAModelWritesThroughTheStoreAndReloads() {
        store.options.add(option("p1", "DeepSeek", "deepseek-chat"));

        presenter.onModelSelected(Role.FAST, store.options.get(0));

        assertEquals(1, store.saves);
        assertEquals(Role.FAST, store.savedRole);
        assertEquals("p1", store.savedProviderId);
        assertEquals("deepseek-chat", store.savedModelId);
        assertEquals(1, background.runs);
    }

    @Test
    public void selectingABudgetTierPersistsIt() {
        presenter.onBudgetSelected(Budget.HIGH);

        assertEquals(1, store.budgetSaves);
        assertSame(Budget.HIGH, store.savedBudget);
    }

    @Test
    public void togglingSuggestionsPersistsIt() {
        presenter.onSuggestionsToggled(false);

        assertEquals(1, store.suggestionSaves);
        assertTrue(store.savedSuggestions == false);
    }

    @Test
    public void aFallenBackRoleRaisesTheNoticeOnLoad() {
        store.options.add(option("p1", "DeepSeek", "deepseek-chat"));
        store.chat = assignment("p1", "deepseek-chat", Fallback.BUILTIN);

        presenter.start();

        assertEquals(Notice.FALLEN_BACK, sink.last().notice);
    }

    @Test
    public void aSelectionThatResolvesTheFallbackClearsTheNoticeAgain() {
        store.options.add(option("p1", "DeepSeek", "deepseek-chat"));
        store.chat = assignment("p1", "deepseek-chat", Fallback.BUILTIN);
        presenter.start();

        presenter.onModelSelected(Role.CHAT, store.options.get(0));

        assertEquals(Notice.NONE, sink.last().notice);
    }

    @Test
    public void anEmptyCatalogRaisesTheNoModelsNotice() {
        presenter.start();

        assertEquals(Notice.NO_MODELS, sink.last().notice);
        assertTrue(sink.last().options.isEmpty());
    }

    // ------------------------------------------------------------------
    // The read-time fallback rule (SPEC-2026-09-23 分工回退规则), pure and pinned.
    // ------------------------------------------------------------------

    @Test
    public void theRuleKeepsASelectionThatIsStillUsable() {
        List<Option> options = Arrays.asList(option("p1", "DeepSeek", "deepseek-chat"));

        Assignment resolved = ModelSettingsPresenter.resolve("p1", "deepseek-chat", options,
                option("p0", "DeepSeek", "deepseek-flash"), null);

        assertEquals(Fallback.NONE, resolved.fallback);
        assertEquals("p1", resolved.providerId);
        assertEquals("deepseek-chat", resolved.modelId);
    }

    @Test
    public void theRuleFallsBackToTheBuiltinDefaultFirst() {
        List<Option> options = Arrays.asList(option("p1", "DeepSeek", "deepseek-chat"));

        Assignment resolved = ModelSettingsPresenter.resolve("gone", "gone-model", options,
                option("p0", "DeepSeek", "deepseek-flash"),
                option("p1", "DeepSeek", "deepseek-chat"));

        assertEquals(Fallback.BUILTIN, resolved.fallback);
        assertEquals("p0", resolved.providerId);
        assertEquals("deepseek-flash", resolved.modelId);
    }

    @Test
    public void theRuleFallsBackToTheEarliestEnabledModelNext() {
        Assignment resolved = ModelSettingsPresenter.resolve("gone", "gone-model",
                Arrays.asList(option("p1", "另一家", "other-model")), null,
                option("p1", "另一家", "other-model"));

        assertEquals(Fallback.EARLIEST, resolved.fallback);
        assertEquals("p1", resolved.providerId);
        assertEquals("other-model", resolved.modelId);
    }

    @Test
    public void theRuleGoesEmptyWhenNothingIsUsable() {
        Assignment resolved = ModelSettingsPresenter.resolve("gone", "gone-model", null, null,
                null);

        assertEquals(Fallback.EMPTY, resolved.fallback);
        assertNull(resolved.providerId);
        assertNull(resolved.modelId);
        assertEquals("", resolved.label);
    }

    /**
     * A learner who never picked has no selection to lose, so the builtin default is the designed
     * starting point and must not be reported as a substitution. Before this rule existed, a fresh
     * install told every learner that their model「已不可用」.
     */
    @Test
    public void theRuleDefaultsSilentlyWhenNothingWasEverStored() {
        List<Option> options = Arrays.asList(option("p1", "DeepSeek", "deepseek-chat"));

        Assignment resolved = ModelSettingsPresenter.resolve(null, null, options,
                option("p0", "DeepSeek", "deepseek-flash"), null);

        assertEquals(Fallback.DEFAULTED, resolved.fallback);
        assertFalse("a pick never made is not a pick that was lost",
                resolved.fallback.substituted());
        assertEquals("p0", resolved.providerId);
        assertEquals("deepseek-flash", resolved.modelId);
    }

    @Test
    public void onlyALostSelectionCountsAsSubstituted() {
        assertFalse(Fallback.NONE.substituted());
        assertFalse(Fallback.DEFAULTED.substituted());
        assertFalse(Fallback.EMPTY.substituted());
        assertTrue(Fallback.BUILTIN.substituted());
        assertTrue(Fallback.EARLIEST.substituted());
    }

    private static Option option(String providerId, String providerName, String modelId) {
        return new Option(providerId, providerName, modelId);
    }

    private static Assignment assignment(String providerId, String modelId, Fallback fallback) {
        return new Assignment(providerId, modelId, fallback);
    }

    private static final class RecordingSink implements ModelSettingsPresenter.Sink {

        final List<State> states = new ArrayList<State>();

        State last() {
            return states.get(states.size() - 1);
        }

        @Override
        public void render(State state) {
            states.add(state);
        }
    }

    private static final class FakeStore implements ModelSettingsPresenter.Store {

        final List<Option> options = new ArrayList<Option>();
        // The real store never answers EMPTY while any enabled provider has a model; the fake
        // mirrors that, so the fallback notice tracks only the roles the tests make fall back.
        Assignment chat = new Assignment("p0", "m0", Fallback.NONE);
        Assignment fast = new Assignment("p0", "m0", Fallback.NONE);
        Assignment translation = new Assignment("p0", "m0", Fallback.NONE);
        Budget budget = Budget.AUTO;
        boolean suggestions = true;
        int saves, budgetSaves, suggestionSaves;
        Role savedRole;
        String savedProviderId, savedModelId;
        Budget savedBudget;
        Boolean savedSuggestions;

        @Override
        public List<Option> options() {
            return options;
        }

        @Override
        public Assignment read(Role role) {
            switch (role) {
                case FAST:
                    return fast;
                case TRANSLATION:
                    return translation;
                default:
                    return chat;
            }
        }

        @Override
        public void save(Role role, String providerId, String modelId) {
            saves++;
            savedRole = role;
            savedProviderId = providerId;
            savedModelId = modelId;
            Assignment stored = new Assignment(providerId, modelId, Fallback.NONE);
            if (role == Role.FAST) {
                fast = stored;
            } else if (role == Role.TRANSLATION) {
                translation = stored;
            } else {
                chat = stored;
            }
        }

        @Override
        public Budget readBudget() {
            return budget;
        }

        @Override
        public void saveBudget(Budget value) {
            budgetSaves++;
            savedBudget = value;
        }

        @Override
        public boolean readSuggestions() {
            return suggestions;
        }

        @Override
        public void saveSuggestions(boolean enabled) {
            suggestionSaves++;
            savedSuggestions = enabled;
        }
    }
}
