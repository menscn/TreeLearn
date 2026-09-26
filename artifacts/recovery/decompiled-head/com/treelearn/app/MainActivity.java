/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.Activity
 *  android.content.ClipData
 *  android.content.ClipboardManager
 *  android.content.Context
 *  android.content.res.ColorStateList
 *  android.os.Build$VERSION
 *  android.os.Bundle
 *  android.os.PersistableBundle
 *  android.view.KeyEvent
 *  android.view.View
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.FrameLayout
 *  android.widget.FrameLayout$LayoutParams
 *  android.widget.ImageView
 *  android.widget.TextView
 *  android.widget.Toast
 *  com.treelearn.app.R$color
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 *  com.treelearn.core.domain.AnswerRevision
 *  com.treelearn.core.domain.ReadingAnchor
 *  com.treelearn.core.domain.ReadingPosition
 *  com.treelearn.treelayout.ViewportConstraints
 */
package com.treelearn.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.treelearn.app.AppContainer;
import com.treelearn.app.R;
import com.treelearn.app.library.LibraryPresenter;
import com.treelearn.app.library.LibrarySink;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.app.navigation.NavigationPresenter;
import com.treelearn.app.navigation.NavigationSink;
import com.treelearn.app.navigation.NavigationViewState;
import com.treelearn.app.reading.ReadingStore;
import com.treelearn.app.screen.ChatScreen;
import com.treelearn.app.screen.HomeScreen;
import com.treelearn.app.screen.LibraryScreen;
import com.treelearn.app.screen.NavigationScreen;
import com.treelearn.app.screen.SearchScreen;
import com.treelearn.app.screen.SettingsScreen;
import com.treelearn.app.screen.TreeCanvasScreen;
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

public final class MainActivity
extends Activity
implements HomeSink,
SettingsSink,
ChatSink,
TreeCanvasSink,
NavigationSink,
SearchPresenter.Sink,
LibrarySink {
    private FrameLayout host;
    private View tabBar;
    private View tabHome;
    private View tabLibrary;
    private View tabSettings;
    private Tab currentTab = Tab.HOME;
    private HomeScreen homeScreen;
    private SettingsScreen settingsScreen;
    private HomePresenter homePresenter;
    private SettingsPresenter settingsPresenter;
    private AppContainer container;
    private ChatScreen chatScreen;
    private ChatPresenter chatPresenter;
    private String chatBranchId;
    private TreeCanvasScreen treeCanvasScreen;
    private TreeCanvasPresenter treeCanvasPresenter;
    private String treeBranchId;
    private NavigationScreen navigationScreen;
    private NavigationPresenter navigationPresenter;
    private String navigationBranchId;
    private String navigatingTo;
    private SearchScreen searchScreen;
    private SearchPresenter searchPresenter;
    private LibraryScreen libraryScreen;
    private LibraryPresenter libraryPresenter;
    private boolean showingSettings;
    private boolean homeStarted;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main);
        this.host = (FrameLayout)this.findViewById(R.id.shell_host);
        this.tabBar = this.findViewById(R.id.tab_bar);
        this.tabHome = this.findViewById(R.id.tab_home);
        this.tabLibrary = this.findViewById(R.id.tab_library);
        this.tabSettings = this.findViewById(R.id.tab_settings);
        this.registerSystemBack();
        this.container = AppContainer.get((Context)this);
        this.homeScreen = new HomeScreen((Context)this);
        this.settingsScreen = new SettingsScreen((Context)this);
        this.homePresenter = new HomePresenter(this, this.container.uiThread(), this.container.background(), this.container.keyVault(), this.container.homeData(), this.container.rootQuestionSubmitter(), this.container.settingsStore());
        this.settingsPresenter = new SettingsPresenter(this, this.container.uiThread(), this.container.background(), this.container.settingsStore());
        this.homeScreen.setActions(this.homePresenter);
        this.settingsScreen.setActions(this.settingsPresenter);
        this.settingsScreen.setOnBackPressed(this::onBackRequested);
        this.tabHome.setOnClickListener(view -> this.showHome());
        this.tabLibrary.setOnClickListener(view -> this.showLibrary());
        this.tabSettings.setOnClickListener(view -> this.showSettings());
        this.showHome();
    }

    protected void onResume() {
        super.onResume();
        if (this.showingSettings) {
            return;
        }
        if (this.navigationBranchId != null) {
            this.navigationPresenter.onResumed();
            return;
        }
        if (this.treeBranchId != null) {
            this.treeCanvasPresenter.onResumed();
            return;
        }
        if (this.chatBranchId != null) {
            this.chatPresenter.onResumed();
            return;
        }
        if (this.searchScreen != null) {
            this.searchPresenter.onResumed();
            return;
        }
        if (this.libraryScreen != null) {
            this.libraryPresenter.onResumed();
            return;
        }
        if (this.homeStarted) {
            this.homePresenter.onResumed();
        } else {
            this.homeStarted = true;
            this.homePresenter.start();
        }
    }

    private void registerSystemBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            this.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::onBackRequested);
        }
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (Build.VERSION.SDK_INT < 33 && event.getKeyCode() == 4 && event.getAction() == 1) {
            this.onBackRequested();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void onBackRequested() {
        if (this.showingSettings) {
            if (this.chatBranchId == null) {
                this.showHome();
            } else {
                this.showChat(this.chatBranchId);
            }
            return;
        }
        if (this.searchScreen != null) {
            this.showTab(this.currentTab);
            return;
        }
        if (this.navigationBranchId != null) {
            this.showChat(this.navigationBranchId);
            return;
        }
        if (this.treeBranchId != null) {
            this.showChat(this.treeBranchId);
            return;
        }
        if (this.chatBranchId != null) {
            this.showTab(this.currentTab);
            return;
        }
        if (this.libraryScreen != null) {
            this.showHome();
            return;
        }
        this.finish();
    }

    @Override
    public void render(HomeViewState state) {
        this.homeScreen.render(state);
    }

    @Override
    public void openSettings() {
        this.showSettings();
    }

    @Override
    public void openBranch(String branchId) {
        this.showChat(branchId);
    }

    @Override
    public void render(SettingsViewState state) {
        this.settingsScreen.render(state);
    }

    @Override
    public void render(ChatViewState state) {
        if (this.chatScreen != null && this.isTheBranchOnScreen(state.branchId)) {
            this.chatScreen.render(state);
        }
    }

    @Override
    public void renderPosition(String branchId, ReadingPosition position) {
        if (this.chatScreen != null && this.isTheBranchOnScreen(branchId)) {
            this.chatScreen.renderPosition(position);
        }
    }

    private boolean isTheBranchOnScreen(String branchId) {
        return this.chatBranchId != null && this.chatBranchId.equals(branchId);
    }

    @Override
    public void render(TreeCanvasViewState state) {
        this.treeCanvasScreen.render(state);
    }

    @Override
    public void render(NavigationViewState state) {
        if (this.navigationScreen != null) {
            this.navigationScreen.render(state);
        }
    }

    @Override
    public void render(SearchViewState state) {
        SearchScreen screen = this.searchScreen;
        if (screen == null) {
            return;
        }
        BranchTitles titles = this.container.branchTitles();
        this.container.background().execute(() -> {
            SearchPage page = SearchPage.of(state, titles);
            this.container.uiThread().post(() -> this.drawSearch(screen, state, page));
        });
    }

    @Override
    public void openAnchor(String topicId, String branchId, ReadingAnchor anchor) {
        this.showChat(branchId, anchor);
    }

    @Override
    public void leaveTree() {
        if (this.navigationBranchId != null) {
            this.showHome();
            return;
        }
        this.showChat(this.treeBranchId);
    }

    @Override
    public void openOverview(String branchId) {
        this.showTree(branchId);
    }

    @Override
    public void openNavigation(String branchId) {
        this.showNavigation(branchId);
    }

    @Override
    public void openArchived(String branchId) {
        this.showNavigation(branchId);
    }

    @Override
    public void openSearch() {
        this.showSearch();
    }

    @Override
    public void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager)this.getSystemService("clipboard");
        if (clipboard == null) {
            return;
        }
        ClipData clip = ClipData.newPlainText((CharSequence)this.getString(R.string.chat_copy_clip_label), (CharSequence)text);
        if (Build.VERSION.SDK_INT >= 33) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        Toast.makeText((Context)this, (int)R.string.chat_copied, (int)0).show();
    }

    @Override
    public void openChildren(String branchId, String messageId, List<ChildBranch> children) {
        this.chatScreen.showChildren(messageId, children, this::openBranch);
    }

    @Override
    public void openVersions(String messageId, List<AnswerRevision> versions) {
        this.chatScreen.showVersions(messageId, versions);
    }

    @Override
    public void close() {
        this.showTab(this.currentTab);
    }

    private void showHome() {
        this.navigationBranchId = null;
        this.navigationScreen = null;
        this.navigationPresenter = null;
        this.chatBranchId = null;
        this.chatScreen = null;
        this.chatPresenter = null;
        this.treeBranchId = null;
        this.treeCanvasScreen = null;
        this.treeCanvasPresenter = null;
        this.showingSettings = false;
        this.searchScreen = null;
        this.searchPresenter = null;
        this.libraryScreen = null;
        this.libraryPresenter = null;
        this.selectTab(Tab.HOME);
        this.swap((View)this.homeScreen);
        if (this.homeStarted) {
            this.homePresenter.onResumed();
        }
    }

    private void showSettings() {
        if (this.settingsIsAFrontRoom()) {
            this.selectTab(Tab.SETTINGS);
        }
        this.showingSettings = true;
        this.libraryScreen = null;
        this.libraryPresenter = null;
        this.swap((View)this.settingsScreen);
        this.settingsPresenter.start();
    }

    private void showLibrary() {
        this.navigationBranchId = null;
        this.navigationScreen = null;
        this.navigationPresenter = null;
        this.chatBranchId = null;
        this.chatScreen = null;
        this.chatPresenter = null;
        this.treeBranchId = null;
        this.treeCanvasScreen = null;
        this.treeCanvasPresenter = null;
        this.showingSettings = false;
        this.searchScreen = null;
        this.searchPresenter = null;
        this.libraryScreen = new LibraryScreen((Context)this);
        this.libraryPresenter = new LibraryPresenter(this, this.container.uiThread(), this.container.background(), this.container.libraryData());
        this.libraryScreen.setActions(this.libraryPresenter);
        this.selectTab(Tab.LIBRARY);
        this.swap((View)this.libraryScreen);
        this.libraryPresenter.start();
    }

    private void showChat(String branchId) {
        this.showChat(branchId, null);
    }

    private void showChat(String branchId, ReadingAnchor explicitAnchor) {
        if (branchId == null || branchId.isEmpty()) {
            return;
        }
        this.navigationBranchId = null;
        this.navigationScreen = null;
        this.navigationPresenter = null;
        this.chatBranchId = branchId;
        this.treeBranchId = null;
        this.treeCanvasScreen = null;
        this.treeCanvasPresenter = null;
        this.showingSettings = false;
        this.searchScreen = null;
        this.searchPresenter = null;
        this.libraryScreen = null;
        this.libraryPresenter = null;
        ReadingStore reading = this.container.readingStore();
        this.chatScreen = new ChatScreen((Context)this);
        this.chatPresenter = new ChatPresenter(this, this.container.uiThread(), this.container.background(), this.container.keyVault(), this.container.chatData(), this.container.chatCommands(), this.container.settingsStore(), this.container.chatCopyLabels(), branchId, reading, this.container.chatDraftStore());
        this.chatScreen.setActions(this.chatPresenter);
        this.swap((View)this.chatScreen);
        if (explicitAnchor != null) {
            this.container.background().execute(() -> reading.saveAnchor(branchId, explicitAnchor));
        }
        this.chatPresenter.start();
    }

    private void showTree(String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            return;
        }
        this.navigationBranchId = null;
        this.navigationScreen = null;
        this.navigationPresenter = null;
        this.treeBranchId = branchId;
        this.chatBranchId = null;
        this.chatScreen = null;
        this.chatPresenter = null;
        this.showingSettings = false;
        this.searchScreen = null;
        this.searchPresenter = null;
        this.libraryScreen = null;
        this.libraryPresenter = null;
        this.treeCanvasScreen = new TreeCanvasScreen((Context)this);
        this.treeCanvasPresenter = new TreeCanvasPresenter(this, this.container.uiThread(), this.container.background(), this.container.treeCanvasData(), this.container.treeCanvasCommands(), ViewportConstraints.of((float)this.host.getWidth(), (float)this.host.getHeight()), this.getResources().getDisplayMetrics().density, branchId);
        this.treeCanvasScreen.setActions(this.treeCanvasPresenter);
        this.swap((View)this.treeCanvasScreen);
        this.treeCanvasPresenter.start();
    }

    private void showNavigation(String branchId) {
        if (branchId == null || branchId.isEmpty() || this.navigatingTo != null) {
            return;
        }
        this.navigatingTo = branchId;
        this.container.background().execute(() -> {
            String topicId = this.container.navigationData().topicOf(branchId);
            this.container.uiThread().post(() -> {
                this.navigatingTo = null;
                if (topicId != null) {
                    this.enterNavigation(topicId, branchId);
                }
            });
        });
    }

    private void enterNavigation(String topicId, String branchId) {
        this.treeBranchId = null;
        this.treeCanvasScreen = null;
        this.treeCanvasPresenter = null;
        this.chatBranchId = null;
        this.chatScreen = null;
        this.chatPresenter = null;
        this.showingSettings = false;
        this.searchScreen = null;
        this.searchPresenter = null;
        this.libraryScreen = null;
        this.libraryPresenter = null;
        this.navigationBranchId = branchId;
        this.navigationScreen = new NavigationScreen((Context)this);
        this.navigationPresenter = new NavigationPresenter(this, this.container.uiThread(), this.container.background(), this.container.navigationData(), topicId, branchId);
        this.navigationScreen.setActions(this.navigationPresenter);
        this.navigationScreen.setHost(new NavigationScreen.Host(){

            @Override
            public void leaveNavigation() {
                MainActivity.this.onBackRequested();
            }

            @Override
            public void openBranch(String selected) {
                MainActivity.this.showChat(selected);
            }

            @Override
            public void openOverview(String selected) {
                MainActivity.this.showTree(selected);
            }
        });
        this.swap((View)this.navigationScreen);
        this.navigationPresenter.start();
    }

    private void showSearch() {
        this.navigationBranchId = null;
        this.navigationScreen = null;
        this.navigationPresenter = null;
        this.treeBranchId = null;
        this.treeCanvasScreen = null;
        this.treeCanvasPresenter = null;
        this.chatBranchId = null;
        this.chatScreen = null;
        this.chatPresenter = null;
        this.showingSettings = false;
        this.libraryScreen = null;
        this.libraryPresenter = null;
        this.searchScreen = new SearchScreen((Context)this);
        this.searchPresenter = new SearchPresenter(this, this.container.uiThread(), this.container.background(), this.container.searchRepository());
        this.searchScreen.setActions(this.searchPresenter);
        this.searchScreen.setHost(this::onBackRequested);
        this.swap((View)this.searchScreen);
        this.searchPresenter.start();
    }

    private void drawSearch(SearchScreen screen, SearchViewState state, SearchPage page) {
        if (this.searchScreen == screen) {
            screen.show(state, page);
        }
    }

    @Override
    public void render(LibraryViewState state) {
        if (this.libraryScreen != null) {
            this.libraryScreen.render(state);
        }
    }

    private void swap(View screen) {
        this.host.removeAllViews();
        this.host.addView(screen, (ViewGroup.LayoutParams)new FrameLayout.LayoutParams(-1, -1));
        this.tabBar.setVisibility(screen == this.homeScreen || screen == this.libraryScreen || screen == this.settingsScreen && this.settingsIsAFrontRoom() ? 0 : 8);
    }

    private boolean settingsIsAFrontRoom() {
        return this.chatBranchId == null;
    }

    private void showTab(Tab tab) {
        switch (tab.ordinal()) {
            case 1: {
                this.showLibrary();
                break;
            }
            case 2: {
                this.showSettings();
                break;
            }
            default: {
                this.showHome();
            }
        }
    }

    private void selectTab(Tab tab) {
        this.currentTab = tab;
        this.markTab(this.tabHome, R.id.tab_home_icon, R.id.tab_home_label, tab == Tab.HOME);
        this.markTab(this.tabLibrary, R.id.tab_library_icon, R.id.tab_library_label, tab == Tab.LIBRARY);
        this.markTab(this.tabSettings, R.id.tab_settings_icon, R.id.tab_settings_label, tab == Tab.SETTINGS);
    }

    private void markTab(View item, int iconId, int labelId, boolean selected) {
        int color2 = this.getColor(selected ? R.color.tl_green_deep : R.color.tl_ink3);
        item.setSelected(selected);
        ((TextView)item.findViewById(labelId)).setTextColor(color2);
        ((ImageView)item.findViewById(iconId)).setImageTintList(ColorStateList.valueOf((int)color2));
    }

    private static enum Tab {
        HOME,
        LIBRARY,
        SETTINGS;

    }
}

