/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.util.AttributeSet
 *  android.view.LayoutInflater
 *  android.view.View
 *  android.view.ViewGroup
 *  android.view.ViewGroup$LayoutParams
 *  android.widget.LinearLayout
 *  android.widget.ScrollView
 *  android.widget.TextView
 *  android.widget.TextView$BufferType
 *  com.treelearn.app.R$dimen
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.screen.MarkdownRenderer;
import com.treelearn.app.ui.ChatMessage;

public final class ReasoningDisclosure
extends LinearLayout {
    private final View toggle;
    private final TextView title;
    private final TextView state;
    private final ScrollView bodyScroll;
    private final TextView body;
    private String messageId = "";
    private Listener listener;

    public ReasoningDisclosure(Context context, AttributeSet attributes) {
        super(context, attributes);
        this.setOrientation(1);
        LayoutInflater.from((Context)context).inflate(R.layout.view_reasoning, (ViewGroup)this, true);
        this.toggle = this.findViewById(R.id.reasoning_toggle);
        this.title = (TextView)this.findViewById(R.id.reasoning_title);
        this.state = (TextView)this.findViewById(R.id.reasoning_state);
        this.bodyScroll = (ScrollView)this.findViewById(R.id.reasoning_body_scroll);
        this.body = (TextView)this.findViewById(R.id.reasoning_body);
        this.toggle.setOnClickListener(view -> {
            if (this.listener != null && !this.messageId.isEmpty()) {
                this.listener.onReasoningToggled(this.messageId);
            }
        });
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void render(ChatMessage message, boolean expanded, boolean thinkingEnabled) {
        this.messageId = message.messageId;
        if (!thinkingEnabled || !message.canExpandReasoning()) {
            this.setVisibility(8);
            this.body.setText((CharSequence)"");
            return;
        }
        this.setVisibility(0);
        this.state.setText(this.stateText(message, expanded));
        this.body.setText(MarkdownRenderer.render(this.bodyText(message, expanded).toString()), TextView.BufferType.SPANNABLE);
        this.bodyScroll.setVisibility(expanded ? 0 : 8);
        this.clampBodyHeight();
        this.toggle.setContentDescription((CharSequence)(this.title.getText() + " " + this.state.getText()));
    }

    private CharSequence stateText(ChatMessage message, boolean expanded) {
        if (message.isFormingReasoning()) {
            return this.getContext().getString(R.string.chat_reasoning_forming);
        }
        return this.getContext().getString(expanded ? R.string.chat_reasoning_collapse : R.string.chat_reasoning_expand);
    }

    private CharSequence bodyText(ChatMessage message, boolean expanded) {
        if (!expanded) {
            return "";
        }
        if (message.reasoning.isEmpty()) {
            return this.getContext().getString(R.string.chat_reasoning_pending);
        }
        return message.reasoning;
    }

    private void clampBodyHeight() {
        if (this.bodyScroll.getVisibility() != 0) {
            return;
        }
        this.bodyScroll.post(() -> {
            int target;
            int maxHeight = this.getResources().getDimensionPixelSize(R.dimen.reasoning_max_height);
            ViewGroup.LayoutParams params = this.bodyScroll.getLayoutParams();
            int n = target = this.bodyScroll.getHeight() > maxHeight ? maxHeight : -2;
            if (params.height != target) {
                params.height = target;
                this.bodyScroll.setLayoutParams(params);
            }
        });
    }

    public static interface Listener {
        public void onReasoningToggled(String var1);
    }
}

