package com.treelearn.app.screen;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import com.treelearn.app.R;

/**
 * The floating glass capsule (T-2026-10-22-061): a surface that projects without its own
 * translucency muting the projection.
 *
 * <p>A View derives its elevation shadow's opacity from its background drawable's alpha unless the
 * outline says otherwise. A 54% chrome fill therefore casts roughly half the shadow its elevation
 * asks for, and a glass face that weakens its own shadow cannot read as lifted off the scene. This
 * class owns the outline - the capsule's shape at full opacity - so the fill can stay translucent
 * and the shadow can stay whole. Both shadow passes are taken from the token copies: Android has no
 * other way to read an rgba token.
 */
public final class GlassSurfaceLayout extends LinearLayout {

    public GlassSurfaceLayout(Context context) {
        this(context, null);
    }

    public GlassSurfaceLayout(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        view.getHeight() / 2f);
                outline.setAlpha(1f);
            }
        });
        setClipToOutline(false);
        setOutlineSpotShadowColor(context.getColor(R.color.glass_shadow_key));
        setOutlineAmbientShadowColor(context.getColor(R.color.glass_shadow_ambient));
    }
}
