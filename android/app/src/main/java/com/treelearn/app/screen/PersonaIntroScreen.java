package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.treelearn.app.R;
import com.treelearn.core.domain.Persona;

/**
 * The static answer-style introduction (SPEC-2026-09-21, 界面方案).
 *
 * <p>One card per {@link Persona#values()}, each carrying the one-sentence positioning and the same
 * question answered in that style. The page reads and returns; it holds no store handle, no request
 * and no selector, because choosing a style belongs only to the topic's picker.
 */
public final class PersonaIntroScreen extends LinearLayout {

    private Runnable onBack;

    public PersonaIntroScreen(Context context) {
        super(context);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.screen_persona_intro, this, true);
        findViewById(R.id.persona_intro_back).setOnClickListener(view -> {
            if (onBack != null) {
                onBack.run();
            }
        });
        ((TextView) findViewById(R.id.persona_intro_lead)).setText(lead());
        LinearLayout cards = findViewById(R.id.persona_intro_cards);
        LayoutInflater inflater = LayoutInflater.from(context);
        for (Persona persona : Persona.values()) {
            View card = inflater.inflate(R.layout.item_persona_intro_card, cards, false);
            bind(card, persona);
            cards.addView(card);
        }
    }

    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    private void bind(View card, Persona persona) {
        ((TextView) card.findViewById(R.id.persona_intro_name))
                .setText(PersonaUi.nameRes(persona));
        ((TextView) card.findViewById(R.id.persona_intro_tag))
                .setText(PersonaUi.tagRes(persona));
        ((TextView) card.findViewById(R.id.persona_intro_sub))
                .setText(PersonaUi.subRes(persona));
        ((TextView) card.findViewById(R.id.persona_intro_question))
                .setText(R.string.persona_intro_question);
        ((TextView) card.findViewById(R.id.persona_intro_answer))
                .setText(PersonaUi.demoRes(persona));
    }

    /** The prototype bolds 「怎么讲」 in green; the rest of the sentence keeps the body color. */
    private CharSequence lead() {
        String text = getContext().getString(R.string.persona_intro_lead);
        String emphasis = getContext().getString(R.string.persona_intro_lead_emphasis);
        SpannableStringBuilder span = new SpannableStringBuilder(text);
        int start = text.indexOf(emphasis);
        if (start >= 0) {
            int end = start + emphasis.length();
            span.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new ForegroundColorSpan(getContext().getColor(R.color.tl_green_deep)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }
}
