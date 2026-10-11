package com.treelearn.web.generation;

import com.treelearn.core.domain.Guard;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The per-operation fan-out behind the SSE protocol (task T-2026-10-10-023, contract §7).
 *
 * <p>It owns everything contract §7.1/§7.3 fixes and the HTTP layer (T-026) must not re-decide:
 * a strictly increasing {@code seq} per operation; delivery filtered by {@code operationId}; a
 * bounded replay window so a reconnecting subscriber can be served from {@code Last-Event-ID}; and a
 * single terminal close after which no frame follows. The eight {@link Type} values are §7.2's,
 * unchanged.
 *
 * <p><strong>Bounded and leak-free.</strong> Each operation keeps at most {@link #BUFFER_CAPACITY}
 * frames; the oldest are evicted first, and a subscriber asking from before the retained window is
 * told to re-snapshot rather than handed a discontinuous tail. A terminal event clears the listener
 * list, so a subscriber cannot be retained after the stream it belonged to has ended.
 *
 * <p>The bus carries no conversation *content* of its own: it moves the key/value frames the caller
 * hands it, and a diagnostic toString would name only counts (technical design §15).
 */
public final class GenerationEventBus {

    /** Contract §7.3: the replay window; beyond it a subscriber is told a snapshot is required. */
    public static final int BUFFER_CAPACITY = 512;

    /** Contract §7.2: the eight event types; the four terminal ones close the stream. */
    public enum Type {
        SNAPSHOT,
        REASONING_DELTA,
        ANSWER_DELTA,
        STATE,
        STABLE,
        FAILED,
        INTERRUPTED,
        CANCELED;

        /** True when this event ends the stream (contract §7.1: the server closes after a terminal). */
        public boolean isTerminal() {
            return this == STABLE || this == FAILED || this == INTERRUPTED || this == CANCELED;
        }
    }

    /** One frame. {@code data} is the type-specific JSON field set; {@code operationId}/{@code seq}
     * are separate because §7.1 fixes them on every frame. Values are objects so a nested safe
     * {@code params} object can travel beside flat primitives. */
    public static final class Event {

        public final String operationId;
        public final long seq;
        public final Type type;
        public final Map<String, Object> data;

        Event(String operationId, long seq, Type type, Map<String, ?> data) {
            this.operationId = operationId;
            this.seq = seq;
            this.type = type;
            Map<String, Object> copy = new LinkedHashMap<>();
            if (data != null) {
                copy.putAll(data);
            }
            this.data = Collections.unmodifiableMap(copy);
        }
    }

    /** A subscriber. {@link #onClosed()} is called once, when the stream reaches a terminal event. */
    public interface Listener {

        void onEvent(Event event);

        default void onClosed() {
        }
    }

    /** The result of a replay request: the contiguous tail, and whether a snapshot must precede it. */
    public static final class Replay {

        public final List<Event> events;
        public final boolean needsSnapshot;

        Replay(List<Event> events, boolean needsSnapshot) {
            this.events = Collections.unmodifiableList(events);
            this.needsSnapshot = needsSnapshot;
        }
    }

    /** The result of an atomic subscribe-and-catch-up: the handle plus whether the operation had
     * already closed (so the listener was never registered). */
    public static final class CatchUp {

        public final Subscription subscription;
        public final boolean closed;

        CatchUp(Subscription subscription, boolean closed) {
            this.subscription = subscription;
            this.closed = closed;
        }
    }

    private static final class Channel {

        final List<Listener> listeners = new ArrayList<>();
        final ArrayDeque<Event> buffer = new ArrayDeque<>();
        long seq;
        boolean closed;
    }

    private final Map<String, Channel> channels = new HashMap<>();

    /** Subscribes to one operation's stream; a subscriber arriving after the close is closed at once. */
    public synchronized Subscription subscribe(String operationId, Listener listener) {
        Guard.id(operationId, "operationId");
        Guard.object(listener, "listener");
        Channel channel = channels.computeIfAbsent(operationId, id -> new Channel());
        if (channel.closed) {
            listener.onClosed();
            return new Subscription(this, operationId, listener);
        }
        channel.listeners.add(listener);
        return new Subscription(this, operationId, listener);
    }

    /**
     * Registers {@code listener} and performs its catch-up in one critical section, so a live frame
     * published concurrently can never be sequenced ahead of the snapshot or the replayed frames
     * (contract §7.1: the first frame is a snapshot; §7.3: {@code seq} stays contiguous end to end).
     *
     * <p>When the subscriber has no {@code Last-Event-ID}, names a {@code seq} this operation never
     * used, or asks from before the retained window, a {@code snapshot} is published at the
     * operation's next {@code seq} (carried to every listener, as a normal frame); otherwise the
     * missing buffered frames are delivered straight to the new listener. A subscriber that arrives
     * after the close is closed at once and never registered.
     *
     * @param snapshotData the snapshot payload the caller read before this call; used only when a
     *        snapshot is actually emitted
     */
    public synchronized CatchUp subscribeAndCatchUp(String operationId, Listener listener,
            Long lastSeq, Map<String, ?> snapshotData) {
        Guard.id(operationId, "operationId");
        Guard.object(listener, "listener");
        Channel channel = channels.computeIfAbsent(operationId, id -> new Channel());
        Subscription subscription = new Subscription(this, operationId, listener);
        if (channel.closed) {
            listener.onClosed();
            return new CatchUp(subscription, true);
        }
        channel.listeners.add(listener);
        boolean needsSnapshot = lastSeq == null || lastSeq > channel.seq || channel.buffer.isEmpty()
                || lastSeq + 1 < channel.buffer.peekFirst().seq;
        if (needsSnapshot) {
            publish(operationId, Type.SNAPSHOT, snapshotData);
        } else {
            for (Event event : new ArrayList<>(channel.buffer)) {
                if (event.seq > lastSeq) {
                    listener.onEvent(event);
                }
            }
        }
        return new CatchUp(subscription, false);
    }

    /**
     * Publishes one frame, assigning the next {@code seq}.
     *
     * @return true when the frame was delivered; false when the operation's stream had already
     *         reached a terminal event (contract §7.4: a late frame must not follow a terminal)
     */
    public synchronized boolean publish(String operationId, Type type, Map<String, ?> data) {
        Guard.id(operationId, "operationId");
        Guard.object(type, "type");
        Channel channel = channels.computeIfAbsent(operationId, id -> new Channel());
        if (channel.closed) {
            return false;
        }
        Event event = new Event(operationId, ++channel.seq, type, data);
        channel.buffer.addLast(event);
        while (channel.buffer.size() > BUFFER_CAPACITY) {
            channel.buffer.removeFirst();
        }
        List<Listener> listeners = new ArrayList<>(channel.listeners);
        for (Listener listener : listeners) {
            listener.onEvent(event);
        }
        if (type.isTerminal()) {
            channel.closed = true;
            channel.listeners.clear();
            for (Listener listener : listeners) {
                listener.onClosed();
            }
        }
        return true;
    }

    /** True once the operation has seen a terminal event. */
    public synchronized boolean isClosed(String operationId) {
        Channel channel = channels.get(operationId);
        return channel != null && channel.closed;
    }

    /**
     * The number of live listeners on one operation, or 0 when it has none.
     *
     * <p>Read-only diagnostic used to prove that a subscriber which disconnected was actually released
     * (task T-2026-10-10-026, acceptance "客户端断开后订阅清理"): an HTTP client that goes away must not
     * leave its {@link Listener} attached. It decides nothing about delivery.
     */
    public synchronized int listenerCount(String operationId) {
        Channel channel = channels.get(operationId);
        return channel == null ? 0 : channel.listeners.size();
    }

    /**
     * The frames after {@code lastSeq}, or a "snapshot first" signal when the window no longer
     * contains the frame that follows {@code lastSeq} (contract §7.3).
     */
    public synchronized Replay replaySince(String operationId, long lastSeq) {
        Channel channel = channels.get(operationId);
        if (channel == null || channel.buffer.isEmpty()) {
            return new Replay(Collections.emptyList(), channel == null);
        }
        List<Event> events = new ArrayList<>();
        for (Event event : channel.buffer) {
            if (event.seq > lastSeq) {
                events.add(event);
            }
        }
        boolean gap = lastSeq + 1 < channel.buffer.peekFirst().seq;
        return new Replay(events, gap);
    }

    /** Removes one listener without disturbing the operation's other subscribers. */
    synchronized void unsubscribe(String operationId, Listener listener) {
        Channel channel = channels.get(operationId);
        if (channel != null) {
            channel.listeners.remove(listener);
        }
    }

    /** One subscription handle; {@link #unsubscribe()} is idempotent-safe. */
    public static final class Subscription {

        private final GenerationEventBus bus;
        private final String operationId;
        private final Listener listener;

        Subscription(GenerationEventBus bus, String operationId, Listener listener) {
            this.bus = bus;
            this.operationId = operationId;
            this.listener = listener;
        }

        public void unsubscribe() {
            bus.unsubscribe(operationId, listener);
        }
    }
}
