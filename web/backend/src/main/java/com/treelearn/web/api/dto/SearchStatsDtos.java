package com.treelearn.web.api.dto;

import java.util.List;

/**
 * 搜索与统计域 DTO（契约 {@code web/contracts/api.md} §5.13、§5.14、§6.7、§6.8）。
 *
 * <p>搜索命中区间 {@code start}/{@code end} 为相对 snippet 的 UTF-16 半开区间；{@code messageId}/
 * {@code revisionId} 为 null 表示非消息命中，{@code messageOrdinal=-1}/{@code revisionNo=0} 表示无消息/无版本。
 * {@code Stats.activeDays} 与 {@code month} 为 date-only 字符串，{@code monthCells} 为当月热力格（-1 为未来/空）。
 */
public final class SearchStatsDtos {

    private SearchStatsDtos() {}

    /** §5.13 SearchResult。 */
    public record SearchResult(
            String query,
            List<SearchResultHit> hits) {}

    /** §5.13 单条命中。 */
    public record SearchResultHit(
            String field,
            String topicId,
            String branchId,
            String messageId,
            int messageOrdinal,
            String revisionId,
            int revisionNo,
            String snippet,
            int start,
            int end) {}

    /** §5.14 Stats。 */
    public record Stats(
            boolean empty,
            String timeZone,
            String month,
            int totalQuestions,
            int totalMessages,
            int currentStreakDays,
            List<String> activeDays,
            List<Integer> monthCells) {}
}
