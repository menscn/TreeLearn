package com.treelearn.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.treelearn.ai.gateway.AiGateway;

import java.io.File;
import com.treelearn.ai.gateway.DeepSeekGatewayFactory;
import com.treelearn.ai.prompt.PromptBudget;
import com.treelearn.app.files.AttachmentStore;
import com.treelearn.app.generation.AiGatewayStreams;
import com.treelearn.app.generation.GenerationCoordinator;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.app.generation.MessageTranslator;
import com.treelearn.app.generation.QuickModelGateway;
import com.treelearn.app.artifact.DerivedService;
import com.treelearn.app.forest.ForestData;
import com.treelearn.app.library.LibraryData;
import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.navigation.SqliteNavigationStore;
import com.treelearn.app.reading.ReadingStore;
import com.treelearn.app.reading.SqliteReadingStore;
import com.treelearn.app.search.BranchTitles;
import com.treelearn.app.search.SearchRepository;
import com.treelearn.app.search.web.ChatSearchStep;
import com.treelearn.app.search.web.HttpWebSearchGateway;
import com.treelearn.app.search.web.KeystoreSearchCredentials;
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
import com.treelearn.app.wiring.ChatWiring;
import com.treelearn.app.wiring.ChatStore;
import com.treelearn.app.wiring.CoordinatorGenerationPort;
import com.treelearn.app.wiring.GenerationPort;
import com.treelearn.app.wiring.ForestWiring;
import com.treelearn.app.wiring.HomeWiring;
import com.treelearn.app.wiring.LibraryWiring;
import com.treelearn.app.wiring.NavigationWiring;
import com.treelearn.app.wiring.PreferenceSearchSettingsStore;
import com.treelearn.app.wiring.PreferenceVoiceSettingsStore;
import com.treelearn.app.wiring.PromptLimits;
import com.treelearn.app.wiring.SharedPreferencesChatDraftStore;
import com.treelearn.app.wiring.SqliteChatStore;
import com.treelearn.app.wiring.HomeStore;
import com.treelearn.app.wiring.SqliteHomeStore;
import com.treelearn.app.wiring.SqliteFileManagerStore;
import com.treelearn.app.wiring.SqliteLibraryStore;
import com.treelearn.app.wiring.SqliteModelSettingsStore;
import com.treelearn.app.wiring.SqliteProviderAdminStore;
import com.treelearn.app.wiring.SqliteQuickModelRoutes;
import com.treelearn.app.wiring.SqliteSearchStore;
import com.treelearn.app.wiring.SqliteTranslationRoutes;
import com.treelearn.app.wiring.SqliteTreeCanvasStore;
import com.treelearn.app.wiring.TelemetrySink;
import com.treelearn.app.wiring.TreeCanvasWiring;
import com.treelearn.core.domain.Branch;
import com.treelearn.data.db.DatabaseTransactionRunner;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.secret.KeystoreSecretStore;
import com.treelearn.data.settings.SettingsRepository;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The app's dependency assembly: one instance, built once, holding the stores and the two threads the
 * presenters need.
 *
 * <p><strong>This is the whole wiring, in one place.</strong> The home, conversation, canvas and
 * navigation paths are built here and nowhere else: the database helper, the transaction runner, the
 * credential store, the gateway and the generation coordinator are created once and handed to the real
 * {@link HomeWiring} / {@link ChatWiring} / {@link TreeCanvasWiring} / {@link NavigationWiring}. No
 * screen constructs any of them, and no screen holds a database handle - an Activity asks this container
 * for a port and passes it on.
 *
 * <p><strong>The credential never lives here.</strong> The gateway resolves the stored key per call
 * through {@link KeystoreSecretStore}, so this object holds no key material, and the only read that
 * touches it is the settings screen's masked one.
 *
 * <p>One background thread on purpose. Every store read and write in this app is a SQLite or
 * Preferences call, and serialising them removes a class of "two screens read the same row at the
 * same time" questions before they can be asked. The UI thread handle is the main {@code Looper}.
 *
 * <p>The container holds the application context, never an Activity, so it cannot keep a destroyed
 * screen alive.
 */
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

    /** The library's topic list: a read-only store, built once like every other store. */
    private final LibraryWiring libraryWiring;

    /** The forest's read: the library's topics plus the one forest projection and the stats entry. */
    private final ForestWiring forestWiring;

    private final ReadingStore readingStore;

    /** T-076: the composer drafts that wait out a process rebuild. */
    private final ChatDraftStore chatDraftStore;

    /** The search port: the statement runner, with its connection opened on first use. */
    private final SearchRepository searchRepository;

    private final SqliteProviderAdminStore providerAdminStore;
    private final SqliteModelSettingsStore modelSettingsStore;
    private final PreferenceSearchSettingsStore searchSettingsStore;
    private final PreferenceVoiceSettingsStore voiceSettingsStore;
    private final AttachmentStore attachmentStore;
    private final SqliteFileManagerStore fileManagerStore;
    private final ChatSearchStep chatSearchStep;
    private final MessageTranslator messageTranslator;

    /** The branch rows the search page names its results from - the conversation store, not a copy. */
    private final ChatStore chatStore;
    /** The library store; the T-106 drawer reuses its topics() read through it. */
    private final SqliteLibraryStore libraryStore;
    /** The home store; T-104's stats detail reads the same statsRaw entry through it. */
    private final HomeStore homeStore;
    /** The one clock: "what time is it" has a single answer for the whole application. */
    private final java.time.Clock clock;

    private final Context context;

    private AppContainer(Context applicationContext) {
        this.context = applicationContext;
        this.settingsStore = new AndroidSettingsStore(applicationContext);

        // One owner each for the connection, the transaction boundary and the clock, so "which
        // database am I on" and "what time is it" have a single answer for the whole application.
        TreeLearnDatabaseHelper helper = new TreeLearnDatabaseHelper(applicationContext);
        DatabaseTransactionRunner transactions = new DatabaseTransactionRunner();
        Clock clock = Clock.systemDefaultZone();

        // The gateway reads the credential per call; the coordinator owns admission and recovery.
        AiGateway adminGateway = DeepSeekGatewayFactory.create();
        KeystoreSecretStore adminSecrets = new KeystoreSecretStore(applicationContext);
        AiGatewayStreams gateway = new AiGatewayStreams(adminGateway, adminSecrets,
                "00000000-0000-0000-0000-000000000001");
        boolean release = (applicationContext.getApplicationInfo().flags & 2) == 0;
        GenerationCoordinator coordinator = new GenerationCoordinator(helper, transactions,
                gateway, clock, TelemetrySink.forVariant(release));
        GenerationPort generation = new CoordinatorGenerationPort(coordinator);
        PromptBudget budget = PromptLimits.budget();

        this.modelSettingsStore = new SqliteModelSettingsStore(helper,
                new SettingsRepository(applicationContext));
        this.attachmentStore = new AttachmentStore(helper,
                new File(applicationContext.getFilesDir(), "attachments"));
        this.fileManagerStore = new SqliteFileManagerStore(this.attachmentStore);
        SqliteQuickModelRoutes quickModelRoutes = new SqliteQuickModelRoutes(helper,
                this.modelSettingsStore);
        QuickModelGateway derivedGateway = new QuickModelGateway(quickModelRoutes, providerId -> {
            if ("00000000-0000-0000-0000-000000000001".equals(providerId)) {
                adminSecrets.migrateLegacySecret(providerId);
            }
            return adminSecrets.read(providerId);
        }, adminGateway::streamChat, gateway);
        this.derivedService = new DerivedService(helper, transactions, derivedGateway, budget, clock);
        this.messageTranslator = new MessageTranslator(new SqliteTranslationRoutes(helper,
                this.modelSettingsStore), providerId -> {
            if ("00000000-0000-0000-0000-000000000001".equals(providerId)) {
                adminSecrets.migrateLegacySecret(providerId);
            }
            return adminSecrets.read(providerId);
        }, adminGateway::streamChat, gateway, budget);
        this.homeStore = new SqliteHomeStore(helper, transactions);
        this.clock = clock;
        this.homeWiring = new HomeWiring(homeStore, generation,
                settingsStore, clock, budget, derivedService);
        this.chatStore = new SqliteChatStore(helper, transactions);
        this.searchSettingsStore = new PreferenceSearchSettingsStore(
                new SettingsRepository(applicationContext), adminSecrets);
        this.voiceSettingsStore = new PreferenceVoiceSettingsStore(
                new SettingsRepository(applicationContext));
        this.chatSearchStep = new ChatSearchStep(this.searchSettingsStore,
                new KeystoreSearchCredentials(adminSecrets), new HttpWebSearchGateway());
        this.chatWiring = new ChatWiring(chatStore, generation, settingsStore, clock, budget,
                derivedService, this.modelSettingsStore::readSuggestions, this.chatSearchStep,
                this.attachmentStore);
        this.treeCanvasWiring = new TreeCanvasWiring(
                new SqliteTreeCanvasStore(helper, transactions));
        this.navigationWiring = new NavigationWiring(
                new SqliteNavigationStore(helper, transactions, coordinator));
        // The completed segment restores and deletes archived branches, and both are the navigation
        // store's own acts: handing the library that port is what keeps one subtree-deletion
        // implementation rather than a second one that could answer with a different set.
        this.libraryStore = new SqliteLibraryStore(helper);
        this.libraryWiring = new LibraryWiring(libraryStore, navigationWiring);
        this.forestWiring = new ForestWiring(libraryWiring, libraryStore, homeStore, clock);
        this.readingStore = new SqliteReadingStore(helper);
        this.searchRepository = new SqliteSearchStore(helper);
        this.chatDraftStore = new SharedPreferencesChatDraftStore(applicationContext);
        this.providerAdminStore = new SqliteProviderAdminStore(helper, transactions, adminSecrets,
                adminGateway, clock);
    }

    public static synchronized AppContainer get(Context context) {
        if (instance == null) {
            instance = new AppContainer(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Drops the process-wide instance so the next {@link #get} opens the database file that exists
     * now. Instrumented fixtures that unlink the database leave this container's helper on a deleted
     * inode; the suites that drive the real shell call this so they read the file they seeded.
     */
    static synchronized void resetForTests() {
        instance = null;
    }

    /** Backed by the real stores; also answers {@link KeyVault}. */
    public SettingsStore settingsStore() {
        return settingsStore;
    }

    public KeyVault keyVault() {
        return settingsStore;
    }

    /** The drawer's quick-list source: the library store itself (T-106, one query, one text). */
    public SqliteLibraryStore libraryStore() {
        return libraryStore;
    }

    /** The stats detail page's raw source: the same store the home strip reads (T-104). */
    public HomeStore homeStore() {
        return homeStore;
    }

    /** The application clock: one owner, one answer (T-104's stats read rides it). */
    public java.time.Clock clock() {
        return clock;
    }

    /** The stored topics, read from the local database. */
    public HomeData homeData() {
        return homeWiring;
    }

    /** The first topic, written through {@code FirstTopicCreation} and sent through the coordinator. */
    public RootQuestionSubmitter rootQuestionSubmitter() {
        return homeWiring;
    }

    /** One branch's stored conversation, read from the local database. */
    public ChatData chatData() {
        return chatWiring;
    }

    /** The conversation's commands, run against the store and the coordinator. */
    public ChatCommands chatCommands() {
        return chatWiring;
    }

    /** The canvas's rows: one topic read as its live branches, their summary line and their rounds. */
    public TreeCanvasData treeCanvasData() {
        return treeCanvasWiring;
    }

    /** The canvas's one write: folding or unfolding a branch. */
    public TreeCanvasCommands treeCanvasCommands() {
        return treeCanvasWiring;
    }

    /**
     * The navigation page's tree: one topic's branches, its summary, and the archive and delete writes.
     *
     * <p>One accessor, not two. The canvas splits its port because folding has a commands contract of
     * its own; navigation's writes ({@code setArchived}, {@code delete}) already sit on
     * {@link NavigationData} next to the reads, and the screen's gestures are the presenter's contract,
     * which the host builds per visit with the sink only it can supply.
     */
    public NavigationData navigationData() {
        return navigationWiring;
    }

    /**
     * The library's two lists: the topics still being worked on, and the archived branches.
     *
     * <p>The completed segment's two writes are not the library's own. Restore is the navigation
     * port's {@code setArchived} and delete takes a {@link NavigationData.Plan} counted by
     * {@link NavigationData#plan} - the same implementation the navigation page uses - so the library
     * cannot offer a deletion that removes a different set for the same branch.
     */
    public LibraryData libraryData() {
        return libraryWiring;
    }

    /** The forest page's one read: the same topic collection, the forest projection and the streak. */
    public ForestData forestData() {
        return forestWiring;
    }

    /**
     * One branch's reading position, read and written through the columns the branch row already has.
     *
     * <p>One instance for the whole application, like the other stores: it holds no state of its own
     * beyond the connection, and a second one would be a second answer to "who writes the reading
     * position of this branch".
     */
    public ReadingStore readingStore() {
        return readingStore;
    }

    /** The composer drafts that wait out a process rebuild, one record per branch. */
    public ChatDraftStore chatDraftStore() {
        return chatDraftStore;
    }

    /**
     * One query's hits, read from the local database.
     *
     * <p>The connection is opened on the first question asked rather than here, so assembling this
     * container never touches the database - see {@link SqliteSearchStore}, whose whole reason for
     * existing is that timing.
     */
    public SearchRepository searchRepository() {
        return searchRepository;
    }

    /** The provider admin room's store: providers, their endpoints and their keys. */
    public SqliteProviderAdminStore providerAdminStore() {
        return providerAdminStore;
    }

    /** The per-role model choices the quick-model routes resolve against. */
    public SqliteModelSettingsStore modelSettingsStore() {
        return modelSettingsStore;
    }

    /** The web-search switch and engine choice the conversation's search step reads. */
    public PreferenceSearchSettingsStore searchSettingsStore() {
        return searchSettingsStore;
    }

    /** The voice settings the read-aloud feature reads. */
    public PreferenceVoiceSettingsStore voiceSettingsStore() {
        return voiceSettingsStore;
    }

    /** The attachment store: the one writer of the attachments directory (T-096). */
    public AttachmentStore attachmentStore() {
        return attachmentStore;
    }

    /** The file manager's read/delete port over the attachment store (T-096). */
    public SqliteFileManagerStore fileManagerStore() {
        return fileManagerStore;
    }

    /** The conversation's one web-search step, shared by every chat like the other ports. */
    public ChatSearchStep chatSearchStep() {
        return chatSearchStep;
    }

    /** The translation port: one translator, routed by the stored per-role models. */
    public MessageTranslator messageTranslator() {
        return messageTranslator;
    }

    /**
     * The one owner of "what is this branch called": the conversation store's own branch rows.
     *
     * <p>Asked instead of joining a title into the search statement, because a second copy of the
     * branch-title read is a second answer to the same question - and a search row and the
     * conversation it opens must never name two different branches. Since 054 it also answers where
     * a branch sits (the ancestor path, walked from the same rows) and whether it is archived, so a
     * result row shown in the library tells the truth about the branch it opens.
     */
    public BranchTitles branchTitles() {
        return new BranchTitles() {
            @Override
            public String titleOf(String branchId) {
                Branch branch = chatStore.branch(branchId);
                return branch == null ? null : branch.title();
            }

            @Override
            public BranchTitles.Badge badgeOf(String branchId) {
                Branch branch = chatStore.branch(branchId);
                if (branch == null) {
                    return null;
                }
                // Ancestors only, nearest first: the row already carries the branch's own title.
                java.util.Deque<String> names = new java.util.ArrayDeque<>();
                String cursor = branch.parentBranchId;
                while (cursor != null) {
                    Branch parent = chatStore.branch(cursor);
                    if (parent == null) {
                        break;
                    }
                    names.addFirst(parent.title());
                    cursor = parent.parentBranchId;
                }
                return new BranchTitles.Badge(String.join(" \u203a ", names), branch.isArchived());
            }
        };
    }

    /** The two clipboard headings, read from {@code res/values/strings.xml} like every other string. */
    public CopyLabels chatCopyLabels() {
        return CopyLabels.of(context.getString(R.string.chat_copy_reasoning),
                context.getString(R.string.chat_copy_answer));
    }

    public UiThread uiThread() {
        return uiThread;
    }

    public Executor background() {
        return background;
    }

    private static final class MainThreadUi implements UiThread {

        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void post(Runnable action) {
            handler.post(action);
        }
    }
}
