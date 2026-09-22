package com.treelearn.ai.gateway;

import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wires the gateway to the Android platform: this is the only file in the package that touches
 * {@code android.util} or a socket, so everything else stays verifiable in a plain JVM test.
 *
 * <p>Timeouts are explicit; the read timeout must tolerate a slow reasoning phase. The stream
 * executor is a daemon pool so a long generation is not tied to a screen, and it is deliberately not
 * the scheduler: the application-level concurrency limit belongs to {@code GenerationCoordinator}.
 */
public final class DeepSeekGatewayFactory {

    public static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    public static final int READ_TIMEOUT_MILLIS = 60_000;

    private static final String LOG_TAG = "TreelearnAi";

    private DeepSeekGatewayFactory() {
    }

    public static AiGateway create() {
        return new AiGateway(new HttpsTransport(CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS),
                new JsonChatRequestBody(), new ProviderJson.Deltas(), new ProviderJson.Balance(),
                new AndroidLog(), newStreamExecutor());
    }

    /** One thread per in-flight call; the pool is bounded by the coordinator, not by itself. */
    public static ExecutorService newStreamExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "treelearn-deepseek-stream");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * The diagnostic sink. The gateway only ever passes metadata, so this line can never carry the
     * key, a prompt, a question, an answer or a reasoning text (technical design 15).
     */
    private static final class AndroidLog implements GatewayPorts.Log {

        @Override
        public void event(String name, String details) {
            Log.d(LOG_TAG, "deepseek " + name + " " + details);
        }
    }
}