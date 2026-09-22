package com.treelearn.app.artifact;

import com.treelearn.ai.prompt.DeepSeekRequest;
import com.treelearn.core.domain.GenerationModel;

/**
 * The one derived call (technical design 10; prompt design 6): submit an assembled request and hand
 * back the model's raw structured output.
 *
 * <p>It is the narrowest seam that lets a use case be proven without a network. The implementation
 * owns the gateway, the SSE stream and the model choice; everything this task decided — which
 * purpose, which version, which envelope, whether the result may be written back, and what happens
 * when it may not — stays on this side of it.
 */
public interface DerivedCaller {

    /**
     * @return the model's raw output, or null when no usable output was produced (a provider failure,
     *         a refusal, an interrupted stream). Never a partially repaired document: the codec
     *         refuses anything that is not one legal envelope, so "repair it a little" has no
     *         meaning at this boundary
     */
    String call(DeepSeekRequest request, GenerationModel model);
}