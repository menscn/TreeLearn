package com.treelearn.app.screen;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.app.forest.ForestPresenter;
import com.treelearn.app.forest.ForestScene;
import com.treelearn.app.forest.ForestViewState;
import com.treelearn.core.domain.Persona;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The forest page (T-068): the slanted ground, the selected tree's slip, the ground's footnote and
 * the four states.
 *
 * <p>It draws; it decides nothing. A tap selects through {@link ForestPresenter}, the slip's arrow
 * asks the same presenter to open the selected tree, and the footnote is a join of the numbers the
 * presenter actually read - a clause whose number is null is left out, never printed as zero.
 *
 * <p>The slip and the footnote are the only two fact surfaces, and they say different things on
 * purpose: the slip is about one tree (segments, archived segments, when, which style), while the
 * footnote is about the whole ground (how many trees, how many segments, the same archived fact and
 * the existing streak). Neither surface offers a management action: the forest only navigates.
 */
public final class ForestScreen extends LinearLayout implements ForestPresenter.Sink {

    /** The ground the trees are drawn on; it reports a tap as a topic id. */
    private final ForestScene scene;
    private final View hint;
    private final View state;
    private final View stateRing;
    private final ImageView stateIcon;
    private final TextView stateTitle;
    private final TextView stateMessage;
    private final TextView stateAction;
    private final View sheet;
    private final TextView sheetTitle;
    private final TextView sheetMeta;
    private final ImageButton sheetOpen;
    private final TextView footnote;

    /** The host's existing conversation door, asked only after the presenter picked a branch. */
    public interface OnBranchOpened {

        void open(String branchId);
    }

    private ForestPresenter presenter;
    private Runnable onBackPressed;
    private Runnable onCreateFirstTopic;
    private OnBranchOpened onBranchOpened;

    public ForestScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_forest, this, true);

        scene = findViewById(R.id.forest_scene);
        hint = findViewById(R.id.forest_hint);
        state = findViewById(R.id.forest_state);
        stateRing = findViewById(R.id.forest_state_ring);
        stateIcon = findViewById(R.id.forest_state_icon);
        stateTitle = findViewById(R.id.forest_state_title);
        stateMessage = findViewById(R.id.forest_state_message);
        stateAction = findViewById(R.id.forest_state_action);
        sheet = findViewById(R.id.forest_sheet);
        sheetTitle = findViewById(R.id.forest_sheet_title);
        sheetMeta = findViewById(R.id.forest_sheet_meta);
        sheetOpen = findViewById(R.id.forest_sheet_open);
        footnote = findViewById(R.id.forest_footnote);

        findViewById(R.id.forest_back).setOnClickListener(view -> {
            if (onBackPressed != null) {
                onBackPressed.run();
            }
        });
        scene.setOnTreeTappedListener(topicId -> {
            if (presenter != null) {
                presenter.onTreeTapped(topicId);
            }
        });
        sheetOpen.setOnClickListener(view -> {
            if (presenter != null) {
                presenter.onSelectedOpened();
            }
        });
        stateAction.setOnClickListener(view -> onStateAction());
    }

    /** The host owns the read; the screen only asks it to select, retry or refresh. */
    public void setPresenter(ForestPresenter presenter) {
        this.presenter = presenter;
    }

    /** The host owns the navigation stack; the screen only reports the back key. */
    public void setOnBackPressed(Runnable onBackPressed) {
        this.onBackPressed = onBackPressed;
    }

    /** The empty ground's way out: the host lands the composer and gives it the caret. */
    public void setOnCreateFirstTopic(Runnable onCreateFirstTopic) {
        this.onCreateFirstTopic = onCreateFirstTopic;
    }

    /** The host's chat door; the presenter has already chosen the branch by the time it lands. */
    public void setOnBranchOpened(OnBranchOpened onBranchOpened) {
        this.onBranchOpened = onBranchOpened;
    }

    @Override
    public void render(ForestViewState next) {
        boolean ready = next.phase == ForestViewState.Phase.READY;
        scene.setTrees(ready ? next.trees : Collections.<ForestViewState.Tree>emptyList(),
                next.selectedTopicId);
        hint.setVisibility(ready && !next.trees.isEmpty() ? VISIBLE : GONE);
        renderState(next);
        renderSheet(next);
        renderFootnote(next);
    }

    /**
     * The tree's slip. The segment count is what the ground drew; the archived clause appears only
     * when there is at least one archived segment (and only when that number was read), so a tree
     * with none does not carry a "0 段已归档" line that would read as a fact worth stating.
     */
    private void renderSheet(ForestViewState next) {
        ForestViewState.Tree selected = next.selected();
        if (selected == null) {
            sheet.setVisibility(GONE);
            return;
        }
        sheet.setVisibility(VISIBLE);
        sheetTitle.setText(selected.title);
        sheetMeta.setText(sheetMetaOf(selected));
        sheetOpen.setContentDescription(getContext().getString(
                R.string.cd_forest_open_topic, selected.title));
    }

    private CharSequence sheetMetaOf(ForestViewState.Tree tree) {
        List<CharSequence> parts = new ArrayList<CharSequence>();
        parts.add(getContext().getString(R.string.forest_meta_branches, tree.branchCount));
        if (tree.archivedCount > 0) {
            parts.add(getContext().getString(R.string.forest_meta_archived, tree.archivedCount));
        }
        parts.add(DateUtils.getRelativeTimeSpanString(tree.lastUsedAt,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        Persona persona = tree.persona == null ? Persona.DEFAULT : tree.persona;
        parts.add(getContext().getString(PersonaUi.nameRes(persona)));
        return join(parts, R.string.forest_meta_separator);
    }

    /**
     * The ground's one line of statistics. The count of trees is always known (it is the list just
     * drawn); the other three arrive only when their own read answered. A failed count read hides
     * both totals and a failed stats read hides only the streak.
     */
    private void renderFootnote(ForestViewState next) {
        if (next.phase != ForestViewState.Phase.READY) {
            footnote.setVisibility(GONE);
            return;
        }
        footnote.setVisibility(VISIBLE);
        List<CharSequence> parts = new ArrayList<CharSequence>();
        parts.add(getContext().getString(R.string.forest_foot_tree, next.trees.size()));
        if (next.segmentTotal != null) {
            parts.add(getContext().getString(R.string.forest_foot_segments,
                    next.segmentTotal.intValue()));
        }
        if (next.archivedTotal != null && next.archivedTotal.intValue() > 0) {
            parts.add(getContext().getString(R.string.forest_foot_archived,
                    next.archivedTotal.intValue()));
        }
        if (next.streakDays != null) {
            parts.add(getContext().getString(R.string.forest_foot_streak,
                    next.streakDays.intValue()));
        }
        footnote.setText(join(parts, R.string.forest_foot_separator));
    }

    private CharSequence join(List<CharSequence> parts, int separatorRes) {
        String separator = " " + getContext().getString(separatorRes) + " ";
        StringBuilder line = new StringBuilder();
        for (CharSequence part : parts) {
            if (line.length() > 0) {
                line.append(separator);
            }
            line.append(part);
        }
        return line.toString();
    }

    /**
     * The three non-ready phases share one pane: loading says one sentence, empty draws the waiting
     * ground and offers the composer, and a failed read offers the same read again. Only the empty
     * phase shows the dashed ring - the ring is the "no tree yet" picture and must not decorate a
     * failure.
     */
    private void renderState(ForestViewState next) {
        switch (next.phase) {
            case LOADING:
                state.setVisibility(VISIBLE);
                stateRing.setVisibility(GONE);
                stateTitle.setText(R.string.forest_loading);
                pillTitle(true);
                stateMessage.setVisibility(GONE);
                stateAction.setVisibility(GONE);
                break;
            case EMPTY:
                state.setVisibility(VISIBLE);
                stateRing.setVisibility(VISIBLE);
                stateIcon.setVisibility(VISIBLE);
                stateTitle.setText(R.string.forest_empty_title);
                pillTitle(false);
                stateMessage.setText(R.string.forest_empty_message);
                stateMessage.setVisibility(VISIBLE);
                stateAction.setText(R.string.forest_empty_action);
                stateAction.setVisibility(VISIBLE);
                break;
            case ERROR:
                state.setVisibility(VISIBLE);
                stateRing.setVisibility(GONE);
                stateTitle.setText(R.string.forest_error_title);
                pillTitle(false);
                stateMessage.setText(R.string.forest_error_message);
                stateMessage.setVisibility(VISIBLE);
                stateAction.setText(R.string.forest_error_action);
                stateAction.setVisibility(VISIBLE);
                break;
            case READY:
            default:
                state.setVisibility(GONE);
                break;
        }
    }

    /** The loading sentence sits on the ground as a paper pill; the two states keep plain serif. */
    private void pillTitle(boolean pill) {
        if (pill) {
            int padH = getResources().getDimensionPixelSize(R.dimen.space_s4);
            int padV = getResources().getDimensionPixelSize(R.dimen.space_s2);
            stateTitle.setBackgroundResource(R.drawable.bg_forest_hint);
            stateTitle.setPadding(padH, padV, padH, padV);
        } else {
            stateTitle.setBackground(null);
            stateTitle.setPadding(0, 0, 0, 0);
        }
    }

    private void onStateAction() {
        if (presenter == null) {
            return;
        }
        switch (presenter.state().phase) {
            case EMPTY:
                if (onCreateFirstTopic != null) {
                    onCreateFirstTopic.run();
                }
                break;
            case ERROR:
                presenter.refresh();
                break;
            default:
                break;
        }
    }

    /**
     * The slip's arrow is the only way into a tree; the presenter resolves it to the selected tree's
     * resume branch and the host opens the existing conversation door.
     */
    @Override
    public void openBranch(String branchId) {
        if (onBranchOpened != null) {
            onBranchOpened.open(branchId);
        }
    }
}
