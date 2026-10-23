package com.treelearn.app.forest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.Test;

/**
 * T-2026-10-23-068: the forest's ground placement is a pure, stable function of the topic id and
 * its segment count. Nothing here reaches a database or a View; the scene only turns these
 * normalized numbers into pixels.
 */
public final class ForestLayoutTest {

    private static String id(String tag) {
        return UUID.nameUUIDFromBytes(tag.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static List<String> ids(int count) {
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            ids.add(id("tree-" + i));
        }
        return ids;
    }

    private static List<Integer> counts(int count) {
        List<Integer> counts = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            counts.add(1 + (i % 10));
        }
        return counts;
    }

    private static Map<String, ForestLayout.Placement> byId(List<String> topicIds,
            List<ForestLayout.Placement> placements) {
        Map<String, ForestLayout.Placement> mapped = new HashMap<String, ForestLayout.Placement>();
        for (int i = 0; i < topicIds.size(); i++) {
            mapped.put(topicIds.get(i), placements.get(i));
        }
        return mapped;
    }

    @Test
    public void theSameForestIsPlacedTheSameWayEveryTime() {
        List<String> topicIds = ids(12);
        List<Integer> branches = counts(12);

        List<ForestLayout.Placement> first = ForestLayout.compute(topicIds, branches);
        List<ForestLayout.Placement> second = ForestLayout.compute(topicIds, branches);

        assertEquals(12, first.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).x, second.get(i).x, 0.0001f);
            assertEquals(first.get(i).y, second.get(i).y, 0.0001f);
            assertEquals(first.get(i).size, second.get(i).size, 0.0001f);
            assertEquals(first.get(i).seed, second.get(i).seed);
            assertEquals(first.get(i).tone, second.get(i).tone, 0.0001f);
        }
    }

    @Test
    public void theDatabaseOrderCannotMoveATree() {
        List<String> topicIds = ids(12);
        List<Integer> branches = counts(12);
        Map<String, ForestLayout.Placement> original = byId(topicIds,
                ForestLayout.compute(topicIds, branches));

        List<Integer> shuffledIndices = new ArrayList<Integer>();
        for (int i = 0; i < topicIds.size(); i++) {
            shuffledIndices.add(i);
        }
        Collections.shuffle(shuffledIndices, new Random(20261023L));
        List<String> shuffledIds = new ArrayList<String>();
        List<Integer> shuffledBranches = new ArrayList<Integer>();
        for (Integer index : shuffledIndices) {
            shuffledIds.add(topicIds.get(index));
            shuffledBranches.add(branches.get(index));
        }

        Map<String, ForestLayout.Placement> shuffled = byId(shuffledIds,
                ForestLayout.compute(shuffledIds, shuffledBranches));
        for (String topicId : topicIds) {
            assertEquals("topic " + topicId + " moved with the row order",
                    original.get(topicId).x, shuffled.get(topicId).x, 0.0001f);
            assertEquals(original.get(topicId).y, shuffled.get(topicId).y, 0.0001f);
            assertEquals(original.get(topicId).size, shuffled.get(topicId).size, 0.0001f);
        }
    }

    @Test
    public void everyTreeStaysInsideTheGround() {
        List<ForestLayout.Placement> placements = ForestLayout.compute(ids(30), counts(30));

        for (ForestLayout.Placement placement : placements) {
            assertTrue("x out of ground: " + placement.x,
                    placement.x >= ForestLayout.MIN_X && placement.x <= ForestLayout.MAX_X);
            assertTrue("y out of ground: " + placement.y,
                    placement.y >= ForestLayout.MIN_Y && placement.y <= ForestLayout.MAX_Y);
            assertTrue("the sprite must have a size", placement.size > 0f);
            assertTrue("tone stays within the prototype's range",
                    placement.tone >= 0.8f && placement.tone <= 1.15f);
            assertNotEquals("the seed must drive a real sprite", 0, placement.seed);
        }
    }

    @Test
    public void moreSegmentsMakeABiggerTreeAndTheCeilingHolds() {
        List<String> topicIds = Collections.singletonList(id("one"));
        float small = ForestLayout.compute(topicIds, Collections.singletonList(1)).get(0).size;
        float medium = ForestLayout.compute(topicIds, Collections.singletonList(4)).get(0).size;
        float large = ForestLayout.compute(topicIds, Collections.singletonList(9)).get(0).size;
        float capped = ForestLayout.compute(topicIds, Collections.singletonList(40)).get(0).size;

        assertTrue("1 → 4 segments must grow the tree", medium > small);
        assertTrue("4 → 9 segments must grow the tree", large > medium);
        assertEquals("past the prototype's largest tree the size stops", large, capped, 0.0001f);
        assertTrue("the ceiling is the prototype's 100px sprite", large <= ForestLayout.MAX_SIZE);
    }

    @Test
    public void treesDoNotShareTheSameGroundAtThePrototypeSize() {
        List<ForestLayout.Placement> placements = ForestLayout.compute(ids(12), counts(12));

        float closest = Float.MAX_VALUE;
        for (int i = 0; i < placements.size(); i++) {
            for (int j = i + 1; j < placements.size(); j++) {
                float dx = placements.get(i).x - placements.get(j).x;
                float dy = placements.get(i).y - placements.get(j).y;
                closest = Math.min(closest, (float) Math.sqrt(dx * dx + dy * dy));
            }
        }
        assertTrue("two 12-tree canopies overlap at " + closest, closest > 0.06f);
    }

    @Test
    public void aCrowdedForestStillProducesAReadableGround() {
        List<ForestLayout.Placement> placements = ForestLayout.compute(ids(40), counts(40));

        assertEquals(40, placements.size());
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < placements.size(); i++) {
            for (int j = i + 1; j < placements.size(); j++) {
                float dx = placements.get(i).x - placements.get(j).x;
                float dy = placements.get(i).y - placements.get(j).y;
                closest = Math.min(closest, (float) Math.sqrt(dx * dx + dy * dy));
            }
        }
        assertTrue("dense forests must still separate their trunks at " + closest,
                closest > 0.04f);
    }
}
