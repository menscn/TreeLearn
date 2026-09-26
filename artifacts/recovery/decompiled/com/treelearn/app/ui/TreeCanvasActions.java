/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

public interface TreeCanvasActions {
    public void onPan(float var1, float var2);

    public void onZoom(float var1, float var2, float var3);

    public void onViewportChanged(float var1, float var2);

    public void onFit();

    public void onTap(float var1, float var2);

    public void onDismissPopup();

    public void onCollapseToggled(String var1);

    public void onOpenCurrent();

    public void onNavigationPressed();

    public void onBackPressed();
}

