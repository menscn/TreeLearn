package com.treelearn.web.storage;

import com.treelearn.core.domain.Annotation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Row access for {@code message_annotations}（桌面 schema 已有该表与两个索引）。
 *
 * <p><strong>clear-on-mismatch 读取只有一条写路径</strong>（镜像 Android
 * {@code AnnotationDao.listFor}，reviewer F2）：{@link #listFor} 在**同一事务**里先删除该消息下
 * {@code content_digest} 不再匹配当前显示答案的行、再查存活行——删除，永不重定位（拍板
 * 2026-09-28）。无论哪条流程替换了答案，下一次读取都会治愈本表，没有任何代码需要记得失效。
 * 删除与查询都在同一事务且查询自带 digest 过滤，交织写入无法把失配行混进返回列表。
 *
 * <p>时间点由调用方写入（rules/sql.md）：本 store 不读时钟。
 */
public final class AnnotationStore {

    private final JdbcSupport jdbc;

    public AnnotationStore(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** 单条插入。anchor 唯一性/CHECK/消息外键由表约束把守，冲突直接抛存储异常。 */
    public void insert(String messageId, String contentDigest, Annotation annotation, long at) {
        jdbc.update("INSERT INTO " + DesktopSchema.Annotations.TABLE + "("
                        + DesktopSchema.Annotations.ID + ", " + DesktopSchema.Annotations.MESSAGE_ID
                        + ", " + DesktopSchema.Annotations.CONTAINER + ", "
                        + DesktopSchema.Annotations.PROSE_INDEX + ", "
                        + DesktopSchema.Annotations.TABLE_INDEX + ", "
                        + DesktopSchema.Annotations.ROW_INDEX + ", "
                        + DesktopSchema.Annotations.COL_INDEX + ", "
                        + DesktopSchema.Annotations.START_OFFSET + ", "
                        + DesktopSchema.Annotations.END_OFFSET + ", "
                        + DesktopSchema.Annotations.KIND + ", "
                        + DesktopSchema.Annotations.CONTENT_DIGEST + ", "
                        + DesktopSchema.Annotations.BODY + ", "
                        + DesktopSchema.Annotations.CREATED_AT + ", "
                        + DesktopSchema.Annotations.UPDATED_AT
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s -> {
                    s.setString(1, annotation.id);
                    s.setString(2, messageId);
                    s.setString(3, annotation.locator.container.name());
                    s.setInt(4, annotation.locator.proseIndex);
                    s.setInt(5, annotation.locator.tableIndex);
                    s.setInt(6, annotation.locator.rowIndex);
                    s.setInt(7, annotation.locator.colIndex);
                    s.setInt(8, annotation.span.start);
                    s.setInt(9, annotation.span.end);
                    s.setString(10, annotation.kind.name());
                    s.setString(11, contentDigest);
                    s.setString(12, annotation.body);
                    s.setLong(13, at);
                    s.setLong(14, at);
                });
    }

    /**
     * 该消息 digest 仍匹配当前显示答案的批注，按锚点序返回——并先删除失配行（同事务，见类注释）。
     */
    public List<Annotation> listFor(String messageId, String contentDigest) {
        return jdbc.runInTransaction(connection -> {
            jdbc.update(connection,
                    "DELETE FROM " + DesktopSchema.Annotations.TABLE + " WHERE "
                            + DesktopSchema.Annotations.MESSAGE_ID + " = ? AND "
                            + DesktopSchema.Annotations.CONTENT_DIGEST + " <> ?",
                    s -> {
                        s.setString(1, messageId);
                        s.setString(2, contentDigest);
                    });
            return jdbc.queryList(connection,
                    "SELECT * FROM " + DesktopSchema.Annotations.TABLE + " WHERE "
                            + DesktopSchema.Annotations.MESSAGE_ID + " = ? AND "
                            + DesktopSchema.Annotations.CONTENT_DIGEST + " = ?"
                            + " ORDER BY " + DesktopSchema.Annotations.CONTAINER + " ASC, "
                            + DesktopSchema.Annotations.PROSE_INDEX + " ASC, "
                            + DesktopSchema.Annotations.TABLE_INDEX + " ASC, "
                            + DesktopSchema.Annotations.ROW_INDEX + " ASC, "
                            + DesktopSchema.Annotations.COL_INDEX + " ASC, "
                            + DesktopSchema.Annotations.START_OFFSET + " ASC",
                    s -> {
                        s.setString(1, messageId);
                        s.setString(2, contentDigest);
                    },
                    AnnotationStore::readAnnotation);
        });
    }

    /** 按批注 id 删除；返回是否确有行被删（幂等语义由控制器表达，不在这里）。 */
    public boolean delete(String annotationId) {
        return jdbc.update("DELETE FROM " + DesktopSchema.Annotations.TABLE + " WHERE "
                + DesktopSchema.Annotations.ID + " = ?", s -> s.setString(1, annotationId)) > 0;
    }

    /** 删除某消息的全部批注（重试/重置流程的失效清理入口；当前无调用方则留作对称读路径）。 */
    public int deleteForMessage(String messageId) {
        return jdbc.update("DELETE FROM " + DesktopSchema.Annotations.TABLE + " WHERE "
                + DesktopSchema.Annotations.MESSAGE_ID + " = ?", s -> s.setString(1, messageId));
    }

    private static Annotation readAnnotation(ResultSet rows) throws SQLException {
        Annotation.Kind kind = Annotation.Kind.valueOf(rows.getString(DesktopSchema.Annotations.KIND));
        Annotation.Locator locator = switch (rows.getString(DesktopSchema.Annotations.CONTAINER)) {
            case "CELL" -> Annotation.Locator.cell(
                    rows.getInt(DesktopSchema.Annotations.TABLE_INDEX),
                    rows.getInt(DesktopSchema.Annotations.ROW_INDEX),
                    rows.getInt(DesktopSchema.Annotations.COL_INDEX));
            default -> Annotation.Locator.prose(rows.getInt(DesktopSchema.Annotations.PROSE_INDEX));
        };
        String body = rows.getString(DesktopSchema.Annotations.BODY);
        if (kind == Annotation.Kind.NOTE) {
            return Annotation.note(rows.getString(DesktopSchema.Annotations.ID), locator,
                    rows.getInt(DesktopSchema.Annotations.START_OFFSET),
                    rows.getInt(DesktopSchema.Annotations.END_OFFSET), body);
        }
        return Annotation.highlight(rows.getString(DesktopSchema.Annotations.ID), locator,
                rows.getInt(DesktopSchema.Annotations.START_OFFSET),
                rows.getInt(DesktopSchema.Annotations.END_OFFSET));
    }

}
