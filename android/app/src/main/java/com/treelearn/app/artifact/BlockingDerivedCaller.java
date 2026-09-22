package com.treelearn.app.artifact;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.core.domain.GenerationModel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Adapts the streaming provider boundary to the derived-operation contract. Derived jobs are
 * background work, so waiting here keeps the provider-specific stream out of {@link DerivedOperation}
 * and gives every derived purpose the same failure-closed handling.
 */
public final class BlockingDerivedCaller implements DerivedCaller {

    private static final long TIMEOUT_SECONDS = 90L;

    private final GenerationGateway gateway;

    public BlockingDerivedCaller(GenerationGateway gateway) {
        if (gateway == null) {
            throw new IllegalArgumentException("gateway must not be null");
        }
        this.gateway = gateway;
    }

    @Override
    public String call(DeepSeekRequest request, GenerationModel model) {
        StringBuilder output = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        StreamOutcome[] terminal = new StreamOutcome[1];
        try {
            gateway.open(model, request, new GenerationSink() {
                @Override
                public boolean onAccepted(int httpStatus) {
                    return true;
                }

                @Override
                public void onDelta(String reasoningChunk, String contentChunk) {
                    if (contentChunk != null) {
                        output.append(contentChunk);
                    }
                }

                @Override
                public void onTerminal(StreamOutcome outcome) {
                    terminal[0] = outcome;
                    done.countDown();
                }
            });
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return null;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException unavailable) {
            return null;
        }
        return terminal[0] != null && terminal[0].kind == StreamOutcome.Kind.COMPLETED
                ? output.toString() : null;
    }
}
