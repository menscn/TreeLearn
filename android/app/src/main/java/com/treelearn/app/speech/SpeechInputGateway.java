package com.treelearn.app.speech;

/**
 * T-095: the speech recognition port (SPEC-081). The presenter and screen never see the platform
 * class; the host owns the implementation and hands results back on the main thread.
 *
 * <p>Recording is never persisted - the recognizer's audio lives and dies inside the platform
 * session (task constraint). Partial results are offered so the [OFF] path can keep what the
 * platform already heard before an error ended the session.</p>
 */
public interface SpeechInputGateway {

    /** Whether this device can recognize speech at all (SPEC-081: no service, no key). */
    boolean isAvailable();

    /** Starts one listen-and-recognize session; results land on the given listener. */
    void start(Listener listener);

    /** Stops the current session without delivering more results. */
    void stop();

    /** The session's outcome; every callback arrives on the main thread. */
    interface Listener {

        /** The platform's partial hypothesis; may arrive many times, may be empty. */
        void onPartial(String text);

        /** The session's final text; exactly one per started session, or none after an error. */
        void onFinal(String text);

        /** A platform error code (RecognitionListener constants); the session is over. */
        void onError(int code);
    }
}
