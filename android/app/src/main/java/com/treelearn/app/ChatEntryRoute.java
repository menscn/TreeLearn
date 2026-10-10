package com.treelearn.app;

final class ChatEntryRoute {
    interface Restorer {
        void showTab(Destination destination);

        void showCanvas(String branchId);

        void showNavigation(String branchId);
    }

    enum Destination {
        HOME,
        LIBRARY,
        SETTINGS,
        CANVAS,
        NAVIGATION
    }

    private final Destination destination;
    private final String branchId;

    private ChatEntryRoute(Destination destination, String branchId) {
        this.destination = destination;
        this.branchId = branchId;
    }

    static ChatEntryRoute captureIfAbsent(ChatEntryRoute existing, Destination selectedTab,
            boolean libraryVisible, String navigationBranchId, String canvasBranchId) {
        return existing == null
                ? capture(selectedTab, libraryVisible, navigationBranchId, canvasBranchId)
                : existing;
    }

    static ChatEntryRoute capture(Destination selectedTab, boolean libraryVisible,
            String navigationBranchId, String canvasBranchId) {
        if (navigationBranchId != null && !navigationBranchId.isEmpty()) {
            return new ChatEntryRoute(Destination.NAVIGATION, navigationBranchId);
        }
        if (canvasBranchId != null && !canvasBranchId.isEmpty()) {
            return new ChatEntryRoute(Destination.CANVAS, canvasBranchId);
        }
        if (libraryVisible) {
            return new ChatEntryRoute(Destination.LIBRARY, null);
        }
        return new ChatEntryRoute(selectedTab, null);
    }

    Destination destination() {
        return destination;
    }

    String branchId() {
        return branchId;
    }

    void restore(Restorer restorer) {
        switch (destination) {
            case CANVAS:
                restorer.showCanvas(branchId);
                break;
            case NAVIGATION:
                restorer.showNavigation(branchId);
                break;
            default:
                restorer.showTab(destination);
                break;
        }
    }
}
