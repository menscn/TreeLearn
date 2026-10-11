package com.treelearn.web.api;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationState;
import com.treelearn.web.api.dto.ErrorDtos;
import com.treelearn.web.api.dto.MessageDtos;
import com.treelearn.web.generation.GenerationEventBus;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.OperationStore;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 生成操作的 SSE 事件流端点（任务 T-2026-10-10-026；契约 {@code web/contracts/api.md} §7）。
 *
 * <p><strong>它只做三件契约规定的事，不重定语义。</strong>
 * <ol>
 *   <li>订阅时先发 {@code snapshot}：内容取自 {@link OperationStore} 的持久化事实（{@code state}、
 *       {@code reasoningContent}、{@code answerContent}；终态另带修订/错误信息）。seq 由总线分配，
 *       因此不会出现 snapshot 之后 deltas 的 seq 倒退（§7.1）。</li>
 *   <li>实时转发总线帧：{@code id:}=seq、{@code event:}=类型、{@code data:}=JSON；STABLE 帧把总线携带的
 *       {@code revisionId} 展开为契约 §7.2 的完整 {@code revision} 对象（消费者按对象读取）。终态帧写出后
 *       关闭流（§7.1）。</li>
 *   <li>{@code Last-Event-ID}：缓冲内→补发缺失帧后继续；早于缓冲起点或未知操作→先补 {@code snapshot}；
 *       seq 大于总线尾号（不属于本操作的伪 id）→同样先补 {@code snapshot}（§7.3）。</li>
 * </ol>
 *
 * <p><strong>快照的 seq 与总线序列一致。</strong>非终态订阅的快照通过 {@link GenerationEventBus#publish}
 * 进入总线，占用其下一个 seq；已终态订阅（总线已关闭，无法再 publish）用总线尾号写出终态快照，随后按
 * 其 {@code state} 补一个对应的终态事件帧（stable/failed/interrupted/canceled），再关流——前端只把终态
 * <em>事件</em>当作终止信号，仅有 snapshot 会让它拿不到 revision 并判 closed-before-terminal。
 * 心跳为注释行、默认 15s、可用测试属性注入短间隔；客户端断开由 emitter 回调取消总线订阅（无泄漏）。
 * 未知操作以 §3.1 错误封装返回 404，不返回裸异常。
 */
@RestController
public class OperationEventsController {

    private static final Logger LOG = LoggerFactory.getLogger(OperationEventsController.class);

    /** 心跳间隔的配置属性；生产默认 15s（契约 §7.1），测试注入短间隔，不改生产默认。 */
    static final String KEEPALIVE_MILLIS_PROPERTY = "treelearn.sse.keepalive-millis";
    private static final long DEFAULT_KEEPALIVE_MILLIS = 15_000L;

    private final OperationStore operations;
    private final AnswerRevisionStore revisions;
    private final GenerationEventBus bus;
    private final long keepaliveMillis;
    private final ScheduledExecutorService keepalives;

    public OperationEventsController(OperationStore operations, AnswerRevisionStore revisions,
            GenerationEventBus bus,
            @Value("${" + KEEPALIVE_MILLIS_PROPERTY + ":15000}") long keepaliveMillis) {
        this.operations = operations;
        this.revisions = revisions;
        this.bus = bus;
        this.keepaliveMillis = keepaliveMillis > 0 ? keepaliveMillis : DEFAULT_KEEPALIVE_MILLIS;
        this.keepalives = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "sse-keepalive");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        keepalives.shutdownNow();
    }

    @GetMapping(value = "/api/operations/{operationId}/events",
            produces = "text/event-stream;charset=UTF-8")
    public SseEmitter events(@PathVariable String operationId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        OperationStore.Operation operation = operations.byId(operationId)
                .orElseThrow(() -> new OperationNotFoundException(operationId));

        SseEmitter emitter = new SseEmitter(0L);
        Stream stream = new Stream(operationId, emitter);

        if (operation.state.isTerminal()) {
            stream.writeTerminalSnapshot();
            stream.finish();
            return emitter;
        }

        stream.subscribe(bus, lastEventId);
        if (!stream.isFinished()) {
            stream.startKeepalive();
        }
        return emitter;
    }

    /** The highest seq the bus has published for one operation, or 0 when it has published none. */
    private long tailSeq(String operationId) {
        var buffered = bus.replaySince(operationId, 0).events;
        return buffered.isEmpty() ? 0 : buffered.get(buffered.size() - 1).seq;
    }

    private static Long parseSeq(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(lastEventId.trim());
            return value < 0 ? 0L : value;
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private static MessageDtos.AnswerRevision revisionFrame(AnswerRevision revision) {
        return new MessageDtos.AnswerRevision(revision.id, revision.messageId, revision.revisionNo,
                revision.reasoningContent, revision.answerContent, revision.model.wireId,
                revision.thinkingEnabled, revision.promptPolicyVersion, revision.createdAt);
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    /** 未知操作：交给 {@link #handleNotFound} 转成 §3.1 错误封装。 */
    static final class OperationNotFoundException extends RuntimeException {

        OperationNotFoundException(String operationId) {
            super("no generation operation carries id " + operationId);
        }
    }

    @ExceptionHandler(OperationNotFoundException.class)
    ResponseEntity<ErrorDtos.ApiErrorResponse> handleNotFound(OperationNotFoundException failure) {
        ErrorDtos.ApiError error = new ErrorDtos.ApiError("NOT_FOUND",
                Map.of("resource", "operation"), false);
        ErrorDtos.ApiErrorResponse body = new ErrorDtos.ApiErrorResponse(error,
                UUID.randomUUID().toString());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * 一次订阅的可写流：串行化所有帧写出（请求线程补发、总线线程实时、心跳线程），按 seq 去重，
     * 终态/断开时取消订阅与心跳。所有总线调用都发生在**不持有流锁**的位置，避免总线锁与流锁互相等待。
     */
    private final class Stream implements GenerationEventBus.Listener {

        private final String operationId;
        private final SseEmitter emitter;
        private final Object lock = new Object();
        private long lastSeq;
        private boolean finished;
        private GenerationEventBus.Subscription subscription;
        private ScheduledFuture<?> keepalive;

        Stream(String operationId, SseEmitter emitter) {
            this.operationId = operationId;
            this.emitter = emitter;
            emitter.onCompletion(this::finish);
            emitter.onTimeout(this::finish);
            emitter.onError(failure -> finish());
        }

        /**
         * 订阅并补帧：先读好快照内容，再把「注册监听 + 补发快照/重放缺失帧」交给总线在同一临界区完成
         * （契约 §7.1 首帧必为 snapshot、§7.3 seq 连续），使实时帧无法抢在补发帧之前被写出，从而
         * 不会被客户端按 seq 当作重复/乱序丢弃。
         */
        void subscribe(GenerationEventBus events, String lastEventId) {
            Map<String, Object> snapshot = snapshotData();
            if (snapshot == null) {
                finish();
                return;
            }
            GenerationEventBus.CatchUp catchUp = events.subscribeAndCatchUp(operationId, this,
                    parseSeq(lastEventId), snapshot);
            subscription = catchUp.subscription;
        }

        /** 订阅时的快照内容（持久化事实）；操作行已不存在时返回 null。 */
        private Map<String, Object> snapshotData() {
            OperationStore.Operation current = operations.byId(operationId).orElse(null);
            if (current == null) {
                return null;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("state", current.state.name());
            data.put("reasoningContent", emptyIfNull(current.partialReasoning));
            data.put("answerContent", emptyIfNull(current.partialAnswer));
            return data;
        }

        boolean isFinished() {
            synchronized (lock) {
                return finished;
            }
        }

        @Override
        public void onEvent(GenerationEventBus.Event event) {
            if (writeBusEvent(event)) {
                finish();
            }
        }

        @Override
        public void onClosed() {
            if (isFinished()) {
                return;
            }
            writeTerminalSnapshot();
            finish();
        }

        /** Writes one bus frame; returns true when the terminal frame was written or the client is gone. */
        private boolean writeBusEvent(GenerationEventBus.Event event) {
            synchronized (lock) {
                if (finished || event.seq <= lastSeq) {
                    return false;
                }
                Map<String, Object> body = new LinkedHashMap<>();
                if (event.type == GenerationEventBus.Type.STABLE) {
                    Object revisionId = event.data.get("revisionId");
                    if (revisionId != null) {
                        revisions.get(revisionId.toString()).ifPresent(revision ->
                                body.put("revision", revisionFrame(revision)));
                    }
                } else {
                    body.putAll(event.data);
                    if (event.type == GenerationEventBus.Type.FAILED) {
                        body.putIfAbsent("params", Map.of());
                    }
                }
                boolean sent = writeFrame(event.seq, event.type, body);
                return !sent || event.type.isTerminal();
            }
        }

        /**
         * 已终态订阅或竞态关流时：从持久化事实写一个终态快照（STABLE 附带完整修订、FAILED 附带
         * errorCode/params），随后按 state 补一个对应终态事件帧（stable/failed/interrupted/canceled），
         * seq 与总线序列一致，再关流。前端只把终态<b>事件</b>当作终止信号，因此仅有 snapshot 会让它
         * 拿到 revision 失败并判 closed-before-terminal。
         */
        void writeTerminalSnapshot() {
            OperationStore.Operation operation = operations.byId(operationId).orElse(null);
            if (operation == null) {
                return;
            }
            long tail = tailSeq(operationId);
            synchronized (lock) {
                if (finished) {
                    return;
                }
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("state", operation.state.name());
                body.put("reasoningContent", emptyIfNull(operation.partialReasoning));
                body.put("answerContent", emptyIfNull(operation.partialAnswer));
                if (operation.state == GenerationState.STABLE) {
                    revisions.findActiveForMessage(operation.messageId).ifPresent(revision ->
                            body.put("revision", revisionFrame(revision)));
                }
                if (operation.state == GenerationState.FAILED && operation.errorCode != null) {
                    body.put("errorCode", operation.errorCode);
                    body.put("params", Map.of());
                }
                long seq = Math.max(lastSeq + 1, Math.max(1, tail));
                writeFrame(seq, GenerationEventBus.Type.SNAPSHOT, body);
                writeTerminalFrame(operation);
            }
        }

        /**
         * 终态快照之后补发一个终态事件帧：STABLE→stable（附完整修订）、FAILED→failed（errorCode/params）、
         * INTERRUPTED→interrupted、CANCELED→canceled（契约 §7.2）。仅当操作确实处于终态时补发。
         */
        private void writeTerminalFrame(OperationStore.Operation operation) {
            GenerationEventBus.Type type;
            Map<String, Object> body = new LinkedHashMap<>();
            switch (operation.state) {
                case STABLE -> {
                    type = GenerationEventBus.Type.STABLE;
                    revisions.findActiveForMessage(operation.messageId).ifPresent(revision ->
                            body.put("revision", revisionFrame(revision)));
                }
                case FAILED -> {
                    type = GenerationEventBus.Type.FAILED;
                    if (operation.errorCode != null) {
                        body.put("errorCode", operation.errorCode);
                    }
                    // 与 §3.3 一致：失败帧携带 params。持久化只保留 errorCode，供应方状态码不可重建，
                    // 故这里给最小参数集（空对象）；带 httpStatus 的现场帧由协调器直接给出。
                    body.put("params", Map.of());
                }
                case INTERRUPTED -> type = GenerationEventBus.Type.INTERRUPTED;
                case CANCELED -> type = GenerationEventBus.Type.CANCELED;
                default -> {
                    return;
                }
            }
            writeFrame(lastSeq + 1, type, body);
        }

        /** Serialises one frame and sends it; a dead client is reported by the boolean. */
        private boolean writeFrame(long seq, GenerationEventBus.Type type, Map<String, Object> body) {
            lastSeq = seq;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("operationId", operationId);
            payload.put("seq", seq);
            payload.putAll(body);
            try {
                emitter.send(SseEmitter.event()
                        .id(Long.toString(seq))
                        .name(type.name().toLowerCase(Locale.ROOT))
                        .data(payload, MediaType.APPLICATION_JSON));
                return true;
            } catch (IOException | IllegalStateException failure) {
                LOG.debug("operation stream write failed: operation={} seq={}", operationId, seq);
                return false;
            }
        }

        void startKeepalive() {
            synchronized (lock) {
                if (finished) {
                    return;
                }
                keepalive = keepalives.scheduleWithFixedDelay(this::heartbeat, keepaliveMillis,
                        keepaliveMillis, TimeUnit.MILLISECONDS);
            }
        }

        private void heartbeat() {
            boolean sent;
            synchronized (lock) {
                if (finished) {
                    return;
                }
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                    sent = true;
                } catch (IOException | IllegalStateException failure) {
                    sent = false;
                }
            }
            if (!sent) {
                finish();
            }
        }

        /** Ends the stream once: cancels the heartbeat, releases the bus subscription, completes the emitter. */
        void finish() {
            GenerationEventBus.Subscription active;
            synchronized (lock) {
                if (finished) {
                    return;
                }
                finished = true;
                if (keepalive != null) {
                    keepalive.cancel(false);
                    keepalive = null;
                }
                active = subscription;
                subscription = null;
            }
            if (active != null) {
                active.unsubscribe();
            }
            try {
                emitter.complete();
            } catch (RuntimeException alreadyCompleted) {
                // onCompletion/timeout 等回调可能已结束该 emitter：重复完成是幂等的。
            }
        }
    }
}
