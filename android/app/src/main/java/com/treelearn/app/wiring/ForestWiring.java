package com.treelearn.app.wiring;

import com.treelearn.app.forest.ForestData;
import com.treelearn.app.library.LibraryData;
import com.treelearn.app.library.LibraryViewState;
import com.treelearn.core.domain.Guard;
import com.treelearn.core.domain.Persona;
import com.treelearn.data.db.dao.TopicDao;
import com.treelearn.data.stats.StatsAggregator;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The forest's real read path (T-068): the library's topic collection, the data layer's one forest
 * projection, the stored style, and the same stats entry point as every other statistics surface.
 *
 * <p><strong>No second topic list.</strong> Which trees exist is {@code LibraryData.topics()}'s
 * answer; this class adds only what that read does not carry. The style is the topic's stored
 * column through the {@link TopicDao#listForestCounts} projection, with
 * {@link SqliteLibraryStore#personaOfBranch} as the fallback when the projection did not answer -
 * so the sheet cannot invent a style the conversation would not name.
 *
 * <p><strong>Degradation is per fact, not per page.</strong> A failed forest projection keeps every
 * tree on the ground and marks its count unknown; a failed stats read costs only the streak. Only
 * the topic collection itself - the thing that decides whether there is a forest at all - turns the
 * page into its error phase.
 */
public final class ForestWiring implements ForestData {

    private final LibraryData library;
    private final SqliteLibraryStore store;
    private final HomeStore home;
    private final Clock clock;

    public ForestWiring(LibraryData library, SqliteLibraryStore store, HomeStore home, Clock clock) {
        this.library = Guard.object(library, "library");
        this.store = Guard.object(store, "store");
        this.home = Guard.object(home, "home");
        this.clock = Guard.object(clock, "clock");
    }

    @Override
    public Snapshot read() {
        List<LibraryViewState.Row> rows = library.topics();
        Map<String, TopicDao.ForestCount> counts = countsByTopic();
        List<Topic> topics = new ArrayList<Topic>(rows.size());
        for (LibraryViewState.Row row : rows) {
            TopicDao.ForestCount count = counts.get(row.topicId);
            boolean countKnown = count != null;
            topics.add(new Topic(row.topicId, row.resumeBranchId, row.title,
                    countKnown ? count.branchCount : 1,
                    countKnown ? count.archivedCount : -1,
                    row.lastUsedAt, personaOf(row, count), countKnown));
        }
        return new Snapshot(topics, streakDays());
    }

    /** One projection for the whole forest; a broken read leaves every count unknown. */
    private Map<String, TopicDao.ForestCount> countsByTopic() {
        try {
            List<TopicDao.ForestCount> read = store.forestCounts();
            Map<String, TopicDao.ForestCount> byTopic =
                    new HashMap<String, TopicDao.ForestCount>(read.size());
            for (TopicDao.ForestCount count : read) {
                byTopic.put(count.topicId, count);
            }
            return byTopic;
        } catch (RuntimeException broken) {
            return Collections.emptyMap();
        }
    }

    /**
     * The style the projection carried; the branch read is the fallback for a topic whose count row
     * is missing. A style the build cannot name already reads as the default through
     * {@link Persona#fromWireId}, the same rule every other surface uses.
     */
    private Persona personaOf(LibraryViewState.Row row, TopicDao.ForestCount count) {
        if (count != null) {
            return Persona.fromWireId(count.personaWireId);
        }
        try {
            TopicDao.PersonaRead read = store.personaOfBranch(row.resumeBranchId);
            return read == null ? Persona.DEFAULT : read.persona;
        } catch (RuntimeException broken) {
            return Persona.DEFAULT;
        }
    }

    /**
     * The same aggregation entry the drawer and the statistics page read; an empty history has no
     * streak to show and an unreadable one hides the fact instead of printing 0.
     */
    private Integer streakDays() {
        try {
            HomeStore.StatsRaw raw = home.statsRaw();
            StatsAggregator.StatsResult result = StatsAggregator.aggregate(raw.answerTimestamps,
                    raw.totalQuestions, raw.totalMessages, clock.getZone(),
                    LocalDate.now(clock));
            return result.isEmpty() ? null : Integer.valueOf(result.currentStreak());
        } catch (RuntimeException broken) {
            return null;
        }
    }
}
