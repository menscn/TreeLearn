package com.treelearn.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.treelearn.core.domain.AnswerRevision;
import com.treelearn.core.domain.GenerationModel;
import com.treelearn.web.api.dto.ApiJson;
import com.treelearn.web.storage.AnswerRevisionStore;
import com.treelearn.web.storage.BranchStore;
import com.treelearn.web.storage.DesktopSchema;
import com.treelearn.web.storage.JdbcSupport;
import com.treelearn.web.storage.MessageStore;
import com.treelearn.web.storage.TopicStore;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * T-2026-10-10-043：应用内搜索与学习统计端点（契约 {@code web/contracts/api.md} §6.7/§6.8、§5.13/§5.14）。
 *
 * <p>真实 HTTP + 真 SQLite + 固定 UTC 时钟：覆盖空查询/无结果、主题/分支/消息三范围命中与定位、命中区间、
 * 归档不可搜、统计与 DB 同源、跨自然日边界的设备时区归属、连续学习天数边界、空月零值、参数非法。
 *
 * <p>每个用例前清空主题表（级联清空分支/消息/版本/操作/快照），使统计与搜索的期望值与库状态确定对应。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SearchStatsEndpointsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final String POLICY = "tl-learning-answer/1.1.0";
    /** 固定「现在」，让「今天/当月」与连续天数的断言可复现（Asia/Shanghai 为 2026-10-05）。 */
    private static final Instant FIXED_NOW = Instant.parse("2026-10-05T12:00:00Z");

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString().replace('\\', '/'));
    }

    @LocalServerPort int port;
    @Autowired JdbcSupport jdbc;
    @Autowired TopicStore topics;
    @Autowired BranchStore branches;
    @Autowired MessageStore messages;
    @Autowired AnswerRevisionStore revisions;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetStore() {
        jdbc.update("DELETE FROM " + DesktopSchema.Topics.TABLE, JdbcSupport.NO_PARAMS);
    }

    // ------------------------------------------------------------------ search

    @Test
    void blankQueryReturnsEmptyHitsWithoutReachingTheStore() throws Exception {
        seedTopic("光合作用研究", "光合作用是什么", "绿色植物的光合作用很关键", "", T0);

        JsonNode blank = search("");
        assertEquals("", blank.get("query").asText());
        assertEquals(0, blank.get("hits").size());

        JsonNode whitespace = search("   ");
        assertEquals(0, whitespace.get("hits").size(), "去空白后为空的查询不得返回任何命中");
    }

    @Test
    void unknownTermReturnsNoHitsWithoutError() throws Exception {
        seedTopic("光合作用研究", "光合作用是什么", "绿色植物的光合作用很关键", "", T0);
        assertEquals(0, search("zzz-no-such-term").get("hits").size());
    }

    @Test
    void threeScopesReturnLocatedHitsInDisplayOrder() throws Exception {
        Seed seed = seedTopic("光合作用研究", "光合作用是什么", "绿色植物的光合作用很关键", "", T0);

        JsonNode hits = search("光合作用").get("hits");
        assertEquals(3, hits.size(), "标题 + 提问 + 回答三范围各命中一次");

        JsonNode title = hits.get(0);
        assertEquals("TITLE", title.get("field").asText());
        assertEquals(seed.topicId, title.get("topicId").asText());
        assertEquals(seed.rootBranchId, title.get("branchId").asText());
        assertTrue(title.get("messageId").isNull(), "标题命中不绑定消息");
        assertEquals(-1, title.get("messageOrdinal").asInt());
        assertTrue(title.get("revisionId").isNull());
        assertEquals(0, title.get("revisionNo").asInt());

        JsonNode question = hits.get(1);
        assertEquals("QUESTION", question.get("field").asText());
        assertEquals(seed.userMessageId, question.get("messageId").asText());
        assertEquals(0, question.get("messageOrdinal").asInt());
        assertTrue(question.get("revisionId").isNull());
        assertEquals(0, question.get("revisionNo").asInt());

        JsonNode answer = hits.get(2);
        assertEquals("ANSWER", answer.get("field").asText());
        assertEquals(seed.aiMessageId, answer.get("messageId").asText());
        assertEquals(1, answer.get("messageOrdinal").asInt());
        assertEquals(seed.revisionId, answer.get("revisionId").asText());
        assertEquals(1, answer.get("revisionNo").asInt());
    }

    @Test
    void hitIntervalIsUtf16WithinTheSnippet() throws Exception {
        seedTopic("光合作用研究", "光合作用是什么", "绿色植物的光合作用很关键", "", T0);

        JsonNode hits = search("光合作用").get("hits");
        JsonNode title = hits.get(0);
        assertEquals("光合作用研究", title.get("snippet").asText());
        assertEquals(0, title.get("start").asInt());
        assertEquals(4, title.get("end").asInt());

        JsonNode answer = hits.get(2);
        assertEquals("绿色植物的光合作用很关键", answer.get("snippet").asText());
        assertEquals(5, answer.get("start").asInt());
        assertEquals(9, answer.get("end").asInt());
    }

    @Test
    void asciiMatchIsCaseInsensitiveAndIntervalTracksTheSnippet() throws Exception {
        seedTopic("Photosynthesis", "what is photosynthesis", "Photosynthesis uses light", null, T0);

        JsonNode hits = search("PHOTOSYNTHESIS").get("hits");
        assertEquals(3, hits.size());
        JsonNode answer = hits.get(2);
        assertEquals("ANSWER", answer.get("field").asText());
        assertEquals(0, answer.get("start").asInt());
        assertEquals(14, answer.get("end").asInt());
    }

    @Test
    void archivedBranchIsNotSearchable() throws Exception {
        Seed seed = seedTopic("光合作用研究", "光合作用是什么", "绿色植物的光合作用很关键", "", T0);

        jdbc.update("UPDATE " + DesktopSchema.Branches.TABLE + " SET "
                + DesktopSchema.Branches.ARCHIVED_AT + " = ? WHERE " + DesktopSchema.Branches.ID
                + " = ?", s -> {
            s.setLong(1, T0); s.setString(2, seed.rootBranchId);
        });

        assertEquals(0, search("光合作用").get("hits").size(), "归档分支的文本不可搜");
    }

    // ------------------------------------------------------------------- stats

    @Test
    void emptyLibraryReturnsZeroStructure() throws Exception {
        JsonNode body = stats("Asia/Shanghai", null);
        assertTrue(body.get("empty").asBoolean());
        assertEquals(0, body.get("totalQuestions").asInt());
        assertEquals(0, body.get("totalMessages").asInt());
        assertEquals(0, body.get("currentStreakDays").asInt());
        assertEquals(0, body.get("activeDays").size());
        assertEquals(42, body.get("monthCells").size());
        assertEquals("2026-10", body.get("month").asText());
    }

    @Test
    void totalsAndDetailShareOneSource() throws Exception {
        seedTopic("t", "问题一", "答案一", "", ms("2026-10-05T04:00:00Z"));

        JsonNode body = stats("Asia/Shanghai", null);
        assertFalse(body.get("empty").asBoolean());
        assertEquals(1, body.get("totalQuestions").asInt());
        assertEquals(2, body.get("totalMessages").asInt());
        assertEquals(1, body.get("activeDays").size());
        assertEquals("2026-10-05", body.get("activeDays").get(0).asText());
        assertEquals(1, body.get("currentStreakDays").asInt());

        // 总量与 DB 计数同一来源
        assertEquals(1, countDb("SELECT COUNT(*) FROM messages WHERE role='USER'"));
        assertEquals(2, countDb("SELECT COUNT(*) FROM messages"));
    }

    @Test
    void crossNaturalDayBoundaryFollowsDeviceTimeZone() throws Exception {
        seedTopic("t", "q", "a", "", ms("2026-10-03T16:30:00Z"));

        JsonNode shanghai = stats("Asia/Shanghai", null);
        assertEquals("Asia/Shanghai", shanghai.get("timeZone").asText());
        assertEquals(1, shanghai.get("activeDays").size());
        assertEquals("2026-10-04", shanghai.get("activeDays").get(0).asText());
        assertEquals(1, monthCell(shanghai, "2026-10", 4));

        JsonNode losAngeles = stats("America/Los_Angeles", null);
        assertEquals("2026-10-03", losAngeles.get("activeDays").get(0).asText());
        assertEquals(1, monthCell(losAngeles, "2026-10", 3));
    }

    @Test
    void streakCountsConsecutiveDaysAndBreaksOnAGap() throws Exception {
        seedTopic("t1", "q1", "a1", "", ms("2026-10-01T04:00:00Z"));
        seedTopic("t2", "q2", "a2", "", ms("2026-10-03T04:00:00Z"));
        seedTopic("t3", "q3", "a3", "", ms("2026-10-04T04:00:00Z"));
        seedTopic("t4", "q4", "a4", "", ms("2026-10-04T14:00:00Z"));
        seedTopic("t5", "q5", "a5", "", ms("2026-10-05T04:00:00Z"));

        JsonNode body = stats("Asia/Shanghai", null);
        assertEquals(4, body.get("activeDays").size(), "同日多次作答只算一个活跃日");
        assertEquals(3, body.get("currentStreakDays").asInt(), "10-03/04/05 连续，10-02 中断");
    }

    @Test
    void streakEndingYesterdayStillCounts() throws Exception {
        seedTopic("t1", "q1", "a1", "", ms("2026-10-03T04:00:00Z"));
        seedTopic("t2", "q2", "a2", "", ms("2026-10-04T04:00:00Z"));

        JsonNode body = stats("Asia/Shanghai", null);
        assertEquals(2, body.get("currentStreakDays").asInt(), "今天尚未学习不打断截至昨天的连续段");
    }

    @Test
    void emptyRequestedMonthReturnsBlankCellsNotAnError() throws Exception {
        seedTopic("t", "q", "a", "", ms("2026-10-04T04:00:00Z"));

        JsonNode past = stats("Asia/Shanghai", "2026-08");
        assertEquals("2026-08", past.get("month").asText());
        for (JsonNode cell : past.get("monthCells")) {
            assertEquals(0, cell.asInt(), "过去月无活跃日 → 全部 0");
        }
        assertEquals(1, past.get("activeDays").size(), "activeDays 全量，不按月裁剪");

        JsonNode future = stats("Asia/Shanghai", "2026-12");
        assertEquals("2026-12", future.get("month").asText());
        for (JsonNode cell : future.get("monthCells")) {
            assertEquals(-1, cell.asInt(), "未来月 → 全部空月格");
        }
    }

    @Test
    void monthDefaultsToTheCurrentMonthInTheTimeZone() throws Exception {
        assertEquals("2026-10", stats("Asia/Shanghai", null).get("month").asText());
        assertEquals("2026-10", stats("America/Los_Angeles", null).get("month").asText());
    }

    @Test
    void invalidTimeZoneAndMonthAreRequestInvalid() throws Exception {
        assertRefusal(getRaw("/api/stats?timeZone=Not%2FAZone"), 400, "REQUEST_INVALID");
        assertRefusal(getRaw("/api/stats?timeZone=Asia%2FShanghai&month=2026-13"), 400,
                "REQUEST_INVALID");
    }

    // -------------------------------------------------------------------- helpers

    private record Seed(String topicId, String rootBranchId, String userMessageId, String aiMessageId,
            String revisionId) {}

    private Seed seedTopic(String title, String question, String answer, String reasoning, long now) {
        TopicStore.Created created = topics.create(now);
        String rootId = created.rootBranch.id;
        if (title != null) {
            branches.renameTitle(rootId, title, now);
        }
        String user = messages.appendUserMessage(rootId, 0, question, "", now).id;
        String ai = messages.appendAiPosition(rootId, 1, now).id;
        AnswerRevision revision = revisions.insert(ai, reasoning == null ? "" : reasoning, answer,
                GenerationModel.FLASH, false, POLICY, now);
        messages.setActiveRevision(ai, revision.id);
        return new Seed(created.topic.id, rootId, user, ai, revision.id);
    }

    private static long ms(String isoInstant) {
        return Instant.parse(isoInstant).toEpochMilli();
    }

    private JsonNode search(String q) throws Exception {
        return get("/api/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
    }

    private JsonNode stats(String timeZone, String month) throws Exception {
        String path = "/api/stats?timeZone=" + URLEncoder.encode(timeZone, StandardCharsets.UTF_8);
        if (month != null) {
            path = path + "&month=" + URLEncoder.encode(month, StandardCharsets.UTF_8);
        }
        return get(path);
    }

    private int countDb(String sql) {
        return jdbc.queryOne(sql, JdbcSupport.NO_PARAMS, rows -> Integer.valueOf(rows.getInt(1)))
                .orElse(0);
    }

    /** 当月 7×6 网格中某一天（day-of-month）所在格的值；周一为首列。 */
    private static int monthCell(JsonNode stats, String month, int day) {
        int offset = YearMonth.parse(month).atDay(1).getDayOfWeek().getValue() - 1;
        return stats.get("monthCells").get(offset + day - 1).asInt();
    }

    private JsonNode get(String path) throws Exception {
        HttpResponse<String> response = getRaw(path);
        assertEquals(200, response.statusCode(), "GET " + path + " -> " + response.body());
        return ApiJson.mapper().readTree(response.body());
    }

    private HttpResponse<String> getRaw(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertRefusal(HttpResponse<String> response, int status, String code)
            throws Exception {
        assertEquals(status, response.statusCode(), "body: " + response.body());
        assertEquals(code,
                ApiJson.mapper().readTree(response.body()).get("error").get("code").asText());
    }

    private String base() {
        return "http://127.0.0.1:" + port;
    }

    // -------------------------------------------------------------- test wiring

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
