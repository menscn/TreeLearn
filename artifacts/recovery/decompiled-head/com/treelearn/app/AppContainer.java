/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.os.Handler
 *  android.os.Looper
 *  com.treelearn.ai.gateway.DeepSeekGatewayFactory
 *  com.treelearn.ai.prompt.PromptBudget
 *  com.treelearn.app.R$string
 *  com.treelearn.core.domain.Branch
 *  com.treelearn.data.db.DatabaseTransactionRunner
 *  com.treelearn.data.db.TreeLearnDatabaseHelper
 *  com.treelearn.data.secret.KeystoreSecretStore
 */
package com.treelearn.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.treelearn.ai.gateway.DeepSeekGatewayFactory;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.R;
import com.treelearn.app.artifact.DerivedService;
import com.treelearn.app.generation.AiGatewayStreams;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.library.LibraryData;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.navigation.SqliteNavigationStore;
import com.treelearn.app.reading.ReadingStore;
import com.treelearn.app.reading.SqliteReadingStore;
import com.treelearn.app.search.BranchTitles;
import com.treelearn.app.search.SearchRepository;
import com.treelearn.app.ui.ChatCommands;
import com.treelearn.app.ui.ChatData;
import com.treelearn.app.ui.ChatDraftStore;
import com.treelearn.app.ui.CopyLabels;
import com.treelearn.app.ui.HomeData;
import com.treelearn.app.ui.KeyVault;
import com.treelearn.app.ui.RootQuestionSubmitter;
import com.treelearn.app.ui.SettingsStore;
import com.treelearn.app.ui.TreeCanvasCommands;
import com.treelearn.app.ui.TreeCanvasData;
import com.treelearn.app.ui.UiThread;
import com.treelearn.app.wiring.AndroidSettingsStore;
import com.treelearn.app.wiring.ChatStore;
import com.treelearn.app.wiring.ChatWiring;
import com.treelearn.app.wiring.CoordinatorGenerationPort;
import com.treelearn.app.wiring.HomeWiring;
import com.treelearn.app.wiring.LibraryWiring;
import com.treelearn.app.wiring.LogcatTelemetry;
import com.treelearn.app.wiring.NavigationWiring;
import com.treelearn.app.wiring.PromptLimits;
import com.treelearn.app.wiring.SharedPreferencesChatDraftStore;
import com.treelearn.app.wiring.SqliteChatStore;
import com.treelearn.app.wiring.SqliteHomeStore;
import com.treelearn.app.wiring.SqliteLibraryStore;
import com.treelearn.app.wiring.SqliteSearchStore;
import com.treelearn.app.wiring.SqliteTreeCanvasStore;
import com.treelearn.app.wiring.TreeCanvasWiring;
import com.treelearn.core.domain.Branch;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.secret.KeystoreSecretStore;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class AppContainer {
    private static AppContainer instance;
    private final Executor background = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "treelearn-store");
        thread.setDaemon(true);
        return thread;
    });
    private final UiThread uiThread = new MainThreadUi();
    private final AndroidSettingsStore settingsStore;
    private final HomeWiring homeWiring;
    private final DerivedService derivedService;
    private final ChatWiring chatWiring;
    private final TreeCanvasWiring treeCanvasWiring;
    private final NavigationWiring navigationWiring;
    private final LibraryWiring libraryWiring;
    private final ReadingStore readingStore;
    private final ChatDraftStore chatDraftStore;
    private final SearchRepository searchRepository;
    private final ChatStore chatStore;
    private final Context context;

    private AppContainer(Context applicationContext) {
        this.context = applicationContext;
        this.settingsStore = new AndroidSettingsStore(applicationContext);
        TreeLearnDatabaseHelper helper = new TreeLearnDatabaseHelper(applicationContext);
        DatabaseTransactionRunner transactions = new DatabaseTransactionRunner();
        Clock clock = Clock.systemUTC();
        AiGatewayStreams gateway = new AiGatewayStreams(DeepSeekGatewayFactory.create(), new KeystoreSecretStore(applicationContext));
        GenerationCoordinator coordinator = new GenerationCoordinator(helper, transactions, gateway, clock, new LogcatTelemetry());
        CoordinatorGenerationPort generation = new CoordinatorGenerationPort(coordinator);
        PromptBudget budget = PromptLimits.budget();
        this.derivedService = new DerivedService(helper, transactions, gateway, budget, clock);
        this.homeWiring = new HomeWiring(new SqliteHomeStore(helper, transactions), generation, this.settingsStore, clock, budget, this.derivedService);
        this.chatStore = new SqliteChatStore(helper, transactions);
        this.chatWiring = new ChatWiring(this.chatStore, generation, this.settingsStore, clock, budget, this.derivedService);
        this.treeCanvasWiring = new TreeCanvasWiring(new SqliteTreeCanvasStore(helper, transactions));
        this.navigationWiring = new NavigationWiring(new SqliteNavigationStore(helper, transactions, coordinator));
        this.libraryWiring = new LibraryWiring(new SqliteLibraryStore(helper), this.navigationWiring);
        this.readingStore = new SqliteReadingStore(helper);
        this.searchRepository = new SqliteSearchStore(helper);
        this.chatDraftStore = new SharedPreferencesChatDraftStore(applicationContext);
    }

    public static synchronized AppContainer get(Context context) {
        if (instance == null) {
            instance = new AppContainer(context.getApplicationContext());
        }
        return instance;
    }

    public SettingsStore settingsStore() {
        return this.settingsStore;
    }

    public KeyVault keyVault() {
        return this.settingsStore;
    }

    public HomeData homeData() {
        return this.homeWiring;
    }

    public RootQuestionSubmitter rootQuestionSubmitter() {
        return this.homeWiring;
    }

    public ChatData chatData() {
        return this.chatWiring;
    }

    public ChatCommands chatCommands() {
        return this.chatWiring;
    }

    public TreeCanvasData treeCanvasData() {
        return this.treeCanvasWiring;
    }

    public TreeCanvasCommands treeCanvasCommands() {
        return this.treeCanvasWiring;
    }

    public NavigationData navigationData() {
        return this.navigationWiring;
    }

    public LibraryData libraryData() {
        return this.libraryWiring;
    }

    public ReadingStore readingStore() {
        return this.readingStore;
    }

    public ChatDraftStore chatDraftStore() {
        return this.chatDraftStore;
    }

    public SearchRepository searchRepository() {
        return this.searchRepository;
    }

    public BranchTitles branchTitles() {
        return branchId -> {
            Branch branch = this.chatStore.branch(branchId);
            return branch == null ? null : branch.title();
        };
    }

    public CopyLabels chatCopyLabels() {
        return CopyLabels.of(this.context.getString(R.string.chat_copy_reasoning), this.context.getString(R.string.chat_copy_answer));
    }

    public UiThread uiThread() {
        return this.uiThread;
    }

    public Executor background() {
        return this.background;
    }

    private static final class MainThreadUi
    implements UiThread {
        private final Handler handler = new Handler(Looper.getMainLooper());

        private MainThreadUi() {
        }

        @Override
        public void post(Runnable action) {
            this.handler.post(action);
        }
    }
}

