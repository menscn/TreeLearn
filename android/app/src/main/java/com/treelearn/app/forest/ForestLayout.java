package com.treelearn.app.forest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The forest's ground geometry (T-068): a pure, stable placement for each topic.
 *
 * <p>This is the answer to the prototype's rule that a tree must not move when the learner comes
 * back. Position is a function of the topic id alone - never of row order, recency or the number of
 * other topics - so adding a topic cannot re-sort the forest around it. The stable rank is the
 * unsigned hash of the id, which also seeds the sprite: one number, one tree, forever.
 *
 * <p>The ground is a golden-angle spiral inside normalised bounds, so a small forest fills the
 * clearing evenly instead of clustering at the top. A crowd past {@value #COMFORTABLE_TREES} gets a
 * deterministic separation pass, and every sprite shrinks a little with density; the trunk stays
 * above the prototype's minimum so a busy forest is still readable.
 *
 * <p>No Android type appears here: the scene multiplies these normalised numbers by the pixels it
 * actually has, and the JVM tests can judge the ground without a device.
 */
public final class ForestLayout {

    /** The clearing's normalised bounds; the prototype's trees sit between these margins. */
    public static final float MIN_X = 0.10f;
    public static final float MAX_X = 0.90f;
    public static final float MIN_Y = 0.13f;
    public static final float MAX_Y = 0.80f;

    /**
     * The prototype's smallest and largest sprite (54px and 100px on a 788px ground), as a fraction
     * of the ground's height. The scene multiplies these by its own height, so the trees keep the
     * prototype's share of the clearing on any phone.
     */
    public static final float MIN_SIZE = 0.0685f;
    public static final float MAX_SIZE = 0.1269f;

    /** Past this many trees the spiral gets a separating pass; at or below it, the spiral is even. */
    public static final int COMFORTABLE_TREES = 18;

    /** 9 segments is the prototype's largest tree (size 100); more do not keep growing it. */
    private static final int SIZE_CAP_SEGMENTS = 9;

    private static final float GOLDEN_ANGLE = 2.399963229728653f;
    private static final float CENTER_X = 0.50f;
    private static final float CENTER_Y = 0.465f;
    private static final float RADIUS_X = 0.40f;
    private static final float RADIUS_Y = 0.335f;
    private static final float MIN_GAP = 0.048f;
    private static final int SEPARATION_ROUNDS = 40;

    private ForestLayout() {
    }

    /** One tree's ground position, in normalised coordinates the scene scales to its own box. */
    public static final class Placement {

        public final float x;
        public final float y;
        /** Sprite size as a fraction of the ground's height. */
        public final float size;
        /** The prototype's canopy shade, 0.80..1.15. */
        public final float tone;
        /** A non-zero sprite seed derived from the same stable hash as the position. */
        public final int seed;

        Placement(float x, float y, float size, float tone, int seed) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.tone = tone;
            this.seed = seed;
        }
    }

    /**
     * Places one tree per topic, in the input's order.
     *
     * @param topicIds     the topics' canonical ids; the only source of position
     * @param branchCounts the segment count per topic, used only for the sprite's size
     */
    public static List<Placement> compute(List<String> topicIds, List<Integer> branchCounts) {
        if (topicIds == null || branchCounts == null) {
            throw new IllegalArgumentException("forest inputs must not be null");
        }
        int count = topicIds.size();
        if (branchCounts.size() != count) {
            throw new IllegalArgumentException("every topic needs one branch count, got "
                    + topicIds.size() + " ids and " + branchCounts.size() + " counts");
        }
        List<Integer> order = stableOrder(topicIds);
        float density = densityFor(count);
        float[] xs = new float[count];
        float[] ys = new float[count];
        Placement[] placed = new Placement[count];

        for (int rank = 0; rank < count; rank++) {
            int index = order.get(rank);
            int hash = hash(topicIds.get(index));
            position(rank, count, hash, xs, ys);
            int branches = branchCounts.get(index) == null ? 1 : branchCounts.get(index);
            placed[index] = new Placement(xs[rank], ys[rank], sizeFor(branches, density),
                    toneFor(hash), seedFor(hash));
        }

        if (count > COMFORTABLE_TREES) {
            separate(xs, ys);
            for (int rank = 0; rank < count; rank++) {
                int index = order.get(rank);
                Placement old = placed[index];
                placed[index] = new Placement(xs[rank], ys[rank], old.size, old.tone, old.seed);
            }
        }

        List<Placement> result = new ArrayList<Placement>(count);
        for (Placement placement : placed) {
            result.add(placement);
        }
        return result;
    }

    /**
     * The rank order: unsigned hash ascending, id as the tiebreak. Two topics always produce the
     * same order, and the database's row order is never consulted.
     */
    private static List<Integer> stableOrder(List<String> topicIds) {
        final int count = topicIds.size();
        List<Integer> order = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) {
            if (topicIds.get(i) == null) {
                throw new IllegalArgumentException("topic id at index " + i + " must not be null");
            }
            order.add(i);
        }
        order.sort(new Comparator<Integer>() {
            @Override
            public int compare(Integer left, Integer right) {
                int byHash = Integer.compare(hash(topicIds.get(left)) ^ 0x80000000,
                        hash(topicIds.get(right)) ^ 0x80000000);
                return byHash != 0 ? byHash : topicIds.get(left).compareTo(topicIds.get(right));
            }
        });
        return order;
    }

    /** Golden-angle spiral: angle by rank, radius by sqrt(rank), both stable per topic. */
    private static void position(int rank, int count, int hash, float[] xs, float[] ys) {
        if (count == 1) {
            xs[0] = CENTER_X;
            ys[0] = 0.46f;
            return;
        }
        if (count == 2) {
            xs[rank] = rank == 0 ? 0.40f : 0.63f;
            ys[rank] = rank == 0 ? 0.44f : 0.56f;
            return;
        }
        float angleJitter = ((hash >>> 16) & 0xFF) / 255f * 0.30f - 0.15f;
        float angle = rank * GOLDEN_ANGLE + angleJitter;
        float radialScale = 0.92f + ((hash >>> 24) & 0xFF) / 255f * 0.08f;
        float rho = (float) Math.sqrt((rank + 0.55f) / count) * radialScale;
        xs[rank] = clamp(CENTER_X + (float) Math.cos(angle) * RADIUS_X * rho, MIN_X, MAX_X);
        ys[rank] = clamp(CENTER_Y + (float) Math.sin(angle) * RADIUS_Y * rho, MIN_Y, MAX_Y);
    }

    /** Push overlapping trunks apart; deterministic because the pair walk is always in rank order. */
    private static void separate(float[] xs, float[] ys) {
        int count = xs.length;
        for (int round = 0; round < SEPARATION_ROUNDS; round++) {
            boolean moved = false;
            for (int left = 0; left < count; left++) {
                for (int right = left + 1; right < count; right++) {
                    float dx = xs[right] - xs[left];
                    float dy = ys[right] - ys[left];
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance >= MIN_GAP) {
                        continue;
                    }
                    if (distance < 0.000001f) {
                        dx = 1f;
                        dy = 0f;
                        distance = 1f;
                    }
                    float push = (MIN_GAP - distance) / 2f + 0.0005f;
                    xs[left] = clamp(xs[left] - dx / distance * push, MIN_X, MAX_X);
                    ys[left] = clamp(ys[left] - dy / distance * push, MIN_Y, MAX_Y);
                    xs[right] = clamp(xs[right] + dx / distance * push, MIN_X, MAX_X);
                    ys[right] = clamp(ys[right] + dy / distance * push, MIN_Y, MAX_Y);
                    moved = true;
                }
            }
            if (!moved) {
                return;
            }
        }
    }

    private static float sizeFor(int branches, float density) {
        int segments = Math.max(1, Math.min(SIZE_CAP_SEGMENTS, branches));
        float span = (MAX_SIZE - MIN_SIZE) * (segments - 1) / (SIZE_CAP_SEGMENTS - 1);
        return (MIN_SIZE + span) * density;
    }

    private static float densityFor(int count) {
        if (count <= 12) {
            return 1f;
        }
        return Math.max(0.62f, Math.min(1f, (float) Math.sqrt(12.0 / count)));
    }

    private static float toneFor(int hash) {
        return 0.80f + ((hash >>> 8) & 0xFF) / 255f * 0.35f;
    }

    private static int seedFor(int hash) {
        int seed = hash & 0x7FFFFFFF;
        return seed == 0 ? 1 : seed;
    }

    /** FNV-1a; the id's own bytes, so the same UUID gives the same tree on every install. */
    private static int hash(String value) {
        int hash = 0x811C9DC5;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x01000193;
        }
        return hash;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
