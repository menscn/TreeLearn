/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.ui;

public final class PopupPlacement {
    public static final float MARGIN = 8.0f;
    public final String branchId;
    public final float x;
    public final float y;
    public final float width;
    public final float height;
    public final boolean flippedX;
    public final boolean flippedY;

    public PopupPlacement(String branchId, float x, float y, float width, float height, boolean flippedX, boolean flippedY) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("branchId must not be blank");
        }
        this.branchId = branchId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.flippedX = flippedX;
        this.flippedY = flippedY;
    }

    public static PopupPlacement place(String branchId, float anchorX, float anchorY, float width, float height, float windowWidth, float windowHeight) {
        float centred = anchorX - width / 2.0f;
        boolean flippedX = centred < 8.0f || centred + width > windowWidth - 8.0f;
        boolean flippedY = anchorY - height - 8.0f < 8.0f;
        return new PopupPlacement(branchId, PopupPlacement.clamp(centred, 8.0f, windowWidth - width - 8.0f), PopupPlacement.clamp(flippedY ? anchorY + 8.0f : anchorY - height - 8.0f, 8.0f, windowHeight - height - 8.0f), width, height, flippedX, flippedY);
    }

    public boolean fitsInside(float windowWidth, float windowHeight) {
        return this.x >= 8.0f && this.x + this.width <= windowWidth - 8.0f && this.y >= 8.0f && this.y + this.height <= windowHeight - 8.0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public String toString() {
        return "PopupPlacement[" + this.branchId + " " + this.width + "x" + this.height + " at " + this.x + "," + this.y + (this.flippedX ? " flippedX" : "") + (this.flippedY ? " flippedY" : "") + "]";
    }
}

