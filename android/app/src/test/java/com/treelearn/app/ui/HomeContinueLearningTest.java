package com.treelearn.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * T-055 acceptance: the home page's continue-learning row is a function of the store read, and the
 * screen only draws what the state already decided.
 *
 * <p>With history the row shows the most recent topic - the list was ordered newest first, so the
 * first card is the one the learner left off on. With no history the row must not exist at all: the
 * state carries a null, so an empty home cannot render an empty placeholder where the row would be.
 * Both assertions are made on the state the screen would draw, without inflating a view.
 */
public final class HomeContinueLearningTest {

    private TestDoubles.RecordingHomeSink sink;
    private TestDoubles.FakeHomeData data;
    private HomePresenter presenter;

    @Before
    public void setUp() {
        sink = new TestDoubles.RecordingHomeSink();
        data = new TestDoubles.FakeHomeData();
        presenter = new HomePresenter(sink, new TestDoubles.InlineUi(),
                new TestDoubles.InlineExecutor(), new TestDoubles.FakeKeyVault(true),
                data, new TestDoubles.FakeSubmitter(), new TestDoubles.FakeSettingsStore());
    }

    @Test
    public void withHistoryTheContinueRowIsTheMostRecentTopic() {
        data.topics = Arrays.asList(
                TestDoubles.topic(TestDoubles.TOPIC_1, "最近的一棵"),
                TestDoubles.topic(TestDoubles.TOPIC_2, "更早的一棵"));

        presenter.start();

        assertEquals("the load lands in the ready phase with both topics read",
                HomeViewState.Phase.READY, sink.last().phase);
        assertEquals("the continue row names the topic the learner used last",
                "最近的一棵", sink.last().continueTopic().title);
        assertSame("the row resumes the same card the list would have opened",
                TestDoubles.TOPIC_1, sink.last().continueTopic().topicId);
    }

    @Test
    public void withNoHistoryTheContinueRowDoesNotExist() {
        presenter.start();

        assertEquals(HomeViewState.Phase.EMPTY, sink.last().phase);
        assertNull("an empty home must not render a placeholder where the row would be",
                sink.last().continueTopic());
    }
}
