package com.treelearn.app.wiring;

import com.treelearn.app.navigation.NavigationData;
import com.treelearn.app.navigation.SqliteNavigationStore;
import com.treelearn.core.domain.Guard;

import java.util.List;

/**
 * The navigation page's real data path: one port, one store, no rule of its own.
 *
 * <p><strong>This class decides nothing.</strong> Which branches are visible, which one is selected,
 * whether the root may be archived, what a deletion covers and when a confirmation is offered are all
 * decided by {@code NavigationPresenter} against the {@link NavigationData} contract, and the archive
 * and delete writes are {@link SqliteNavigationStore}'s. Nothing is re-derived here: a second copy of
 * "what is visible" is exactly how the list and the canvas would come to disagree, and both read the
 * same {@code branches} rows.
 *
 * <p><strong>What it is for.</strong> It is the single production construction point of the navigation
 * store - the host asks the container for a {@link NavigationData} and never builds a store, a database
 * helper or a coordinator itself, which is the same shape {@link TreeCanvasWiring} gives the canvas. The
 * store is built once and handed out, so every visit to the page reads the connection the rest of the
 * app is already on.
 *
 * <p><strong>Why it does not implement {@code NavigationActions}.</strong> That contract is the
 * screen's gestures and is satisfied by {@code NavigationPresenter}, which the host builds per visit
 * with the sink and the executor only it can supply. Implementing it here would put a second
 * implementation of the archive and delete decisions next to the presenter's, so this class stays a
 * delegation.
 */
public final class NavigationWiring implements NavigationData {

    private final NavigationData store;

    public NavigationWiring(NavigationData store) {
        this.store = Guard.object(store, "store");
    }

    @Override
    public String topicOf(String branchId) {
        return store.topicOf(branchId);
    }

    @Override
    public Snapshot load(String topicId) {
        return store.load(topicId);
    }

    @Override
    public Summary summary(String branchId) {
        return store.summary(branchId);
    }

    @Override
    public void setArchived(String branchId, boolean archived, long now) {
        store.setArchived(branchId, archived, now);
    }

    @Override
    public int cancelInFlight(List<String> branchIds) {
        return store.cancelInFlight(branchIds);
    }

    @Override
    public void delete(Plan plan) {
        store.delete(plan);
    }
}
