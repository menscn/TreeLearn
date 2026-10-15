package com.treelearn.treelayout;

/**
 * One placed title plate in the canopy bar above the crown.
 *
 * <p>Plates share their lane's baseline ({@code y + height}), so a plate that grows a summary line
 * grows upwards and can never dip into the leaves below. The leader line runs from the bottom
 * centre of the plate to {@code (leaderX, leaderY)} — the pointy end of its own leaf.
 */
public final class LabelGeometry {

    public final String branchId;
    public final float x;
    public final float y;
    public final float width;
    public final float height;
    /** Bar row, 0 = the row closest to the crown. */
    public final int row;
    /** True only in the near tier and only when the branch actually has a summary. */
    public final boolean showSummary;
    public final float leaderX;
    public final float leaderY;

    LabelGeometry(String branchId, float x, float y, float width, float height, int row,
            boolean showSummary, float leaderX, float leaderY) {
        this.branchId = branchId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.row = row;
        this.showSummary = showSummary;
        this.leaderX = leaderX;
        this.leaderY = leaderY;
    }

    @Override
    public String toString() {
        return "LabelGeometry[" + branchId + " row=" + row + " at " + x + "," + y + " "
                + width + "x" + height + (showSummary ? " +summary" : "") + "]";
    }
}
