package com.treelearn.app.forest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.treelearn.core.domain.Persona;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

/**
 * T-2026-10-23-068: the forest page's state machine and its honest degradations.
 *
 * <p>The presenter turns one read into one of four phases, keeps the selection across a refresh,
 * and never invents a number the read did not answer. It holds no View, so every rule here is
 * provable on a plain JVM.
 */
public final class ForestPresenterTest {

    private static String id(String tag) {
        return UUID.nameUUIDFromBytes(tag.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static ForestData.Topic topic(String tag, int branches, int archived, boolean known,
            Persona persona) {
        return new ForestData.Topic(id(tag), id("branch-" + tag), "TOPIC-" + tag, branches,
                archived, 1_756_000_000_000L, persona, known);
    }

    private static ForestData.Snapshot snapshot(ForestData.Topic... topics) {
        return new ForestData.Snapshot(Arrays.asList(topics), 3);
    }

    private static final class FakeData implements ForestData {
        private Snapshot snapshot;
        private RuntimeException failure;

        FakeData(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        void answerWith(Snapshot snapshot) {
            this.snapshot = snapshot;
            this.failure = null;
        }

        @Override
        public Snapshot read() {
            if (failure != null) {
                throw failure;
            }
            return snapshot;
        }
    }

    private static final class RecordingSink implements ForestPresenter.Sink {
        ForestViewState last;
        final List<String> opened = new ArrayList<String>();

        @Override
        public void render(ForestViewState next) {
            last = next;
        }

        @Override
        public void openBranch(String branchId) {
            opened.add(branchId);
        }
    }

    @Test
    public void oneReadBecomesTheReadyForestWithItsRealTotals() {
        RecordingSink sink = new RecordingSink();
        FakeData data = new FakeData(snapshot(
                topic("a", 9, 1, true, Persona.STUDY_BUDDY),
                topic("b", 4, 0, true, Persona.STRICT_MENTOR)));
        ForestPresenter presenter = new ForestPresenter(sink, data);

        presenter.start();

        assertEquals(ForestViewState.Phase.READY, sink.last.phase);
        assertEquals(2, sink.last.trees.size());
        assertNull("nothing is selected until the learner taps a tree", sink.last.selectedTopicId);
        assertEquals(Integer.valueOf(13), sink.last.segmentTotal);
        assertEquals(Integer.valueOf(1), sink.last.archivedTotal);
        assertEquals(Integer.valueOf(3), sink.last.streakDays);
        assertEquals(Persona.STUDY_BUDDY, sink.last.trees.get(0).persona);
        assertNotNull("every ready tree carries a placement", sink.last.trees.get(0).placement);
    }

    @Test
    public void noTopicIsTheEmptyPhaseNotAnEmptyGround() {
        RecordingSink sink = new RecordingSink();
        ForestPresenter presenter = new ForestPresenter(sink,
                new FakeData(new ForestData.Snapshot(
                        Collections.<ForestData.Topic>emptyList(), null)));

        presenter.start();

        assertEquals(ForestViewState.Phase.EMPTY, sink.last.phase);
        assertTrue(sink.last.trees.isEmpty());
    }

    @Test
    public void anUnreadableTopicSetIsTheErrorPhase() {
        RecordingSink sink = new RecordingSink();
        FakeData data = new FakeData(snapshot(topic("a", 2, 0, true, Persona.DEFAULT)));
        data.failWith(new IllegalStateException("SYNTHETIC-BROKEN-TOPICS"));
        ForestPresenter presenter = new ForestPresenter(sink, data);

        presenter.start();

        assertEquals(ForestViewState.Phase.ERROR, sink.last.phase);
        assertTrue(sink.last.trees.isEmpty());
    }

    @Test
    public void anUnknownSegmentCountDrawsOneSegmentAndDropsTheTotals() {
        RecordingSink sink = new RecordingSink();
        ForestPresenter presenter = new ForestPresenter(sink, new FakeData(snapshot(
                topic("a", 9, 1, true, Persona.DEFAULT),
                topic("b", 1, -1, false, Persona.LATE_NIGHT))));

        presenter.start();

        assertEquals(ForestViewState.Phase.READY, sink.last.phase);
        assertEquals(2, sink.last.trees.size());
        assertEquals("the degraded tree is drawn as one segment", 1,
                sink.last.trees.get(1).branchCount);
        assertEquals("the unknown archived count is not shown", -1,
                sink.last.trees.get(1).archivedCount);
        assertNull("a partial sum must not pretend to be the total", sink.last.segmentTotal);
        assertNull("a partial archived sum must be omitted too", sink.last.archivedTotal);
    }

    @Test
    public void aMissingStatsReadHidesOnlyTheStreak() {
        RecordingSink sink = new RecordingSink();
        ForestPresenter presenter = new ForestPresenter(sink,
                new FakeData(new ForestData.Snapshot(
                        Collections.singletonList(topic("a", 3, 0, true, Persona.DEFAULT)), null)));

        presenter.start();

        assertEquals(ForestViewState.Phase.READY, sink.last.phase);
        assertEquals(Integer.valueOf(3), sink.last.segmentTotal);
        assertNull(sink.last.streakDays);
    }

    @Test
    public void tappingATreeSelectsItAndTheArrowOpensItsResumeBranch() {
        RecordingSink sink = new RecordingSink();
        ForestPresenter presenter = new ForestPresenter(sink, new FakeData(snapshot(
                topic("a", 9, 1, true, Persona.DEFAULT),
                topic("b", 4, 0, true, Persona.DEFAULT))));
        presenter.start();

        presenter.onTreeTapped(id("b"));
        assertEquals(id("b"), sink.last.selectedTopicId);

        presenter.onTreeTapped(id("a"));
        assertEquals("a second tap only moves the selection", id("a"), sink.last.selectedTopicId);
        assertTrue("selecting never opens a topic", sink.opened.isEmpty());

        presenter.onSelectedOpened();
        assertEquals(Collections.singletonList(id("branch-a")), sink.opened);
    }

    @Test
    public void theArrowWithoutASelectionDoesNothing() {
        RecordingSink sink = new RecordingSink();
        ForestPresenter presenter = new ForestPresenter(sink,
                new FakeData(snapshot(topic("a", 9, 1, true, Persona.DEFAULT))));
        presenter.start();

        presenter.onSelectedOpened();

        assertTrue(sink.opened.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void anUnknownTreeCannotBecomeTheSelection() {
        RecordingSink sink = new RecordingSink();
        ForestPresenter presenter = new ForestPresenter(sink,
                new FakeData(snapshot(topic("a", 9, 1, true, Persona.DEFAULT))));
        presenter.start();

        presenter.onTreeTapped(id("not-in-the-forest"));
    }

    @Test
    public void aRetryKeepsTheSelectionThatStillExists() {
        RecordingSink sink = new RecordingSink();
        FakeData data = new FakeData(snapshot(
                topic("a", 9, 1, true, Persona.DEFAULT),
                topic("b", 4, 0, true, Persona.DEFAULT)));
        ForestPresenter presenter = new ForestPresenter(sink, data);
        presenter.start();
        presenter.onTreeTapped(id("b"));

        data.answerWith(snapshot(
                topic("b", 4, 0, true, Persona.DEFAULT),
                topic("a", 9, 1, true, Persona.DEFAULT)));
        presenter.refresh();

        assertEquals(ForestViewState.Phase.READY, sink.last.phase);
        assertEquals("the learner's tree stays selected across a re-read", id("b"),
                sink.last.selectedTopicId);
    }

    @Test
    public void retryingAnErrorGoesBackThroughTheSameRead() {
        RecordingSink sink = new RecordingSink();
        FakeData data = new FakeData(snapshot(topic("a", 9, 1, true, Persona.DEFAULT)));
        data.failWith(new IllegalStateException("SYNTHETIC-BROKEN-TOPICS"));
        ForestPresenter presenter = new ForestPresenter(sink, data);
        presenter.start();
        assertEquals(ForestViewState.Phase.ERROR, sink.last.phase);

        data.answerWith(snapshot(topic("a", 9, 1, true, Persona.DEFAULT)));
        presenter.refresh();

        assertEquals(ForestViewState.Phase.READY, sink.last.phase);
        assertEquals(1, sink.last.trees.size());
    }
}
