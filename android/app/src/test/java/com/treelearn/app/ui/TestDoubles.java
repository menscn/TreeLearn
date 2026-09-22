package com.treelearn.app.ui;

import com.treelearn.core.domain.GenerationModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Doubles for the JVM tests: they run every posted action inline, so a test reads the states a screen
 * would have been asked to draw without a Looper or a device.
 */
final class TestDoubles {

    /** Canonical lowercase UUIDs: Guard.id refuses any other spelling of an identifier. */
    static final String TOPIC_1 = "11111111-1111-4111-8111-111111111111";
    static final String TOPIC_2 = "22222222-2222-4222-8222-222222222222";
    static final String BRANCH_RESUME = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    static final String BRANCH_ROOT = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    private TestDoubles() {
    }

    /** Runs the action on the calling thread and remembers that it was asked. */
    static final class InlineUi implements UiThread {

        final List<Runnable> posted = new ArrayList<Runnable>();

        @Override
        public void post(Runnable action) {
            posted.add(action);
            action.run();
        }
    }

    /** The store work of this task is synchronous in the tests; a real one uses the I/O thread. */
    static final class InlineExecutor implements Executor {

        int runs;

        @Override
        public void execute(Runnable command) {
            runs++;
            command.run();
        }
    }

    /** Records every state, so a test can assert on the last one and on the sequence. */
    static final class RecordingHomeSink implements HomeSink {

        final List<HomeViewState> states = new ArrayList<HomeViewState>();
        int settingsOpened;
        int archivedRequested;
        int searches;
        final List<String> branchesOpened = new ArrayList<String>();
        final List<String> archivedOpened = new ArrayList<String>();

        @Override
        public void render(HomeViewState state) {
            states.add(state);
        }

        @Override
        public void openSettings() {
            settingsOpened++;
        }

        @Override
        public void openBranch(String branchId) {
            branchesOpened.add(branchId);
        }

        @Override
        public void openArchived(String branchId) {
            archivedOpened.add(branchId);
        }

        @Override
        public void openSearch() {
            searches++;
        }

        HomeViewState last() {
            return states.get(states.size() - 1);
        }
    }

    static final class RecordingSettingsSink implements SettingsSink {

        final List<SettingsViewState> states = new ArrayList<SettingsViewState>();

        @Override
        public void render(SettingsViewState state) {
            states.add(state);
        }

        SettingsViewState last() {
            return states.get(states.size() - 1);
        }
    }

    static final class FakeKeyVault implements KeyVault {

        boolean configured;

        FakeKeyVault(boolean configured) {
            this.configured = configured;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }
    }

    /** Topics and the strip, with a switch to make the strip fail like a store-side error would. */
    static final class FakeHomeData implements HomeData {

        List<TopicCard> topics = Collections.emptyList();
        boolean topicsThrow;
        List<Recommendation> recommendations = StarterRecommendations.all();
        boolean recommendationsThrow;

        @Override
        public List<TopicCard> recentTopics() {
            if (topicsThrow) {
                throw new IllegalStateException("the topic list could not be read");
            }
            return topics;
        }

        @Override
        public Recommendations recommendations(boolean hasTopics) {
            if (recommendationsThrow) {
                throw new IllegalStateException("the strip could not be produced");
            }
            return Recommendations.starterFallback(recommendations);
        }
    }

    static final class FakeSubmitter implements RootQuestionSubmitter {

        SubmitResult result = SubmitResult.refused(HomeViewState.Notice.STORAGE);
        boolean throwsInstead;
        int calls;
        String lastQuestion;
        GenerationModel lastModel;
        boolean lastThinking;

        @Override
        public SubmitResult submit(String question, GenerationModel model, boolean thinkingEnabled) {
            calls++;
            lastQuestion = question;
            lastModel = model;
            lastThinking = thinkingEnabled;
            if (throwsInstead) {
                throw new IllegalStateException("the store refused the write");
            }
            return result;
        }
    }

    /** In-memory settings: remembers what was written, and can refuse a save on demand. */
    static final class FakeSettingsStore implements SettingsStore {

        GenerationModel model = GenerationModel.FLASH;
        boolean thinking;
        KeySnapshot key = KeySnapshot.missing();
        boolean refuseSave;
        char[] lastSaved;
        int deleted;

        @Override
        public GenerationModel model() {
            return model;
        }

        @Override
        public void setModel(GenerationModel value) {
            this.model = value;
        }

        @Override
        public boolean thinkingEnabled() {
            return thinking;
        }

        @Override
        public void setThinkingEnabled(boolean enabled) {
            this.thinking = enabled;
        }

        @Override
        public KeySnapshot readKey() {
            return key;
        }

        @Override
        public void saveKey(char[] secret) throws SecretStoreRefused {
            if (refuseSave) {
                throw new SecretStoreRefused("refused by the test");
            }
            lastSaved = Arrays.copyOf(secret, secret.length);
            key = KeySnapshot.ofStoredKey(secret);
        }

        @Override
        public void deleteKey() {
            deleted++;
            key = KeySnapshot.missing();
        }
    }

    static TopicCard topic(String id, String title) {
        return new TopicCard(id, BRANCH_RESUME, title, "", 1, 1_700_000_000_000L);
    }
}
