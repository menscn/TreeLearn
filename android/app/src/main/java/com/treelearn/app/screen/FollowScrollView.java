package com.treelearn.app.screen;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

public final class FollowScrollView extends ScrollView {
    private View.OnTouchListener touchObserver;

    public FollowScrollView(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    public void setTouchObserver(View.OnTouchListener observer) {
        touchObserver = observer;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (touchObserver != null) {
            touchObserver.onTouch(this, event);
        }
        return super.dispatchTouchEvent(event);
    }
}
