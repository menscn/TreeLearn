package com.treelearn.web.api;

import com.treelearn.core.domain.GenerationState;
import com.treelearn.core.domain.MessageRole;
import com.treelearn.core.domain.SearchHit;
import com.treelearn.core.domain.SearchQuery;
import com.treelearn.web.api.dto.SearchStatsDtos;
import com.treelearn.web.storage.DesktopSchema.AnswerRevisions;
import com.treelearn.web.storage.DesktopSchema.Branches;
import com.treelearn.web.storage.DesktopSchema.GenerationOperations;
import com.treelearn.web.storage.DesktopSchema.Messages;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.generation.ServiceRefusalException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用内搜索与学习统计端点（任务 T-2026-10-10-043，契约 §5.13/§5.14/§6.7/§6.8）。
 *
 * <p><strong>搜索逐臂镜像 Android {@code SearchDao}</strong>：七臂 {@code UNION ALL}
 * （TITLE/QUESTION/ANCHOR/ANSWER/REASONING/PARTIAL_ANSWER/PARTIAL_REASONING），归档分支不可搜，
 * 答案/推理经 {@code messages.active_revision_id}、失败尝试经 {@code messages.active_operation_id}，
 * 全部 {@code LIKE ? ESCAPE '\'} 参数化（模式来自共享域 {@link SearchQuery}，空查询不建
 * {@code LIKE '%%'}）。行序不受 SQL 保证，排序复用 {@link SearchHit#DISPLAY_ORDER}。
 *
 * <p><strong>统计镜像 Android {@code StatsDao} + {@code StatsAggregator}</strong>：三个原始计数
 * （回答时间戳、提问数、消息数）出 DB，自然日归属/连续段/空态在 Java 内判定（有回答即活跃），
 * 存储时间戳为 UTC 毫秒，「哪一天」按设备时区折算而非平移存储值。当月热力格 7×6、周一为首列：
 * 未来整月全 {@code -1}；非未来月的补位格为 {@code 0}、当月未到的日子为 {@code -1}（对齐
 * {@code StatsDetailPresenter.monthValues}，补位格取值按本任务测试钉死的口径收窄）。
 *
 * <p>命中区间为相对 snippet 的 UTF-16 半开区间：折叠文本内首个命中，定位失败回落零宽区间 0/0。
 */
@RestController
class SearchStatsController {

    private static final int MONTH_CELL_COUNT = 42;

    private final JdbcSupport jdbc;
    private final Clock clock;

    SearchStatsController(JdbcSupport jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------ search

    @GetMapping("/api/search")
    SearchStatsDtos.SearchResult search(@RequestParam("q") String q) {
        SearchQuery query = SearchQuery.of(q);
        if (query.isEmpty()) {
            return new SearchStatsDtos.SearchResult(query.normalized(), List.of());
        }
        List<SearchHit> hits = jdbc.queryList(SEARCH_STATEMENT,
                bindPattern(query.likePattern()), SearchStatsController::readHit);
        hits.sort(SearchHit.DISPLAY_ORDER);
        List<SearchStatsDtos.SearchResultHit> mapped = new ArrayList<>(hits.size());
        for (SearchHit hit : hits) {
            mapped.add(toDto(hit, query));
        }
        return new SearchStatsDtos.SearchResult(query.normalized(), List.copyOf(mapped));
    }

    private static SearchStatsDtos.SearchResultHit toDto(SearchHit hit, SearchQuery query) {
        int[] interval = intervalOf(hit.snippet, query.normalized());
        return new SearchStatsDtos.SearchResultHit(hit.field.name(), hit.topicId, hit.branchId,
                hit.messageId, hit.messageOrdinal, hit.revisionId, hit.revisionNo, hit.snippet,
                interval[0], interval[1]);
    }

    /**
     * 折叠文本内首个命中。查询与 snippet 各自按同一折叠规则变小写后 indexOf；折叠改变长度或找不到
     * 时回落零宽区间（0/0），不猜一个可能越界的区间。
     */
    private static int[] intervalOf(String snippet, String foldedQuery) {
        int start = snippet.toLowerCase(Locale.ROOT).indexOf(foldedQuery);
        if (start < 0) {
            return new int[] {0, 0};
        }
        return new int[] {start, start + foldedQuery.length()};
    }

    private static SearchHit readHit(java.sql.ResultSet rows) throws java.sql.SQLException {
        String messageId = rows.getString("message_id");
        return SearchHit.of(SearchHit.Field.valueOf(rows.getString("matched_field")),
                rows.getString(Branches.TOPIC_ID), rows.getString("branch_id"), messageId,
                messageId == null ? -1 : rows.getInt(Messages.ORDINAL), rows.getString("revision_id"),
                rows.getInt(AnswerRevisions.REVISION_NO), rows.getString("snippet"));
    }

    private static JdbcSupport.Binder bindPattern(String pattern) {
        return statement -> {
            for (int i = 1; i <= 7; i++) {
                statement.setString(i, pattern);
            }
        };
    }

    /** 七臂搜索语句：列名全部来自 {@code DesktopSchema} 常量，语句形状与 Android {@code SearchDao} 对齐。 */
    private static final String SEARCH_STATEMENT = String.join(" UNION ALL ", List.of(
            arm(SearchHit.Field.TITLE, "NULL", "-1", "NULL", "0", branchColumn(Branches.TITLE),
                    like(branchColumn(Branches.TITLE))),
            arm(SearchHit.Field.QUESTION, messageColumn(Messages.ID), messageColumn(Messages.ORDINAL),
                    "NULL", "0", messageColumn(Messages.USER_CONTENT),
                    messageColumn(Messages.ROLE) + " = '" + MessageRole.USER.name() + "' AND "
                            + like(messageColumn(Messages.USER_CONTENT))),
            arm(SearchHit.Field.ANCHOR, "sm." + Messages.ID, "sm." + Messages.ORDINAL,
                    "b." + Branches.SOURCE_REVISION_ID, "0", "b." + Branches.ANCHOR_QUOTE,
                    like("b." + Branches.ANCHOR_QUOTE)),
            arm(SearchHit.Field.ANSWER, messageColumn(Messages.ID), messageColumn(Messages.ORDINAL),
                    "ar." + AnswerRevisions.ID, "ar." + AnswerRevisions.REVISION_NO,
                    "ar." + AnswerRevisions.ANSWER_CONTENT, like("ar." + AnswerRevisions.ANSWER_CONTENT)),
            arm(SearchHit.Field.REASONING, messageColumn(Messages.ID), messageColumn(Messages.ORDINAL),
                    "ar." + AnswerRevisions.ID, "ar." + AnswerRevisions.REVISION_NO,
                    "ar." + AnswerRevisions.REASONING_CONTENT,
                    like("ar." + AnswerRevisions.REASONING_CONTENT)),
            arm(SearchHit.Field.PARTIAL_ANSWER, messageColumn(Messages.ID),
                    messageColumn(Messages.ORDINAL), "NULL", "0",
                    "go." + GenerationOperations.PARTIAL_ANSWER, failedAttempt(
                            "go." + GenerationOperations.PARTIAL_ANSWER)),
            arm(SearchHit.Field.PARTIAL_REASONING, messageColumn(Messages.ID),
                    messageColumn(Messages.ORDINAL), "NULL", "0",
                    "go." + GenerationOperations.PARTIAL_REASONING, failedAttempt(
                            "go." + GenerationOperations.PARTIAL_REASONING))));

    private static String arm(SearchHit.Field field, String messageId, String ordinal,
            String revisionId, String revisionNo, String snippetColumn, String predicate) {
        return "SELECT '" + field.name() + "' AS matched_field, b." + Branches.TOPIC_ID + ", "
                + "b." + Branches.ID + " AS branch_id, " + messageId + " AS message_id, "
                + ordinal + " AS " + Messages.ORDINAL + ", " + revisionId + " AS revision_id, "
                + revisionNo + " AS " + AnswerRevisions.REVISION_NO + ", " + snippetColumn
                + " AS snippet FROM " + fromClause(field)
                + " WHERE b." + Branches.ARCHIVED_AT + " IS NULL AND " + predicate;
    }

    private static String fromClause(SearchHit.Field field) {
        String branches = Branches.TABLE + " b";
        return switch (field) {
            case TITLE, ANCHOR -> field == SearchHit.Field.TITLE ? branches
                    : branches + " JOIN " + Messages.TABLE + " sm ON sm." + Messages.ID + " = b."
                            + Branches.SOURCE_MESSAGE_ID;
            case QUESTION -> branches + " JOIN " + Messages.TABLE + " m ON m." + Messages.BRANCH_ID
                    + " = b." + Branches.ID;
            case ANSWER, REASONING -> branches + " JOIN " + Messages.TABLE + " m ON m."
                    + Messages.BRANCH_ID + " = b." + Branches.ID + " JOIN "
                    + AnswerRevisions.TABLE + " ar ON ar." + AnswerRevisions.ID + " = m."
                    + Messages.ACTIVE_REVISION_ID;
            case PARTIAL_ANSWER, PARTIAL_REASONING -> branches + " JOIN " + Messages.TABLE
                    + " m ON m." + Messages.BRANCH_ID + " = b." + Branches.ID + " JOIN "
                    + GenerationOperations.TABLE + " go ON go." + GenerationOperations.ID + " = m."
                    + Messages.ACTIVE_OPERATION_ID;
        };
    }

    private static String like(String column) {
        return column + " LIKE ? ESCAPE '\\'";
    }

    /** 只搜已结束且未产出版本的尝试的部分内容（FAILED/INTERRUPTED/CANCELED）。 */
    private static String failedAttempt(String partialColumn) {
        return "go." + GenerationOperations.STATE + " IN ('" + GenerationState.FAILED.name()
                + "','" + GenerationState.INTERRUPTED.name() + "','" + GenerationState.CANCELED.name()
                + "') AND " + like(partialColumn);
    }

    private static String branchColumn(String column) {
        return "b." + column;
    }

    private static String messageColumn(String column) {
        return "m." + column;
    }

    // ------------------------------------------------------------------------- stats

    @GetMapping("/api/stats")
    SearchStatsDtos.Stats stats(@RequestParam("timeZone") String timeZone,
            @RequestParam(name = "month", required = false) String month) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timeZone);
        } catch (DateTimeException malformed) {
            throw requestInvalid();
        }
        YearMonth requested;
        if (month == null) {
            requested = YearMonth.from(LocalDate.now(clock.withZone(zone)));
        } else {
            try {
                requested = YearMonth.parse(month);
            } catch (DateTimeException malformed) {
                throw requestInvalid();
            }
        }

        long totalMessages = count("SELECT COUNT(*) FROM " + Messages.TABLE);
        long totalQuestions = count("SELECT COUNT(*) FROM " + Messages.TABLE + " WHERE "
                + Messages.ROLE + " = '" + MessageRole.USER.name() + "'");
        List<Long> answerTimestamps = jdbc.queryList(
                "SELECT " + AnswerRevisions.CREATED_AT + " FROM " + AnswerRevisions.TABLE,
                JdbcSupport.NO_PARAMS, rows -> Long.valueOf(rows.getLong(1)));

        LocalDate today = LocalDate.now(clock.withZone(zone));
        TreeMap<LocalDate, Long> answersPerDay = new TreeMap<>();
        for (Long timestamp : answerTimestamps) {
            LocalDate day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
            answersPerDay.merge(day, 1L, Long::sum);
        }
        List<LocalDate> activeDays = new ArrayList<>(answersPerDay.keySet());

        List<Integer> monthCells = monthCells(requested, today, answersPerDay);
        return new SearchStatsDtos.Stats(totalMessages == 0 && answerTimestamps.isEmpty(),
                timeZone, requested.toString(), (int) totalQuestions, (int) totalMessages,
                currentStreak(activeDays, today),
                activeDays.stream().map(LocalDate::toString).toList(), monthCells);
    }

    /** 7×6 热力格：周一为首列；规则见类注释（未来整月全 -1，补位格 0，未到的日子 -1）。 */
    private static List<Integer> monthCells(YearMonth requested, LocalDate today,
            TreeMap<LocalDate, Long> answersPerDay) {
        List<Integer> cells = new ArrayList<>(MONTH_CELL_COUNT);
        int firstWeekday = requested.atDay(1).getDayOfWeek().getValue() - 1;
        int daysInMonth = requested.lengthOfMonth();
        boolean futureMonth = requested.isAfter(YearMonth.from(today));
        for (int cell = 0; cell < MONTH_CELL_COUNT; cell++) {
            int day = cell - firstWeekday + 1;
            if (futureMonth) {
                cells.add(-1);
            } else if (day < 1 || day > daysInMonth) {
                cells.add(0);
            } else if (requested.atDay(day).isAfter(today)) {
                cells.add(-1);
            } else {
                cells.add(answersPerDay.getOrDefault(requested.atDay(day), 0L).intValue());
            }
        }
        return cells;
    }

    /**
     * 连续活跃日：截至今天或昨天（今天尚未学习不打断），中断一天即断——逐日镜像
     * {@code StatsAggregator.currentStreak}。
     */
    private static int currentStreak(List<LocalDate> activeDays, LocalDate today) {
        if (activeDays.isEmpty()) {
            return 0;
        }
        int lastIndex = activeDays.size() - 1;
        LocalDate last = activeDays.get(lastIndex);
        if (last.isAfter(today) || last.isBefore(today.minusDays(1))) {
            return 0;
        }
        int streak = 1;
        for (int i = lastIndex - 1; i >= 0; i--) {
            if (!activeDays.get(i).equals(activeDays.get(i + 1).minusDays(1))) {
                break;
            }
            streak++;
        }
        return streak;
    }

    private long count(String sql) {
        return jdbc.queryOne(sql, JdbcSupport.NO_PARAMS, rows -> Long.valueOf(rows.getLong(1)))
                .orElse(0L);
    }

    private static ServiceRefusalException requestInvalid() {
        return new ServiceRefusalException("REQUEST_INVALID", Map.of("httpStatus", 400));
    }
}
