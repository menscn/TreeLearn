package com.treelearn.app.voice;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.treelearn.app.voice.TtsEngine;
import java.util.Locale;

public final class SystemTtsEngine
implements TtsEngine {
    private static final String UTTERANCE_ID = "treelearn_voice";
    private final Context context;
    private TextToSpeech tts;
    private TtsEngine.Listener listener;
    private boolean released;

    public SystemTtsEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void init(final TtsEngine.Listener listener) {
        this.listener = listener;
        this.tts = new TextToSpeech(this.context, new TextToSpeech.OnInitListener(){

            public void onInit(int status) {
                if (SystemTtsEngine.this.released) {
                    return;
                }
                if (status != 0 || SystemTtsEngine.this.tts == null) {
                    SystemTtsEngine.this.deliverUnavailable();
                    return;
                }
                int language = SystemTtsEngine.this.tts.setLanguage(Locale.getDefault());
                if (language == -1 || language == -2) {
                    SystemTtsEngine.this.deliverUnavailable();
                } else {
                    listener.onEngineReady();
                }
            }
        });
        this.tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){

            public void onStart(String utteranceId) {
            }

            public void onDone(String utteranceId) {
                if (!SystemTtsEngine.this.released && listener != null) {
                    listener.onPlaybackDone();
                }
            }

            public void onError(String utteranceId) {
                if (!SystemTtsEngine.this.released && listener != null) {
                    listener.onPlaybackError();
                }
            }
        });
    }

    @Override
    public void speak(String text) {
        if (this.tts == null || this.released || text == null || text.isEmpty()) {
            return;
        }
        this.tts.speak((CharSequence)text, 0, null, UTTERANCE_ID);
    }

    @Override
    public void stop() {
        if (this.tts != null && !this.released) {
            this.tts.stop();
        }
    }

    @Override
    public void release() {
        if (this.released) {
            return;
        }
        this.released = true;
        if (this.tts != null) {
            this.tts.stop();
            this.tts.shutdown();
            this.tts = null;
        }
    }

    private void deliverUnavailable() {
        if (this.listener != null) {
            this.listener.onEngineUnavailable();
        }
    }
}

