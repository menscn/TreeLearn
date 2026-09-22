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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import com.treelearn.app.library.LibraryPresenter;
import com.treelearn.app.library.LibrarySink;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.screen.ChatScreen;
import com.treelearn.app.screen.HomeScreen;
import com.treelearn.app.screen.LibraryScreen;
import com.treelearn.app.screen.NavigationScreen;
import com.treelearn.app.screen.SearchScreen;
import com.treelearn.app.screen.SettingsScreen;
import com.treelearn.app.screen.TreeCanvasScreen;
import com.treelearn.app.navigation.NavigationPresenter;
import com.treelearn.app.navigation.NavigationSink;
import com.treelearn.app.navigation.NavigationViewState;
import com.treelearn.app.reading.ReadingStore;
import com.treelearn.app.search.BranchTitles;
import com.treelearn.app.search.SearchPage;
import com.treelearn.app.search.SearchPresenter;
import com.treelearn.app.search.SearchViewState;
import com.treelearn.app.ui.ChatPresenter;
import com.treelearn.app.ui.ChatSink;
import com.treelearn.app.ui.ChatViewState;
import com.treelearn.app.ui.ChildBranch;
import com.treelearn.app.ui.HomePresenter;
import com.treelearn.app.ui.HomeSink;
import com.treelearn.app.ui.HomeViewState;
import com.treelearn.app.ui.SettingsPresenter;
import com.treelearn.app.ui.SettingsSink;
import com.treelearn.app.ui.SettingsViewState;
import com.treelearn.app.ui.TreeCanvasPresenter;
import com.treelearn.app.ui.TreeCanvasSink;
import com.treelearn.app.ui.TreeCanvasViewState;
import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.ReadingAnchor;
import com.treelearn.core.domain.ReadingPosition;
import com.treelearn.treelayout.ViewportConstraints;

import java.util.List;

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
 * <p>The shell is the only place that knows about every screen; it holds no view state, no draft and no
 * decision of its own beyond two: which screen is on top - because that is what back acts on - and
 * which tab the first level is standing on, because that is what back returns to. A conversation, a
 * canvas, the navigation page and the library are created per visit and dropped when they are left, so
 * nothing keeps a read screen or its presenter alive.
 */
public final class MainActivity extends Activity
        implements HomeSink, SettingsSink, ChatSink, TreeCanvasSink, NavigationSink,
        SearchPresenter.Sink, LibrarySink {

    /** The three first-level destinations the bottom bar carries. */
    private enum Tab {
        HOME,
        LIBRARY,
        SETTINGS
    }

    private FrameLayout host;
    private View tabBar;
    private View tabHome;
    private View tabLibrary;
    private View tabSettings;
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

    /** The search page and its decisions; null while the page is not on screen. */
    private SearchScreen searchScreen;
    private SearchPresenter searchPresenter;

    /** The library page and its decisions; null while the page is not on screen. */
    private LibraryScreen libraryScreen;
    private LibraryPresenter libraryPresenter;

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
        tabSettings = findViewById(R.id.tab_settings);
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
        settingsScreen.setActions(settingsPresenter);
        settingsScreen.setOnBackPressed(this::onBackRequested);

        tabHome.setOnClickListener(view -> showHome());
        tabLibrary.setOnClickListener(view -> showLibrary());
        tabSettings.setOnClickListener(view -> showSettings());

        showHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        if (searchScreen != null) {
            searchPresenter.onResumed();
            return;
        }
        if (libraryScreen != null) {
            libraryPresenter.onResumed();
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
     * from - the same move the prototype's canvas appbar makes. The library steps back to the home tab,
     * a conversation steps back to the tab it was opened from, the search page steps back to the tab
     * that opened it, and the home page leaves the app.
     *
     * <p>Both platforms land here, so there is a single place that decides what back means and no
     * second copy of the rule to drift. A second-level screen returns through {@link #showTab} rather
     * than to the home page: the tab the learner left from is the tab they come back to.
     */
    private void onBackRequested() {
        if (showingSettings) {
            if (chatBranchId == null) {
                showHome();
            } else {
                showChat(chatBranchId);
            }
            return;
        }
        if (searchScreen != null) {
            showTab(currentTab);
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
            showTab(currentTab);
            return;
        }
        if (libraryScreen != null) {
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

    /**
     * A tapped topic, a branch a fork just created, or a node tapped on the canvas opens the
     * conversation on that branch. Both {@link ChatSink} and {@link TreeCanvasSink} ask for it, and it
     * is one method on purpose: wherever a branch is chosen, it opens the same way.
     */
    @Override
    public void openBranch(String branchId) {
        showChat(branchId);
    }

    @Override
    public void render(SettingsViewState state) {
        settingsScreen.render(state);
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
     * The search page's state. The page object is built off the UI thread, because naming a row's
     * branch is a store read like any other; only the finished page comes back to be drawn, and a page
     * whose screen has been left in the meantime is dropped rather than drawn into.
     */
    @Override
    public void render(SearchViewState state) {
        final SearchScreen screen = searchScreen;
        if (screen == null) {
            return;
        }
        final BranchTitles titles = container.branchTitles();
        container.background().execute(() -> {
            final SearchPage page = SearchPage.of(state, titles);
            container.uiThread().post(() -> drawSearch(screen, state, page));
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
     * The home page's "archived" entry. The page shows one topic's tree and the home list is ordered
     * newest first, so the entry opens the tree of the most recently used topic - the one whose rows the
     * learner is looking at.
     */
    @Override
    public void openArchived(String branchId) {
        showNavigation(branchId);
    }

    /**
     * The home page's search entry. It opens a page of its own rather than a branch screen, so it reads
     * no branch and resolves no topic: search spans every branch, including the ones the learner has
     * never opened.
     */
    @Override
    public void openSearch() {
        showSearch();
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
     * is drawn from what the presenter handed over; its rows open a branch the same way every other
     * branch is opened, and leaving the list without choosing one opens nothing.
     */
    @Override
    public void openChildren(String branchId, String messageId, List<ChildBranch> children) {
        chatScreen.showChildren(messageId, children, this::openBranch);
    }

    @Override
    public void openVersions(String messageId, List<AnswerRevision> versions) {
        chatScreen.showVersions(messageId, versions);
    }

    /** A conversation is left for the first level it was opened from, not for the home page. */
    @Override
    public void close() {
        showTab(currentTab);
    }

    private void showHome() {
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        chatBranchId = null;
        chatScreen = null;
        chatPresenter = null;
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        showingSettings = false;
        searchScreen = null;
        searchPresenter = null;
        libraryScreen = null;
        libraryPresenter = null;
        selectTab(Tab.HOME);
        swap(homeScreen);
        if (homeStarted) {
            homePresenter.onResumed();
        }
    }

    private void showSettings() {
        // Settings is a first-level destination when it is reached as one. Reached from a conversation
        // it is a detour inside that conversation, and the level underneath stays where it was, which is
        // what makes leaving it return to the conversation rather than to the settings tab.
        if (settingsIsAFrontRoom()) {
            selectTab(Tab.SETTINGS);
        }
        showingSettings = true;
        libraryScreen = null;
        libraryPresenter = null;
        swap(settingsScreen);
        settingsPresenter.start();
    }

    /**
     * Opens the library: the topics the learner has, with the one entry into each of them.
     *
     * <p>Built here and dropped when the tab is left, like every other screen, so the list is read
     * fresh on each visit - a topic created in a conversation is on the next look rather than on the
     * next launch.
     */
    private void showLibrary() {
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        chatBranchId = null;
        chatScreen = null;
        chatPresenter = null;
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        showingSettings = false;
        searchScreen = null;
        searchPresenter = null;
        libraryScreen = new LibraryScreen(this);
        libraryPresenter = new LibraryPresenter(this, container.uiThread(), container.background(),
                container.libraryData());
        libraryScreen.setActions(libraryPresenter);
        selectTab(Tab.LIBRARY);
        swap(libraryScreen);
        libraryPresenter.start();
    }

    /**
     * Opens the conversation on one branch. The screen and its presenter are built here and dropped
     * when the conversation is left, so the branch id - not a surviving view - is what a return needs.
     */
    private void showChat(String branchId) {
        showChat(branchId, null);
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
        if (branchId == null || branchId.isEmpty()) {
            return;
        }
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        chatBranchId = branchId;
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        showingSettings = false;
        searchScreen = null;
        searchPresenter = null;
        libraryScreen = null;
        libraryPresenter = null;
        ReadingStore reading = container.readingStore();
        chatScreen = new ChatScreen(this);
        chatPresenter = new ChatPresenter(this, container.uiThread(), container.background(),
                container.keyVault(), container.chatData(), container.chatCommands(),
                container.settingsStore(), container.chatCopyLabels(), branchId, reading,
                container.chatDraftStore());
        chatScreen.setActions(chatPresenter);
        swap(chatScreen);
        if (explicitAnchor != null) {
            container.background().execute(() -> reading.saveAnchor(branchId, explicitAnchor));
        }
        chatPresenter.start();
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
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        treeBranchId = branchId;
        chatBranchId = null;
        chatScreen = null;
        chatPresenter = null;
        showingSettings = false;
        searchScreen = null;
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
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        chatBranchId = null;
        chatScreen = null;
        chatPresenter = null;
        showingSettings = false;
        searchScreen = null;
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
     * Opens the search page. Built here and dropped when it is left, like every other screen, so the
     * query lives for exactly one visit: no draft is left behind to surprise the learner the next time
     * the entry is tapped.
     */
    private void showSearch() {
        navigationBranchId = null;
        navigationScreen = null;
        navigationPresenter = null;
        treeBranchId = null;
        treeCanvasScreen = null;
        treeCanvasPresenter = null;
        chatBranchId = null;
        chatScreen = null;
        chatPresenter = null;
        showingSettings = false;
        libraryScreen = null;
        libraryPresenter = null;
        searchScreen = new SearchScreen(this);
        searchPresenter = new SearchPresenter(this, container.uiThread(), container.background(),
                container.searchRepository());
        searchScreen.setActions(searchPresenter);
        searchScreen.setHost(this::onBackRequested);
        swap(searchScreen);
        searchPresenter.start();
    }

    /** A page whose screen has been left is dropped rather than drawn into. */
    private void drawSearch(SearchScreen screen, SearchViewState state, SearchPage page) {
        if (searchScreen == screen) {
            screen.show(state, page);
        }
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
        tabBar.setVisibility(screen == homeScreen || screen == libraryScreen
                || (screen == settingsScreen && settingsIsAFrontRoom())
                        ? View.VISIBLE : View.GONE);
    }

    /**
     * True when settings stands as a first-level destination rather than as a detour from a conversation.
     * One rule with two readers - {@link #showSettings()} and {@link #swap(View)} - so the bar and the
     * highlight cannot disagree about what the page is.
     */
    private boolean settingsIsAFrontRoom() {
        return chatBranchId == null;
    }

    /** A first-level destination, by tab. The only way the level moves besides the tab tap itself. */
    private void showTab(Tab tab) {
        switch (tab) {
            case LIBRARY:
                showLibrary();
                break;
            case SETTINGS:
                showSettings();
                break;
            default:
                showHome();
                break;
        }
    }

    /** Marks where the first level stands; the bar is a readout of that fact and decides nothing. */
    private void selectTab(Tab tab) {
        currentTab = tab;
        markTab(tabHome, R.id.tab_home_icon, R.id.tab_home_label, tab == Tab.HOME);
        markTab(tabLibrary, R.id.tab_library_icon, R.id.tab_library_label, tab == Tab.LIBRARY);
        markTab(tabSettings, R.id.tab_settings_icon, R.id.tab_settings_label, tab == Tab.SETTINGS);
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
