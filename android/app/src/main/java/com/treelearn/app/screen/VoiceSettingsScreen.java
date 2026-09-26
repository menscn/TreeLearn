package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.VoiceSettingsPresenter;

public final class VoiceSettingsScreen
extends LinearLayout
implements VoiceSettingsPresenter.Sink {
    private static final String MARK = "\u2713";
    private final TextView notice;
    private final LinearLayout rows;
    private VoiceSettingsPresenter actions;
    private Runnable onBack;

    public VoiceSettingsScreen(Context context) {
        super(context);
        this.setOrientation(LinearLayout.VERTICAL);
        int gutter = this.dimen(R.dimen.page_gutter);
        this.setPadding(gutter, 0, gutter, this.dimen(R.dimen.space_s8));
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(16);
        header.setPadding(0, this.dimen(R.dimen.space_s4), 0, 0);
        ImageView back = new ImageView(context);
        back.setImageResource(R.drawable.ic_nav_back);
        back.setImageTintList(ColorStateList.valueOf((int)this.color(R.color.tl_green_deep)));
        back.setContentDescription((CharSequence)context.getString(R.string.cd_voice_back));
        back.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(this.dimen(R.dimen.touch_min), this.dimen(R.dimen.touch_min)));
        back.setOnClickListener(view -> {
            if (this.onBack != null) {
                this.onBack.run();
            }
        });
        header.addView((View)back);
        TextView title = this.tokenText(R.dimen.text_h1, R.color.tl_ink);
        title.setText(R.string.voice_title);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        header.addView((View)title);
        this.addView((View)header);
        this.notice = this.tokenText(R.dimen.text_sm, R.color.tl_amber);
        this.notice.setVisibility(View.GONE);
        this.addView((View)this.notice);
        this.rows = new LinearLayout(context);
        this.rows.setOrientation(LinearLayout.VERTICAL);
        this.addView((View)this.rows);
    }

    public void setActions(VoiceSettingsPresenter actions) {
        this.actions = actions;
    }

    public void setOnBackPressed(Runnable onBack) {
        this.onBack = onBack;
    }

    @Override
    public void render(VoiceSettingsPresenter.State state) {
        if (state.notice == VoiceSettingsPresenter.Notice.NONE) {
            this.notice.setVisibility(View.GONE);
        } else {
            this.notice.setText(state.notice == VoiceSettingsPresenter.Notice.NO_ENGINE ? R.string.voice_notice_no_engine : R.string.voice_notice_playback_error);
            this.notice.setTextColor(this.color(state.notice == VoiceSettingsPresenter.Notice.NO_ENGINE ? R.color.tl_amber : R.color.tl_red));
            this.notice.setVisibility(View.VISIBLE);
        }
        this.rows.removeAllViews();
        this.rows.addView((View)this.sectionLabel(R.string.voice_engine_section));
        this.rows.addView(this.providerCard(state));
        this.rows.addView((View)this.sectionLabel(R.string.voice_audition_section));
        if (state.engineReady) {
            this.rows.addView(this.auditionCard(state));
        }
    }

    private View providerCard(VoiceSettingsPresenter.State state) {
        boolean selected = "system".equals(state.provider);
        LinearLayout card = this.card();
        LinearLayout names = new LinearLayout(this.getContext());
        names.setOrientation(LinearLayout.VERTICAL);
        names.setLayoutParams((ViewGroup.LayoutParams)new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView name = this.tokenText(R.dimen.text_h2, R.color.tl_ink);
        name.setText(R.string.voice_provider_system);
        names.addView((View)name);
        TextView sub = this.tokenText(R.dimen.text_cap, R.color.tl_ink3);
        sub.setText(R.string.voice_provider_system_sub);
        names.addView((View)sub);
        card.addView((View)names);
        TextView mark = this.tokenText(R.dimen.text_h2, R.color.tl_green_deep);
        mark.setText((CharSequence)(selected ? MARK : ""));
        card.addView((View)mark);
        card.setOnClickListener(view -> {
            if (this.actions != null && !selected) {
                this.actions.onProviderSelected("system");
            }
        });
        return card;
    }

    private View auditionCard(VoiceSettingsPresenter.State state) {
        LinearLayout card = new LinearLayout(this.getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setMinimumHeight(this.dimen(R.dimen.touch_min));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation((float)this.dimen(R.dimen.card_elevation));
        int pad = this.dimen(R.dimen.space_s4);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams strip = new LinearLayout.LayoutParams(-1, -2);
        strip.bottomMargin = this.dimen(R.dimen.space_s2);
        card.setLayoutParams((ViewGroup.LayoutParams)strip);
        TextView sentence = this.tokenText(R.dimen.text_body, R.color.tl_ink2);
        sentence.setText(R.string.voice_audition_text);
        card.addView((View)sentence);
        Button audition = new Button(this.getContext());
        boolean speaking = state.phase == VoiceSettingsPresenter.Phase.SPEAKING;
        audition.setText(speaking ? R.string.voice_audition_stop : R.string.voice_audition_play);
        audition.setContentDescription((CharSequence)this.getContext().getString(R.string.cd_voice_audition));
        audition.setBackgroundResource(speaking ? R.drawable.bg_button_danger : R.drawable.bg_button_primary);
        audition.setTextColor(this.color(speaking ? R.color.tl_red : R.color.tl_surface));
        audition.setMinimumHeight(this.dimen(R.dimen.touch_min));
        audition.setMinimumWidth(this.dimen(R.dimen.touch_min));
        audition.setOnClickListener(view -> {
            if (this.actions != null) {
                this.actions.onAuditionPressed();
            }
        });
        LinearLayout.LayoutParams buttonRow = new LinearLayout.LayoutParams(-2, -2);
        buttonRow.topMargin = this.dimen(R.dimen.space_s3);
        audition.setLayoutParams((ViewGroup.LayoutParams)buttonRow);
        card.addView((View)audition);
        return card;
    }

    private TextView sectionLabel(int textRes) {
        TextView label = this.tokenText(R.dimen.text_cap, R.color.tl_ink3);
        label.setText(textRes);
        int top = this.dimen(R.dimen.space_s6);
        label.setPadding(0, top, 0, 0);
        return label;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this.getContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(16);
        card.setMinimumHeight(this.dimen(R.dimen.touch_min));
        card.setBackgroundResource(R.drawable.bg_card);
        card.setElevation((float)this.dimen(R.dimen.card_elevation));
        int gutter = this.dimen(R.dimen.space_s4);
        card.setPadding(gutter, gutter, gutter, gutter);
        LinearLayout.LayoutParams strip = new LinearLayout.LayoutParams(-1, -2);
        strip.bottomMargin = this.dimen(R.dimen.space_s2);
        card.setLayoutParams((ViewGroup.LayoutParams)strip);
        return card;
    }

    private TextView tokenText(int sizeRes, int colorRes) {
        TextView text = new TextView(this.getContext());
        text.setTextSize(0, (float)this.dimen(sizeRes));
        text.setTextColor(this.color(colorRes));
        return text;
    }

    protected void onDetachedFromWindow() {
        if (this.actions != null) {
            this.actions.leavePage();
        }
        super.onDetachedFromWindow();
    }

    private int dimen(int res) {
        return this.getResources().getDimensionPixelSize(res);
    }

    private int color(int res) {
        return this.getContext().getColor(res);
    }
}

