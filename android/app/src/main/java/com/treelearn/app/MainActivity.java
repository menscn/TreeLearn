package com.treelearn.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import com.treelearn.app.library.LibraryPresenter;
import com.treelearn.app.library.LibrarySink;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.screen.ChatScreen;
import com.treelearn.app.screen.FileManagerScreen;
import com.treelearn.app.screen.HomeDrawerScreen;
import com.treelearn.app.screen.HomeScreen;
import com.treelearn.app.screen.LibraryScreen;
import com.treelearn.app.screen.ModelSettingsScreen;
import com.treelearn.app.screen.NavigationScreen;
import com.treelearn.app.screen.ProviderAdminScreen;
import com.treelearn.app.screen.SearchServiceSettingsScreen;
import com.treelearn.app.screen.StatsDetailScreen;
import com.treelearn.app.screen.SettingsKeyScreen;
import com.treelearn.app.screen.SettingsScreen;
import com.treelearn.app.screen.TreeCanvasScreen;
import com.treelearn.app.screen.VoiceSettingsScreen;
import com.treelearn.app.navigation.NavigationPresenter;
import com.treelearn.app.navigation.NavigationSink;
import com.treelearn.app.navigation.NavigationViewState;
import com.treelearn.app.reading.ReadingStore;
import com.treelearn.app.search.BranchTitles;
import com.treelearn.app.search.SearchPage;
import com.treelearn.app.search.SearchPresenter;
import com.treelearn.app.search.SearchViewState;
import com.treelearn.app.ui.SpeechInputPolicy;
import com.treelearn.app.ui.ChatPresenter;
import com.treelearn.app.ui.ChatSpeaker;
import com.treelearn.app.ui.ChatSink;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.FileManagerPresenter;
import com.treelearn.app.ui.HomeDrawerPresenter;
import com.treelearn.app.ui.HomePresenter;
import com.treelearn.app.ui.HomeSink;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.ModelSettingsPresenter;
import com.treelearn.app.ui.StatsDetailPresenter;
import com.treelearn.app.ui.ProviderAdminPresenter;
import com.treelearn.app.ui.SearchSettingsPresenter;
import com.treelearn.app.ui.SettingsPresenter;
import com.treelearn.app.ui.SettingsSink;
import com.treelearn.app.ui.SettingsViewState;
import com.treelearn.app.ui.TreeCanvasPresenter;
import com.treelearn.app.ui.TreeCanvasSink;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.app.ui.VoiceSettingsPresenter;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.Annotation;
import com.treelearn.app.voice.SystemTtsEngine;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;
import com.treelearn.treelayout.ViewportConstraints;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The app shell: one Activity, one content host, and the persistent bottom tab bar that carries the
 * first-level destinations.
 *
 * <p><strong>The first level is a bar, not a page.</strong> PRD 6.2.16 as of V3.2 makes 主页 / 学习库 /
 * 设置 three peers reachable from a bar the shell owns, so this Activity keeps two things apart: the
 * content host, whose single child is swapped as before, and the bar, which is included once in
 * {@code activity_main.xml} and never drawn by a screen. {@link #swap(View)} is the only place that
 * decides whether the bar is on screen, and {@link #showTab} is the only place that decides which tab
 * a first-level destination belongs to - so a second-level screen can never leave the bar behind, and
 * a return can never land on the wrong tab.
 *
 * <p>The conversation arrived in T-013 the same way, the tree canvas in T-038, the archive list in
 * T-039 and search in T-040: further children of the same host, not second Activities. The library is
 * the fifth, and the only one that is a first-level destination rather than a screen opened from one.
 *
 * <p>The shell is the only place that knows about every screen; it holds no view state or draft, only
 * the screen on top, the selected first-level tab, and the entry route for an open conversation. A
 * conversation, a canvas, the navigation page and the library are created per visit and dropped when
 * they are left, so nothing keeps a read screen or its presenter alive.
 */
public final class MainActivity extends Activity
        implements HomeSink, SettingsSink, ChatSink, TreeCanvasSink, NavigationSink,
        SearchPresenter.Sink, LibrarySink {

    /** The first-level destinations the bottom bar carries. */
    private enum Tab {
        HOME,
        LIBRARY
    }

    private FrameLayout host;
    private View tabBar;
    private View tabHome;
    private View tabLibrary;
    /**
     * The tab the first level is standing on. Only a tab tap or a first-level screen changes it: a
     * second-level screen leaves it alone, which is what makes a return land where it came from.
     */
    private Tab currentTab = Tab.HOME;

    private HomeScreen homeScreen;
    private SettingsScreen settingsScreen;
    private HomePresenter homePresenter;
    private SettingsPresenter settingsPresenter;

    private AppContainer container;
    private ChatScreen chatScreen;
    private ChatPresenter chatPresenter;
    /** The branch the conversation shows; null while the conversation is not on screen. */
    private String chatBranchId;
    private ChatEntryRoute chatEntryRoute;

    private TreeCanvasScreen treeCanvasScreen;
    private TreeCanvasPresenter treeCanvasPresenter;
    /** The branch the canvas was opened from; null while the canvas is not on screen. */
    private String treeBranchId;

    private NavigationScreen navigationScreen;
    private NavigationPresenter navigationPresenter;
    /** The branch the navigation page was opened on; null while the page is not on screen. */
    private String navigationBranchId;
    /** The branch one pending entry is resolving a topic for; null when no entry is in flight. */
    private String navigatingTo;

    /**
     * The search stack behind the library's search box (054). The independent search screen is
     * retired; the presenter and the repository it reads are exactly the ones T-040 shipped.
     */
    private SearchPresenter searchPresenter;

    /** The library page and its decisions; null while the page is not on screen. */
    private LibraryScreen libraryScreen;
    private LibraryPresenter libraryPresenter;

    /** The sub-settings rooms and their decisions; null while the room is not on screen. The key
     *  room shares the settings presenter, so it holds no presenter field of its own. */
    private SettingsKeyScreen keyAdminScreen;
    private ProviderAdminScreen providerAdminScreen;
    private ProviderAdminPresenter providerAdminPresenter;
    private ModelSettingsScreen modelSettingsScreen;
    private ModelSettingsPresenter modelSettingsPresenter;
    private SearchServiceSettingsScreen searchSettingsScreen;
    private SearchSettingsPresenter searchSettingsPresenter;
    private VoiceSettingsScreen voiceSettingsScreen;
    private VoiceSettingsPresenter voiceSettingsPresenter;
    private FileManagerScreen fileManagerScreen;
    private FileManagerPresenter fileManagerPresenter;
    private StatsDetailScreen statsDetailScreen;
    private StatsDetailPresenter statsDetailPresenter;
    private HomeDrawerScreen homeDrawerScreen;
    private HomeDrawerPresenter homeDrawerPresenter;

    private boolean showingSettings;
    private boolean homeStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        host = findViewById(R.id.shell_host);
        tabBar = findViewById(R.id.tab_bar);
        tabHome = findViewById(R.id.tab_home);
        tabLibrary = findViewById(R.id.tab_library);
        registerSystemBack();

        container = AppContainer.get(this);
        homeScreen = new HomeScreen(this);
        settingsScreen = new SettingsScreen(this);
        homePresenter = new HomePresenter(this, container.uiThread(), container.background(),
                container.keyVault(), container.homeData(), container.rootQuestionSubmitter(),
                container.settingsStore());
        settingsPresenter = new SettingsPresenter(this, container.uiThread(),
                container.background(), container.settingsStore());

        homeScreen.setActions(homePresenter);
        homeScreen.setOnSettingsPressed(this::showSettings);
        // The settings root page is a pure navigation list; the settings presenter's actions are
        // consumed by the key room (showKeyAdmin), which shares the presenter and its store reads.
        settingsScreen.setOnBackPressed(this::onBackRequested);

        // The home drawer (T-106): an overlay on main_drawer_root, outside swap()'s reach. Its
        // navigation hands off to the very same room methods the home screen itself uses, and its
        // quick list reads the library store's own query and the drawer owns the compact stats view.
        homeDrawerScreen = new HomeDrawerScreen(this);
        homeDrawerPresenter = new HomeDrawerPresenter(new HomeDrawerPresenter.Sink() {
            @Override
            public void render(HomeDrawerPresenter.State next) {
                homeDrawerScreen.render(next);
            }

            @Override
            public void openBranch(String branchId) {
                showChat(branchId);
            }

            @Override
            public void openStats() {
                showStatsDetail();
            }
        }, container.libraryData()::topics, container.homeData()::stats, container.uiThread(),
                container.background());
        homeDrawerScreen.setActions(homeDrawerPresenter);
        ((FrameLayout) findViewById(R.id.main_drawer_root)).addView(homeDrawerScreen,
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        homeScreen.setOnDrawerPressed(homeDrawerPresenter::onDrawerToggled);
        // The five sub-settings entries. This wiring was missing for every predecessor room
        // (087/088/091/093): the screens existed, their setters bound clicks, but no caller ever
        // invoked the setters, so the entries were dead in the production shell. Same one-line
        // pattern for all five; the file manager (T-096) needs its own entry to be alive, and the
        // four predecessors are repaired by the same stroke rather than left dead.
        settingsScreen.setOnProvidersPressed(this::showProviders);
        settingsScreen.setOnKeyPressed(this::showKeyAdmin);
        settingsScreen.setOnModelsPressed(this::showModels);
        settingsScreen.setOnSearchPressed(this::showSearchSettings);
        settingsScreen.setOnVoicePressed(this::showVoiceSettings);
        settingsScreen.setOnFilesPressed(this::showFileManager);

        tabHome.setOnClickListener(view -> showHome());
        tabLibrary.setOnClickListener(view -> showLibrary());

        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (fileManagerScreen != null) {
            fileManagerPresenter.onResumed();
            return;
        }
        if (showingSettings) {
            return;
        }
        if (navigationBranchId != null) {
            navigationPresenter.onResumed();
            return;
        }
        if (treeBranchId != null) {
            treeCanvasPresenter.onResumed();
            return;
        }
        if (chatBranchId != null) {
            chatPresenter.onResumed();
            return;
        }
        if (libraryScreen != null) {
            libraryPresenter.onResumed();
            if (searchPresenter != null && !searchPresenter.state().draft.isEmpty()) {
                searchPresenter.onResumed();
            }
            return;
        }
        if (homeStarted) {
            homePresenter.onResumed();
        } else {
            homeStarted = true;
            homePresenter.start();
        }
    }

    /**
     * Makes back leave the top-most screen, on every supported platform version, without the AndroidX
     * dispatcher this module cannot depend on (see the dependency policy in android/README.md: app main
     * code resolves against zero AndroidX coordinates, evidenced in artifacts/evidence/T-012-lint.txt).
     *
     * <p>On API 33+ a back gesture is delivered to the registered {@code OnBackInvokedCallback} instead
     * of to {@code onBackPressed}, and on API 36 it is not delivered as {@code KEYCODE_BACK} either -
     * which is exactly why the deprecated override this class used to carry was dead code on the
     * acceptance device. {@link #dispatchKeyEvent} keeps the same behaviour on API 28-32, where a back
     * key event still is how the platform reports back.
     */
    private void registerSystemBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::onBackRequested);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            onBackRequested();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * The one back decision: settings step back to whatever opened them, the navigation page steps back
     * to the conversation it was opened from, the canvas steps back to the conversation it was opened
     * from - the same move the prototype's canvas appbar makes. The library steps back within itself
     * while a query is live and to the home tab otherwise, a conversation steps back to the tab it was
     * opened from, the search page steps back to the tab that opened it, and the home page leaves the
     * app.
     *
     * <p>Both platforms land here, so there is a single place that decides what back means and no
     * second copy of the rule to drift. A second-level screen returns through {@link #showTab} rather
     * than to the home page: the tab the learner left from is the tab they come back to.
     *
     * <p>The library's own arrow reports the same request as the gesture, so the two cannot disagree:
     * a live query is consumed first (the library's own lists come back), and only a library already
     * showing its lists steps out to the home tab.
     */
    private void onBackRequested() {
        if (homeDrawerScreen != null
                && homeDrawerScreen.getVisibility() == View.VISIBLE) {
            homeDrawerPresenter.onDrawerToggled();
            return;
        }
        if (statsDetailScreen != null) {
            showHome();
            return;
        }
        if (keyAdminScreen != null || providerAdminScreen != null || modelSettingsScreen != null
                || searchSettingsScreen != null || voiceSettingsScreen != null
                || fileManagerScreen != null) {
            showSettings();
            return;
        }
        if (showingSettings) {
            if (SettingsBackRoute.afterBack(chatBranchId) == SettingsBackRoute.Target.HOME) {
                showHome();
            } else {
                showChat(chatBranchId);
            }
            return;
        }
        if (navigationBranchId != null) {
            showChat(navigationBranchId);
            return;
        }
        if (treeBranchId != null) {
            showChat(treeBranchId);
            return;
        }
        if (chatBranchId != null) {
            if (chatScreen != null && chatScreen.onSystemBack()) {
                return;
            }
            chatEntryRoute = null;
            showTab(currentTab);
            return;
        }
        if (libraryScreen != null) {
            // A live query is consumed first: the screen clears the box it holds and says whether
            // there was one. The clearing arrives back through the box's own watcher as an empty
            // query, and the presenter then restores the library's own lists - which is also what
            // takes the search scope line away. A second press, with nothing to clear, steps out.
            if (libraryScreen.leaveSearch()) {
                if (libraryPresenter != null) {
                    libraryPresenter.onSearchLeft();
                }
                return;
            }
            showHome();
            return;
        }
        finish();
    }

    @Override
    public void render(HomeViewState state) {
        homeScreen.render(state);
    }

    @Override
    public void openSettings() {
        showSettings();
    }

    /** All chat entry points use the opened branch's parent hierarchy for top-bar Back. */
    @Override
    public void openBranch(String branchId) {
        showChat(branchId);
    }

    @Override
    public void openBranch(String branchId, String initialQuestion) {
        showChat(branchId, initialQuestion);
    }

    @Override
    public void dismissKeyboard() {
        if (chatScreen == null || chatScreen.getWindowToken() == null) {
            return;
        }
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(chatScreen.getWindowToken(), 0);
        }
    }

    @Override
    public void render(SettingsViewState state) {
        if (keyAdminScreen != null) {
            keyAdminScreen.render(state);
        } else {
            settingsScreen.render(state);
        }
    }

    /**
     * The conversation's state. Guarded for the same reason the navigation page's is, and it was this
     * slice's own acceptance walk that found the crash: a conversation's read - and, while an answer is
     * being written, its poll - can still be in flight when a second-level screen takes over, and
     * {@link #showChat(String)} drops the screen the moment it does. A state that lands after that is
     * dropped rather than drawn into a screen that is gone.
     *
     * <p>T-064 adds the branch half of the guard: a fork opens a second conversation while the first
     * presenter's finishing read is still in flight, and a state of the old branch drawn into the new
     * screen is exactly the cross-branch flash a learner sees as the answer changing under them. A
     * state is drawn only when it belongs to the branch this screen was opened for.
     */
    @Override
    public void render(ChatViewState state) {
        if (chatScreen != null && isTheBranchOnScreen(state.branchId)) {
            chatScreen.render(state);
        }
    }

    @Override
    public void renderAnnotations(Map<String, List<Annotation>> annotations) {
        if (chatScreen != null) {
            chatScreen.renderAnnotations(annotations);
        }
    }

    /** Where the conversation stands: the presenter resolved it, the screen only scrolls there. */
    @Override
    public void renderPosition(String branchId, ReadingPosition position) {
        // The same branch guard the state above carries: a position resolved for a branch the
        // learner has already left must not scroll the conversation that replaced it. The branch
        // rides with the call because a ReadingPosition carries no identity of its own.
        if (chatScreen != null && isTheBranchOnScreen(branchId)) {
            chatScreen.renderPosition(position);
        }
    }

    /** Whether {@code branchId} is the branch the current conversation screen was opened for. */
    private boolean isTheBranchOnScreen(String branchId) {
        return chatBranchId != null && chatBranchId.equals(branchId);
    }

    @Override
    public void render(TreeCanvasViewState state) {
        treeCanvasScreen.render(state);
    }

    /**
     * The navigation page's state. Guarded the way the conversation's now is: its presenter posts from
     * a screen the learner has already left (a tree read that finishes after back was pressed), and a
     * page that is gone must not be drawn into.
     */
    @Override
    public void render(NavigationViewState state) {
        if (navigationScreen != null) {
            navigationScreen.render(state);
        }
    }

    /**
     * The search state, drawn inside the library (054). The page object is built off the UI thread,
     * because naming a row's branch is a store read like any other; only the finished page comes back
     * to be drawn, and a page whose screen has been left in the meantime is dropped rather than drawn
     * into.
     */
    @Override
    public void render(SearchViewState state) {
        final LibraryScreen screen = libraryScreen;
        if (screen == null) {
            return;
        }
        final BranchTitles titles = container.branchTitles();
        container.background().execute(() -> {
            final SearchPage page = SearchPage.of(state, titles);
            container.uiThread().post(() -> {
                if (libraryScreen == screen) {
                    screen.showSearch(state, page);
                }
            });
        });
    }

    /**
     * A tapped search row: the branch it belongs to, standing where the row was found.
     *
     * <p>{@code topicId} travels with the hit but is not needed for the move - the branch row already
     * knows its topic - and it is not looked up again here. This is the one production caller of
     * {@link #showChat(String, ReadingAnchor)}, the door T-042 built, so a search jump and a restore on
     * the way back in are one position written through one store rather than two ways of pointing at a
     * conversation.
     */
    @Override
    public void openAnchor(String topicId, String branchId, ReadingAnchor anchor) {
        showChat(branchId, anchor);
    }

    /**
     * Leaving a tree, one name for two sinks.
     *
     * <p>{@link TreeCanvasSink#leaveTree()} and {@link NavigationSink#leaveTree()} share a signature and
     * mean different destinations: the canvas steps back to the conversation it was opened from, while
     * the navigation page is left for the home screen - its presenter only asks when the topic's tree read
     * came back empty, so there is no branch left to converse on. Only one of the two can be on top, which
     * is the same fact {@link #onBackRequested()} already orders by.
     */
    @Override
    public void leaveTree() {
        if (navigationBranchId != null) {
            showHome();
            return;
        }
        showChat(treeBranchId);
    }

    /**
     * The conversation's overview entry: the topic's whole tree, centred on the branch the learner
     * stood on. It is navigation only - the branch the conversation shows is not changed by looking at
     * the tree around it (prototype v2.5, {@code openCanvas}).
     */
    @Override
    public void openOverview(String branchId) {
        showTree(branchId);
    }

    /**
     * The navigation page's entry, from the conversation and from the canvas alike. One method on
     * purpose, exactly like {@link #openBranch(String)}: wherever the page is asked for, it opens on the
     * tree that branch belongs to.
     */
    @Override
    public void openNavigation(String branchId) {
        showNavigation(branchId);
    }

    /**
     * The clipboard is the one place an answer leaves the app, so the clip is marked sensitive on the
     * versions that support it: the system then keeps it out of the clipboard preview instead of
     * showing a reasoning excerpt on the lock screen.
     */
    @Override
    public void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        ClipData clip = ClipData.newPlainText(getString(R.string.chat_copy_clip_label), text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.chat_copied, Toast.LENGTH_SHORT).show();
    }

    /**
     * The child-branch list of one answer. The branches were read before this was called, so the list
     * is drawn from what the presenter handed over; a selected row returns to the current presenter
     * so its branch visit is added to the back history.
     */
    @Override
    public void openChildren(String messageId, List<ChildBranch> children) {
        ChatPresenter presenter = chatPresenter;
        chatScreen.showChildren(messageId, children, presenter::onChildBranchChosen);
    }

    @Override
    public void openVersions(String messageId, List<AnswerRevision> versions) {
        chatScreen.showVersions(messageId, versions);
    }

    /**
     * A conversation the store refused to read. The learner already sees the one static sentence the
     * screen owns, so this is the line that makes the failure diagnosable afterwards: it carries the
     * branch id and the store's own reason, and the read that failed produced no messages, so it can
     * carry no learner content.
     */
    @Override
    public void reportUnreadable(String branchId, String cause) {
        android.util.Log.w("TreelearnUi", "read failed branch=" + branchId + " cause=" + cause);
    }

    /** Closing a root conversation restores the screen that opened the chat. */
    @Override
    public void close() {
        ChatEntryRoute route = chatEntryRoute;
        chatEntryRoute = null;
        if (route == null) {
            showTab(currentTab);
            return;
        }
        route.restore(new ChatEntryRoute.Restorer() {
            @Override
            public void showTab(ChatEntryRoute.Destination destination) {
                switch (destination) {
                    case LIBRARY:
                        showLibrary();
                        break;
                    case SETTINGS:
                        MainActivity.this.showSettings();
                        break;
                    default:
                        showHome();
                        break;
                }
            }

            @Override
            public void showCanvas(String branchId) {
                showTree(branchId);
            }

            @Override
            public void showNavigation(String branchId) {
                MainActivity.this.showNavigation(branchId);
            }
        });
    }

    private void showHome() {
        chatEntryRoute = null;
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        chatBranchId = null;
        stopSpeechSession();
        chatScreen = null;
        chatPresenter = null;
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        showingSettings = false;
        searchPresenter = null;
        libraryScreen = null;
        libraryPresenter = null;
        selectTab(Tab.HOME);
        swap(homeScreen);
        homeDrawerPresenter.start();
        if (homeStarted) {
            homePresenter.onResumed();
        }
    }

    private void showSettings() {
        // Settings is a room opened from Home or Chat, not a bottom navigation destination.
        showingSettings = true;
        libraryScreen = null;
        libraryPresenter = null;
        keyAdminScreen = null;
        providerAdminScreen = null;
        providerAdminPresenter = null;
        modelSettingsScreen = null;
        modelSettingsPresenter = null;
        searchSettingsScreen = null;
        searchSettingsPresenter = null;
        voiceSettingsScreen = null;
        voiceSettingsPresenter = null;
        fileManagerScreen = null;
        fileManagerPresenter = null;
        statsDetailScreen = null;
        statsDetailPresenter = null;
        swap(settingsScreen);
        settingsPresenter.start();
        refreshProvidersSubtitle();
        refreshModelsSubtitle();
    }

    /** Opens the API KEY room (settings redesign 2026-09-28). It shares the settings presenter, so
     *  its renders arrive through {@link #render(SettingsViewState)} and are routed by whether the
     *  room is on screen; start() re-reads the store, exactly like every other room's open. */
    private void showKeyAdmin() {
        keyAdminScreen = new SettingsKeyScreen(this);
        keyAdminScreen.setActions(settingsPresenter);
        keyAdminScreen.setOnBackPressed(this::onBackRequested);
        swap(keyAdminScreen);
        settingsPresenter.start();
    }

    private void showProviders() {
        providerAdminScreen = new ProviderAdminScreen(this);
        providerAdminPresenter = new ProviderAdminPresenter(providerAdminScreen,
                container.uiThread(), container.background(), container.providerAdminStore());
        providerAdminScreen.setActions(providerAdminPresenter);
        providerAdminScreen.setOnBackPressed(this::onBackRequested);
        swap(providerAdminScreen);
        providerAdminPresenter.start();
    }

    private void refreshProvidersSubtitle() {
        container.background().execute(() -> {
            int enabled = 0;
            for (ProviderAdminPresenter.Row row : container.providerAdminStore().list()) {
                if (!row.enabled) {
                    continue;
                }
                enabled++;
            }
            int count = enabled;
            container.uiThread().post(() -> {
                if (providerAdminScreen == null) {
                    settingsScreen.setProvidersSubtitle(getString(R.string.providers_entry_enabled_count,
                            count));
                }
            });
        });
    }

    private void showModels() {
        modelSettingsScreen = new ModelSettingsScreen(this);
        modelSettingsPresenter = new ModelSettingsPresenter(modelSettingsScreen,
                container.uiThread(), container.background(), container.modelSettingsStore());
        modelSettingsScreen.setActions(modelSettingsPresenter);
        modelSettingsScreen.setOnBackPressed(this::onBackRequested);
        swap(modelSettingsScreen);
        modelSettingsPresenter.start();
    }

    private void showSearchSettings() {
        searchSettingsScreen = new SearchServiceSettingsScreen(this);
        searchSettingsPresenter = new SearchSettingsPresenter(searchSettingsScreen,
                container.uiThread(), container.background(), container.searchSettingsStore());
        searchSettingsScreen.setActions(searchSettingsPresenter);
        searchSettingsScreen.setOnBackPressed(this::onBackRequested);
        swap(searchSettingsScreen);
        searchSettingsPresenter.start();
    }

    private void showVoiceSettings() {
        voiceSettingsScreen = new VoiceSettingsScreen(this);
        voiceSettingsPresenter = new VoiceSettingsPresenter(voiceSettingsScreen,
                container.uiThread(), container.background(), container.voiceSettingsStore(),
                new SystemTtsEngine(this), getString(R.string.voice_audition_text));
        voiceSettingsScreen.setActions(voiceSettingsPresenter);
        voiceSettingsScreen.setOnBackPressed(this::onBackRequested);
        swap(voiceSettingsScreen);
        voiceSettingsPresenter.start();
    }

    /** Opens the file manager (T-096): built here and dropped on leave, like every other screen. */
    private void showFileManager() {
        fileManagerScreen = new FileManagerScreen(this);
        fileManagerPresenter = new FileManagerPresenter(fileManagerScreen,
                container.uiThread(), container.background(), container.fileManagerStore());
        fileManagerScreen.setActions(fileManagerPresenter);
        fileManagerScreen.setOnBackPressed(this::onBackRequested);
        swap(fileManagerScreen);
        fileManagerPresenter.start();
    }

    /**
     * T-104: the statistics detail room. The drawer (T-106) is the production entry; this method is
     * the navigation target it calls, exactly like every other room's show method.
     */
    private void showStatsDetail() {
        statsDetailScreen = new StatsDetailScreen(this);
        statsDetailPresenter = new StatsDetailPresenter(statsDetailScreen,
                container.uiThread(), container.background(),
                container.homeStore()::statsRaw, container.clock());
        statsDetailScreen.setOnBackPressed(this::onBackRequested);
        swap(statsDetailScreen);
        statsDetailPresenter.start();
    }

    /** The SAF picker's request code; T-097 owns this one host-side flow. */
    private static final int REQUEST_ATTACH = 0x7A6;

    /** The microphone permission request code; T-095 owns this one host-side flow. */
    private static final int REQUEST_MICROPHONE = 0x7A7;

    private SpeechInputPolicy speechPolicy = new SpeechInputPolicy();
    private com.treelearn.app.speech.SpeechInputGateway speechGateway;
    private String speechPartial = "";
    /** True only while the finger that asked for the mic permission is still holding the key. */
    private boolean speechPressActive;

    /** T-097: the attach key opens the SAF picker; cancellation lands back here untouched. */
    private void onAttachPressed() {
        android.content.Intent picker = new android.content.Intent(
                android.content.Intent.ACTION_GET_CONTENT);
        picker.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{
                "text/plain", "text/markdown", "application/pdf"});
        try {
            startActivityForResult(
                    android.content.Intent.createChooser(picker, getString(R.string.cd_chat_attach)),
                    REQUEST_ATTACH);
        } catch (android.content.ActivityNotFoundException noPicker) {
            // No documents provider on this device: the entry reports and changes nothing, the
            // same no-side-effect rule the SAF cancel path follows.
        }
    }

    /**
     * T-095 press-and-hold start: the permission is asked here and only here (SPEC-081), never at
     * launch. Granted - the session starts; denied - the key greys out and the session never starts.
     */
    private void onVoicePressed() {
        if (checkSelfPermission("android.permission.RECORD_AUDIO")
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startSpeechSession();
            return;
        }
        // The grant callback starts the session only if this press is still held: releasing
        // inside the system dialog must not leave a mic session running with no finger down.
        speechPressActive = true;
        requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, REQUEST_MICROPHONE);
    }

    private void onVoiceReleased() {
        speechPressActive = false;
        stopSpeechSession();
    }

    /** Ends any live recognizer session; safe to call from anywhere, any number of times. */
    private void stopSpeechSession() {
        speechPressActive = false;
        if (speechGateway != null) {
            speechGateway.stop();
        }
        speechPartial = "";
        if (chatScreen != null) {
            chatScreen.setListening(false);
        }
    }

    private void startSpeechSession() {
        if (speechGateway == null || !speechGateway.isAvailable()) {
            return;
        }
        speechPartial = "";
        chatScreen.setListening(true);
        speechGateway.start(new com.treelearn.app.speech.SpeechInputGateway.Listener() {

            @Override
            public void onPartial(String text) {
                // [OFF] insurance: keep the platform partial so an error can still spend it.
                speechPartial = text == null ? "" : text;
            }

            @Override
            public void onFinal(String text) {
                chatScreen.setListening(false);
                speechPartial = "";
                if (chatPresenter != null) {
                    chatPresenter.onSpeechRecognized(text);
                }
            }

            @Override
            public void onError(int code) {
                chatScreen.setListening(false);
                // [OFF] (SPEC-081): keep what the platform already heard, then fall back quietly.
                if (chatPresenter != null && !speechPartial.trim().isEmpty()) {
                    chatPresenter.onSpeechRecognized(speechPartial);
                }
                speechPartial = "";
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_MICROPHONE) {
            return;
        }
        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            speechPolicy.markPermissionGranted();
            chatScreen.setSpeechPolicy(speechPolicy);
            // Start only for a press still held (released inside the dialog => no session).
            if (speechPressActive) {
                startSpeechSession();
            }
            return;
        }
        speechPolicy.markPermissionDenied();
        chatScreen.setSpeechPolicy(speechPolicy);
        // Denied now: the key greys out and the user is told on the spot (SPEC-081 [PERM]).
        android.widget.Toast.makeText(this, R.string.chat_voice_denied,
                android.widget.Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ATTACH || resultCode != RESULT_OK || data == null
                || data.getData() == null) {
            return;
        }
        final android.net.Uri uri = data.getData();
        final ChatPresenter presenter = chatPresenter;
        if (presenter == null) {
            return;
        }
        container.background().execute(() -> {
            try {
                String name = queryDisplayName(uri);
                String mime = resolveMime(uri);
                byte[] bytes = readCapped(uri);
                container.uiThread().post(
                        () -> presenter.onAttachmentPicked(name, mime, bytes));
            } catch (java.io.IOException | RuntimeException unreadable) {
                // An unreadable pick changes nothing: no record, no chip, no draft damage.
            }
        });
    }

    private String queryDisplayName(android.net.Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index >= 0 && cursor.getString(index) != null) {
                    return cursor.getString(index);
                }
            }
        }
        return "SYNTHETIC-ATTACHMENT";
    }

    private String resolveMime(android.net.Uri uri) {
        String resolved = getContentResolver().getType(uri);
        return resolved == null ? "application/octet-stream" : resolved;
    }

    /** Reads the pick fully; the extractor's own ceiling refuses anything over 20 MB. */
    private byte[] readCapped(android.net.Uri uri) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new java.io.IOException("no stream for " + uri);
            }
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) {
                out.write(chunk, 0, n);
            }
        }
        return out.toByteArray();
    }

    private ChatPresenter.Translator translationPort() {
        return new ChatPresenter.Translator() {
            @Override
            public void translate(GenerationModel fallbackModel, String sourceText,
                    ChatPresenter.Translator.Callback callback) {
                container.messageTranslator().translate(fallbackModel, sourceText, callback::onFinished);
            }
        };
    }

    private void refreshModelsSubtitle() {
        container.background().execute(() -> {
            String label = container.modelSettingsStore().read(ModelSettingsPresenter.Role.CHAT).label;
            container.uiThread().post(() -> {
                if (modelSettingsScreen == null && !label.isEmpty()) {
                    settingsScreen.setModelsSubtitle(label);
                }
            });
        });
    }

    /**
     * Opens the library: the topics the learner has, with the one entry into each of them.
     *
     * <p>Built here and dropped when the tab is left, like every other screen, so the list is read
     * fresh on each visit - a topic created in a conversation is on the next look rather than on the
     * next launch.
     */
    private void showLibrary() {
        chatEntryRoute = null;
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        chatBranchId = null;
        stopSpeechSession();
        chatScreen = null;
        chatPresenter = null;
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        showingSettings = false;
        searchPresenter = new SearchPresenter(this, container.uiThread(), container.background(),
                container.searchRepository());
        libraryScreen = new LibraryScreen(this);
        libraryPresenter = new LibraryPresenter(this, container.uiThread(), container.background(),
                container.libraryData(), searchPresenter);
        libraryScreen.setActions(libraryPresenter);
        libraryScreen.setOnBackPressed(this::onBackRequested);
        selectTab(Tab.LIBRARY);
        swap(libraryScreen);
        libraryPresenter.start();
    }

    /**
     * Opens a conversation on one branch. Its screen is rebuilt per visit; the branch and first-entry
     * route, rather than a surviving view, determine where its top-bar Back goes.
     */
    private void showChat(String branchId) {
        showChat(branchId, null, "");
    }

    private void showChat(String branchId, String initialQuestion) {
        showChat(branchId, null, initialQuestion);
    }

    /**
     * Opens the conversation on one branch, optionally standing on a position the caller chose.
     *
     * <p>This is the one door into a conversation, and the explicit anchor is what a search jump will
     * hand it: a tapped hit is a reading position like any other, so it is written through the same
     * port the session writes through and the ordinary restore finds it when the branch opens. The
     * write is queued before the read that follows it, and the container's store thread is a single
     * one, so the position is stored by the time the branch is read - and neither of the two runs on
     * the UI thread.
     */
    private void showChat(String branchId, ReadingAnchor explicitAnchor) {
        showChat(branchId, explicitAnchor, "");
    }

    private void showChat(String branchId, ReadingAnchor explicitAnchor, String initialQuestion) {
        if (branchId == null || branchId.isEmpty()) {
            return;
        }
        chatEntryRoute = captureChatEntryRoute(chatEntryRoute);
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        chatBranchId = branchId;
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        showingSettings = false;
        searchPresenter = null;
        libraryScreen = null;
        libraryPresenter = null;
        ReadingStore reading = container.readingStore();
        chatScreen = new ChatScreen(this);
        ChatSpeaker chatSpeaker = new ChatSpeaker(chatScreen, container.uiThread(),
                new SystemTtsEngine(this));
        chatScreen.setSpeaker(chatSpeaker);
        chatPresenter = new ChatPresenter(this, container.uiThread(), container.background(),
                container.keyVault(), container.chatData(), container.chatCommands(),
                container.settingsStore(), container.chatCopyLabels(), branchId, reading,
                container.chatDraftStore(), translationPort(), container.chatSearchStep(),
                chatSpeaker, container.attachmentStore(),
                () -> container.modelSettingsStore()
                        .read(ModelSettingsPresenter.Role.CHAT));
        chatScreen.setActions(chatPresenter);

        // The speech key (T-095, SPEC-081): the host owns the recognizer, the permission flow and
        // the policy; the screen only draws. No recognition service on this device means the key
        // never renders at all - the no-fake-entry rule.
        com.treelearn.app.speech.SpeechInputGateway gateway =
                new com.treelearn.app.speech.PlatformSpeechInput(this);
        speechGateway = gateway;
        chatScreen.setSpeechPolicy(speechPolicy);
        chatScreen.setVoiceAvailable(gateway.isAvailable());
        chatScreen.setOnVoicePressed(this::onVoicePressed);
        chatScreen.setOnVoiceReleased(this::onVoiceReleased);
        chatScreen.setOnVoiceGuidance(() -> android.widget.Toast.makeText(this,
                R.string.chat_voice_denied, android.widget.Toast.LENGTH_SHORT).show());
        chatScreen.setOnAttachPressed(this::onAttachPressed);
        swap(chatScreen);
        if (explicitAnchor != null) {
            ChatPresenter presenter = chatPresenter;
            container.background().execute(() -> {
                try {
                    reading.saveAnchor(branchId, explicitAnchor);
                } catch (RuntimeException refused) {
                    if (presenter != null) {
                        presenter.notifyReadingPositionRefused();
                    }
                }
            });
        }
        chatPresenter.start(initialQuestion);
        chatSpeaker.start();
    }

    /**
     * Opens the canvas on one branch. Like the conversation it is built here and dropped when it is
     * left, so the branch id - not a surviving view - is what a return needs.
     *
     * <p>The initial viewport is the host's own size, read at the moment of the tap. It is only the
     * seed the first fit is computed with: the canvas view reports its real box through
     * {@code onViewportChanged} the moment it is laid out, and that is the measurement every later
     * frame uses. A zero-size seed is not an option - a read that answers before the first layout would
     * then fit the tree into an empty box, and the later resize deliberately keeps the zoom it finds
     * rather than refitting, so the tree would be drawn off-screen.
     */
    private void showTree(String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            return;
        }
        chatEntryRoute = null;
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        treeBranchId = branchId;
        chatBranchId = null;
        stopSpeechSession();
        chatScreen = null;
        chatPresenter = null;
        showingSettings = false;
        searchPresenter = null;
        libraryScreen = null;
        libraryPresenter = null;
        treeCanvasScreen = new TreeCanvasScreen(this);
        treeCanvasPresenter = new TreeCanvasPresenter(this, container.uiThread(),
                container.background(), container.treeCanvasData(), container.treeCanvasCommands(),
                ViewportConstraints.of(host.getWidth(), host.getHeight()),
                getResources().getDisplayMetrics().density, branchId);
        treeCanvasScreen.setActions(treeCanvasPresenter);
        swap(treeCanvasScreen);
        treeCanvasPresenter.start();
    }

    /**
     * Opens the navigation page on the tree one branch belongs to.
     *
     * <p>The topic is resolved on the background thread, never here: it is a store read, and this class
     * holds no database handle for the same reason no other screen does. Only one entry is in flight at a
     * time, so a double tap cannot build two pages, and an entry that finds no topic leaves the screen
     * exactly as it was.
     */
    private void showNavigation(String branchId) {
        if (branchId == null || branchId.isEmpty() || navigatingTo != null) {
            return;
        }
        navigatingTo = branchId;
        container.background().execute(() -> {
            final String topicId = container.navigationData().topicOf(branchId);
            container.uiThread().post(() -> {
                navigatingTo = null;
                if (topicId != null) {
                    enterNavigation(topicId, branchId);
                }
            });
        });
    }

    /**
     * Builds the page on the topic the entry resolved. The screen and its presenter are built here and
     * dropped when the page is left, so the branch id - not a surviving view - is what a return needs.
     */
    private void enterNavigation(String topicId, String branchId) {
        chatEntryRoute = null;
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        chatBranchId = null;
        stopSpeechSession();
        chatScreen = null;
        chatPresenter = null;
        showingSettings = false;
        searchPresenter = null;
        libraryScreen = null;
        libraryPresenter = null;
        navigationBranchId = branchId;
        navigationScreen = new NavigationScreen(this);
        navigationPresenter = new NavigationPresenter(this, container.uiThread(),
                container.background(), container.navigationData(), topicId, branchId);
        navigationScreen.setActions(navigationPresenter);
        navigationScreen.setHost(new NavigationScreen.Host() {
            @Override
            public void leaveNavigation() {
                onBackRequested();
            }

            @Override
            public void openBranch(String selected) {
                showChat(selected);
            }

            @Override
            public void openOverview(String selected) {
                showTree(selected);
            }
        });
        swap(navigationScreen);
        navigationPresenter.start();
    }

    /**
     * The library's state. Guarded the way the navigation page's is: the list is read on the background
     * thread, so a read that lands after the learner has left the tab must be dropped rather than drawn
     * into a screen that is gone.
     */
    @Override
    public void render(LibraryViewState state) {
        if (libraryScreen != null) {
            libraryScreen.render(state);
        }
    }

    /**
     * The one place that decides whether the tab bar is on screen.
     *
     * <p>A first-level destination shows it and a second-level screen hides it, and that judgement is
     * made here rather than by each screen for itself: a bar every screen positioned on its own is a bar
     * whose visibility drifts the first time a screen forgets. The predicate reads the shell's own
     * knowledge of what it is showing, so it cannot disagree with the screen that was just swapped in.
     */
    private void swap(View screen) {
        host.removeAllViews();
        host.addView(screen, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // T-2026-09-27-007: only the home enters with the prototype's motion. The library and the
        // settings ride this same swap, so an unguarded call here would change how they look too.
        if (screen == homeScreen) {
            homeScreen.playEntrance();
        }
        tabBar.setVisibility(screen == homeScreen || screen == libraryScreen
                ? View.VISIBLE : View.GONE);
    }

    /** A first-level destination, by tab. The only way the level moves besides the tab tap itself. */
    private void showTab(Tab tab) {
        switch (tab) {
            case LIBRARY:
                showLibrary();
                break;
            default:
                showHome();
                break;
        }
    }

    private ChatEntryRoute captureChatEntryRoute(ChatEntryRoute existing) {
        ChatEntryRoute.Destination selectedTab;
        switch (currentTab) {
            case LIBRARY:
                selectedTab = ChatEntryRoute.Destination.LIBRARY;
                break;
            default:
                selectedTab = ChatEntryRoute.Destination.HOME;
                break;
        }
        return ChatEntryRoute.captureIfAbsent(existing, selectedTab, libraryScreen != null,
                navigationBranchId, treeBranchId);
    }

    /** Marks where the first level stands; the bar is a readout of that fact and decides nothing. */
    private void selectTab(Tab tab) {
        currentTab = tab;
        markTab(tabHome, R.id.tab_home_icon, R.id.tab_home_label, tab == Tab.HOME);
        markTab(tabLibrary, R.id.tab_library_icon, R.id.tab_library_label, tab == Tab.LIBRARY);
    }

    /**
     * Paints one tab item for the state it is in. Both the label and the icon carry the state - a bar
     * that moved only one of them would read as half-selected, and it is the pair the learner sees.
     *
     * <p>The item's two children are named by the caller rather than looked up by a shared id: three
     * items in one layout cannot all answer to one id, so each tab names its own and the caller is the
     * one that knows which pair it handed over.
     */
    private void markTab(View item, int iconId, int labelId, boolean selected) {
        int color = getColor(selected ? R.color.tl_green_deep : R.color.tl_ink3);
        item.setSelected(selected);
        ((TextView) item.findViewById(labelId)).setTextColor(color);
        ((ImageView) item.findViewById(iconId)).setImageTintList(ColorStateList.valueOf(color));
    }
}
