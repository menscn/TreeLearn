package com.treelearn.web.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.web.generation.GenerationEventBus.Event;
import com.treelearn.web.generation.GenerationEventBus.Listener;
import com.treelearn.web.generation.GenerationEventBus.Replay;
import com.treelearn.web.generation.GenerationEventBus.Subscription;
import com.treelearn.web.generation.GenerationEventBus.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T-2026-10-10-023: the in-memory fan-out behind the SSE protocol (contract §7). The bus owns the
 * seq counter, the per-operation filter, the bounded replay window and the terminal close; the HTTP
 * layer (T-025) only serialises what it is handed.
 */
class GenerationEventBusTest {

    private static String operation() {
        return UUID.randomUUID().toString();
    }

    /** Records everything one subscription is told, so a test can assert order and closure. */
    private static final class Recorder implements Listener {

        final List<Event> events = new ArrayList<>();
        int closedCount;

        @Override
        public void onEvent(Event event) {
            events.add(event);
        }

        @Override
        public void onClosed() {
            closedCount++;
        }
    }

    @Test
    void seqIsMonotonicPerOperationAndTypesFollowThePublishOrder() {
        GenerationEventBus bus = new GenerationEventBus();
        String op = operation();
        Recorder recorder = new Recorder();
        bus.subscribe(op, recorder);

        assertTrue(bus.publish(op, Type.SNAPSHOT, Map.of("state", "STREAMING_ANSWER")));
        assertTrue(bus.publish(op, Type.REASONING_DELTA, Map.of("delta", "想")));
        assertTrue(bus.publish(op, Type.ANSWER_DELTA, Map.of("delta", "答")));

        assertEquals(List.of(1L, 2L, 3L), recorder.events.stream().map(e -> e.seq).toList(),
                "seq must be a strictly increasing positive sequence");
        assertEquals(List.of(Type.SNAPSHOT, Type.REASONING_DELTA, Type.ANSWER_DELTA),
                recorder.events.stream().map(e -> e.type).toList(),
                "the event type is carried unchanged, in publish order");
        assertEquals(op, recorder.events.get(0).operationId);
        assertEquals("答", recorder.events.get(2).data.get("delta"));
    }

    @Test
    void anOperationOnlySeesItsOwnEvents() {
        GenerationEventBus bus = new GenerationEventBus();
        String a = operation();
        String b = operation();
        Recorder recorder = new Recorder();
        bus.subscribe(a, recorder);

        bus.publish(b, Type.ANSWER_DELTA, Map.of("delta", "b"));
        bus.publish(a, Type.ANSWER_DELTA, Map.of("delta", "a"));

        assertEquals(1, recorder.events.size(), "a late event of another operation must be filtered");
        assertEquals("a", recorder.events.get(0).data.get("delta"));
    }

    @Test
    void theBufferIsBoundedAndReplayReportsTheGap() {
        GenerationEventBus bus = new GenerationEventBus();
        String op = operation();
        int total = GenerationEventBus.BUFFER_CAPACITY + 88;
        for (int index = 0; index < total; index++) {
            bus.publish(op, Type.ANSWER_DELTA, Map.of("delta", Integer.toString(index)));
        }

        Replay fromStart = bus.replaySince(op, 0);
        assertEquals(GenerationEventBus.BUFFER_CAPACITY, fromStart.events.size(),
                "the buffer keeps at most BUFFER_CAPACITY frames per operation");
        assertEquals(total - GenerationEventBus.BUFFER_CAPACITY + 1L, fromStart.events.get(0).seq,
                "the oldest frame is evicted first");
        assertTrue(fromStart.needsSnapshot,
                "asking from before the retained window is a gap, so the subscriber is told to "
                        + "re-snapshot");

        Replay inWindow = bus.replaySince(op, total - 12L);
        assertFalse(inWindow.needsSnapshot, "a contiguous resume needs no snapshot");
        assertEquals(12, inWindow.events.size());
    }

    @Test
    void aTerminalEventClosesTheStreamAndLaterEventsAreIgnored() {
        GenerationEventBus bus = new GenerationEventBus();
        String op = operation();
        Recorder recorder = new Recorder();
        bus.subscribe(op, recorder);

        bus.publish(op, Type.ANSWER_DELTA, Map.of("delta", "答"));
        assertFalse(bus.isClosed(op));
        assertTrue(bus.publish(op, Type.STABLE, Map.of("revision", "r-1")));

        assertTrue(recorder.events.stream().anyMatch(e -> e.type == Type.STABLE),
                "the terminal event is delivered before the stream closes");
        assertEquals(1, recorder.closedCount, "the stream is closed exactly once");
        assertTrue(bus.isClosed(op));
        assertFalse(bus.publish(op, Type.ANSWER_DELTA, Map.of("delta", "late")),
                "no frame may follow a terminal event");
        assertEquals(2, recorder.events.size());

        Recorder late = new Recorder();
        bus.subscribe(op, late);
        assertEquals(0, late.events.size());
        assertEquals(1, late.closedCount, "a subscriber arriving after the close is closed at once");
    }

    @Test
    void unsubscribingRemovesTheListenerWithoutAffectingTheOthers() {
        GenerationEventBus bus = new GenerationEventBus();
        String op = operation();
        Recorder kept = new Recorder();
        Recorder removed = new Recorder();
        bus.subscribe(op, kept);
        Subscription subscription = bus.subscribe(op, removed);

        subscription.unsubscribe();
        bus.publish(op, Type.STATE, Map.of("state", "SUBMITTING"));

        assertEquals(1, kept.events.size());
        assertEquals(0, removed.events.size(), "an unsubscribed listener must receive nothing more");
    }
}
