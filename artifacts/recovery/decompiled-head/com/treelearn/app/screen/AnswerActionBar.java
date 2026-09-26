/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.util.AttributeSet
 *  android.view.LayoutInflater
 *  android.view.ViewGroup
 *  android.widget.Button
 *  android.widget.LinearLayout
 *  android.widget.TextView
 *  com.treelearn.app.R$id
 *  com.treelearn.app.R$layout
 *  com.treelearn.app.R$string
 */
package com.treelearn.app.screen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.treelearn.app.R;
import com.treelearn.app.ui.ChatMessage;
import java.util.function.Consumer;

public final class AnswerActionBar
extends LinearLayout {
    private final Button quote;
    private final Button copy;
    private final Button reset;
    private final Button fork;
    private final Button children;
    private final TextView version;
    private String messageId = "";
    private Listener listener;

    public AnswerActionBar(Context context, AttributeSet attributes) {
        super(context, attributes);
        this.setOrientation(0);
        LayoutInflater.from((Context)context).inflate(R.layout.view_answer_actions, (ViewGroup)this, true);
        this.quote = (Button)this.findViewById(R.id.act_quote);
        this.copy = (Button)this.findViewById(R.id.act_copy);
        this.reset = (Button)this.findViewById(R.id.act_reset);
        this.fork = (Button)this.findViewById(R.id.act_fork);
        this.children = (Button)this.findViewById(R.id.act_children);
        this.version = (TextView)this.findViewById(R.id.act_version);
        this.quote.setContentDescription((CharSequence)this.getContext().getString(R.string.chat_quote_title));
        this.copy.setContentDescription((CharSequence)this.getContext().getString(R.string.chat_copy_title));
        this.reset.setContentDescription((CharSequence)this.getContext().getString(R.string.chat_reset_title));
        this.fork.setContentDescription((CharSequence)this.getContext().getString(R.string.chat_fork_title));
        this.quote.setOnClickListener(view -> this.fire(target -> target.onQuotePressed(this.messageId)));
        this.copy.setOnClickListener(view -> this.fire(target -> target.onCopyPressed(this.messageId)));
        this.reset.setOnClickListener(view -> this.fire(target -> target.onResetPressed(this.messageId)));
        this.fork.setOnClickListener(view -> this.fire(target -> target.onForkPressed(this.messageId)));
        this.children.setOnClickListener(view -> this.fire(target -> target.onChildrenPressed(this.messageId)));
        this.version.setOnClickListener(view -> this.fire(target -> target.onVersionPressed(this.messageId)));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void render(ChatMessage message) {
        this.messageId = message.messageId;
        this.quote.setEnabled(message.canQuote());
        this.copy.setEnabled(message.canCopy());
        this.reset.setEnabled(message.canReset());
        this.fork.setEnabled(message.canFork());
        boolean hasChildren = message.hasChildren();
        this.children.setVisibility(hasChildren ? 0 : 8);
        this.children.setEnabled(hasChildren);
        this.children.setText((CharSequence)(hasChildren ? this.getContext().getString(R.string.chat_act_children_count, new Object[]{message.childBranchCount}) : this.getContext().getString(R.string.chat_act_children)));
        this.children.setContentDescription((CharSequence)(hasChildren ? this.getContext().getString(R.string.chat_children_title, new Object[]{message.childBranchCount}) : this.getContext().getString(R.string.chat_act_children)));
        boolean hasVersions = message.hasVersionHistory();
        this.version.setVisibility(hasVersions ? 0 : 8);
        this.version.setText((CharSequence)this.getContext().getString(R.string.chat_version_chip, new Object[]{message.activeRevisionNo(), message.revisionCount}));
        this.version.setContentDescription((CharSequence)this.getContext().getString(R.string.chat_version_title, new Object[]{message.activeRevisionNo(), message.revisionCount}));
    }

    public String messageId() {
        return this.messageId;
    }

    private void fire(Consumer<Listener> call) {
        if (this.listener != null && !this.messageId.isEmpty()) {
            call.accept(this.listener);
        }
    }

    public static interface Listener {
        public void onQuotePressed(String var1);

        public void onCopyPressed(String var1);

        public void onResetPressed(String var1);

        public void onForkPressed(String var1);

        public void onChildrenPressed(String var1);

        public void onVersionPressed(String var1);
    }
}

