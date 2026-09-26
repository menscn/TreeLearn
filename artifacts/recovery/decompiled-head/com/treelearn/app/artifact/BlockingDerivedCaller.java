/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.treelearn.ai.gateway.GenerationSink
 *  com.treelearn.ai.gateway.StreamOutcome
 *  com.treelearn.ai.gateway.StreamOutcome$Kind
 *  com.treelearn.ai.prompt.DeepSeekRequest
 *  com.treelearn.core.domain.GenerationModel
 */
package com.treelearn.app.artifact;

import com.treelearn.ai.gateway.GenerationSink;
import com.treelearn.ai.gateway.StreamOutcome;
import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.app.artifact.DerivedCaller;
import com.treelearn.app.generation.GenerationGateway;
import com.treelearn.core.domain.GenerationModel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class BlockingDerivedCaller
implements DerivedCaller {
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
        final StringBuilder output = new StringBuilder();
        final CountDownLatch done = new CountDownLatch(1);
        final StreamOutcome[] terminal = new StreamOutcome[1];
        try {
            this.gateway.open(model, request, new GenerationSink(){

                public boolean onAccepted(int httpStatus) {
                    return true;
                }

                public void onDelta(String reasoningChunk, String contentChunk) {
                    if (contentChunk != null) {
                        output.append(contentChunk);
                    }
                }

                public void onTerminal(StreamOutcome outcome) {
                    terminal[0] = outcome;
                    done.countDown();
                }
            });
            if (!done.await(90L, TimeUnit.SECONDS)) {
                return null;
            }
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
        catch (RuntimeException unavailable) {
            return null;
        }
        return terminal[0] != null && terminal[0].kind == StreamOutcome.Kind.COMPLETED ? output.toString() : null;
    }
}

