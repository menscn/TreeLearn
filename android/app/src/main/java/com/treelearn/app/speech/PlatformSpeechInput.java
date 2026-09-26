package com.treelearn.app.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

/**
 * T-095: the platform SpeechRecognizer behind {@link SpeechInputGateway} (SPEC-081: 系统 STT,
 * 零新依赖). One recognizer per host context, reused across sessions; the audio is the platform's
 * own and is never written anywhere.
 *
 * <p>RecognitionListener delivers on the main thread, so the gateway's main-thread promise is the
 * platform's own guarantee, not an extra hop. Errors map straight through - the screen's policy
 * decides what a code means, this class only forwards it.</p>
 */
public final class PlatformSpeechInput implements SpeechInputGateway {

    private final Context context;
    private SpeechRecognizer recognizer;

    public PlatformSpeechInput(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    @Override
    public void start(final Listener listener) {
        stop();
        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                listener.onError(error);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    // The platform ended the session with nothing - the [OFF] complete-silence
                    // case; the presenter's blank-check does the quiet fall-back.
                    listener.onFinal("");
                    return;
                }
                listener.onFinal(matches.get(0));
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches =
                        partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    listener.onPartial(matches.get(0));
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizer.startListening(intent);
    }

    @Override
    public void stop() {
        if (recognizer != null) {
            recognizer.stopListening();
            recognizer.destroy();
            recognizer = null;
        }
    }
}
