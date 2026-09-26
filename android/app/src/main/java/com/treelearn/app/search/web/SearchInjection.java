package com.treelearn.app.search.web;

import java.util.List;

/**
 * Builds the grounding block sent with a question that had web search enabled (T-092).
 *
 * <p>Everything inside an entry's title and snippet is untrusted third-party text: a line break
 * inside either field would let a crafted search result forge the block's own frame - fake
 * "rank. title（domain）" entry lines or planted instructions - which then travels to the model
 * as part of the current input. Both fields are flattened to a single line before the length
 * cap, so the only line structure in the block is the structure this class wrote.
 */
public final class SearchInjection {
    private SearchInjection() {
    }

    public static String block(String query, List<WebSearchGateway.Item> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(4064);
        out.append("【联网检索结果】\n")
                .append("以下是针对当前问题的联网检索条目（按相关度排序，检索时间以条目返回为准）。")
                .append("回答时可参考以下条目，并在引用处用【序号·域名】标注对应来源，未参考的内容不要标注：\n")
                .append("（问题：").append(cap(nullToEmpty(query), 100)).append("）\n");
        int fitted = 0;
        for (WebSearchGateway.Item item : items) {
            StringBuilder entry = new StringBuilder();
            entry.append(item.index).append(". ").append(sanitize(item.title, 80))
                    .append("（").append(item.domain).append("）\n")
                    .append(sanitize(item.snippet, 200)).append('\n');
            if (out.length() + entry.length() > 4000) {
                break;
            }
            out.append(entry);
            fitted++;
        }
        return fitted == 0 ? "" : out.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** The query echo is the learner's own text; only its length is capped. */
    private static String cap(String value, int limit) {
        String text = nullToEmpty(value);
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    /**
     * Untrusted field: every line separator, tab and other Unicode line break becomes one space,
     * then the length cap applies. Guarantees the field occupies exactly one line.
     */
    private static String sanitize(String value, int limit) {
        String flat = nullToEmpty(value).replaceAll(
                "[\\t\\r\\n\\u000B\\u000C\\u001C-\\u001F\\u0085\\u2028\\u2029]+", " ");
        return flat.length() <= limit ? flat : flat.substring(0, limit);
    }
}
