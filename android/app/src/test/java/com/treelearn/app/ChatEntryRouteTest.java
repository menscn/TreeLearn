package com.treelearn.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ChatEntryRouteTest {

    private static final class RecordingRestorer implements ChatEntryRoute.Restorer {
        private ChatEntryRoute.Destination tab;
        private String canvasBranchId;
        private String navigationBranchId;

        @Override
        public void showTab(ChatEntryRoute.Destination destination) {
            tab = destination;
        }

        @Override
        public void showCanvas(String branchId) {
            canvasBranchId = branchId;
        }

        @Override
        public void showNavigation(String branchId) {
            navigationBranchId = branchId;
        }
    }

    @Test
    public void navigationOriginKeepsItsBranchForRootReturn() {
        ChatEntryRoute route = ChatEntryRoute.capture(ChatEntryRoute.Destination.HOME,
                false, "navigation-branch", null);
        RecordingRestorer restorer = new RecordingRestorer();

        route.restore(restorer);

        assertEquals("navigation-branch", restorer.navigationBranchId);
    }

    @Test
    public void canvasOriginKeepsItsBranchForRootReturn() {
        ChatEntryRoute route = ChatEntryRoute.capture(ChatEntryRoute.Destination.HOME,
                false, null, "canvas-branch");
        RecordingRestorer restorer = new RecordingRestorer();

        route.restore(restorer);

        assertEquals("canvas-branch", restorer.canvasBranchId);
    }

    @Test
    public void libraryOriginReturnsToLibraryInsteadOfSelectedTab() {
        ChatEntryRoute route = ChatEntryRoute.capture(ChatEntryRoute.Destination.HOME,
                true, null, null);
        RecordingRestorer restorer = new RecordingRestorer();

        route.restore(restorer);

        assertEquals(ChatEntryRoute.Destination.LIBRARY, restorer.tab);
    }

    @Test
    public void firstLevelOriginUsesItsSelectedTab() {
        ChatEntryRoute route = ChatEntryRoute.capture(ChatEntryRoute.Destination.SETTINGS,
                false, null, null);
        RecordingRestorer restorer = new RecordingRestorer();

        route.restore(restorer);

        assertEquals(ChatEntryRoute.Destination.SETTINGS, restorer.tab);
    }

    @Test
    public void homeOriginRestoresHome() {
        ChatEntryRoute route = ChatEntryRoute.capture(ChatEntryRoute.Destination.HOME,
                false, null, null);
        RecordingRestorer restorer = new RecordingRestorer();

        route.restore(restorer);

        assertEquals(ChatEntryRoute.Destination.HOME, restorer.tab);
    }

    @Test
    public void openingAncestorBranchesDoesNotReplaceTheOriginalEntry() {
        ChatEntryRoute original = ChatEntryRoute.capture(ChatEntryRoute.Destination.HOME,
                true, null, null);

        ChatEntryRoute afterOpeningParent = ChatEntryRoute.captureIfAbsent(original,
                ChatEntryRoute.Destination.HOME, false, null, null);
        RecordingRestorer restorer = new RecordingRestorer();
        afterOpeningParent.restore(restorer);

        assertEquals("the first source remains the root-return destination", original,
                afterOpeningParent);
        assertEquals(ChatEntryRoute.Destination.LIBRARY, restorer.tab);
    }
}
