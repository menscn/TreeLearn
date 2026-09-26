/*
 * Decompiled with CFR 0.152.
 */
package com.treelearn.app.search.web;

import com.treelearn.app.search.web.WebSearchGateway;
import java.util.List;

public final class SearchInjection {
    private SearchInjection() {
    }

    public static String block(String query, List<WebSearchGateway.Item> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(4064);
        out.append("\u3010\u8054\u7f51\u68c0\u7d22\u7ed3\u679c\u3011\n").append("\u4ee5\u4e0b\u662f\u9488\u5bf9\u5f53\u524d\u95ee\u9898\u7684\u8054\u7f51\u68c0\u7d22\u6761\u76ee\uff08\u6309\u76f8\u5173\u5ea6\u6392\u5e8f\uff0c\u68c0\u7d22\u65f6\u95f4\u4ee5\u6761\u76ee\u8fd4\u56de\u4e3a\u51c6\uff09\u3002").append("\u56de\u7b54\u65f6\u53ef\u53c2\u8003\u4ee5\u4e0b\u6761\u76ee\uff0c\u5e76\u5728\u5f15\u7528\u5904\u7528\u3010\u5e8f\u53f7\u00b7\u57df\u540d\u3011\u6807\u6ce8\u5bf9\u5e94\u6765\u6e90\uff0c\u672a\u53c2\u8003\u7684\u5185\u5bb9\u4e0d\u8981\u6807\u6ce8\uff1a\n").append("\uff08\u95ee\u9898\uff1a").append(SearchInjection.trim(SearchInjection.nullToEmpty(query), 100)).append("\uff09\n");
        int fitted = 0;
        for (WebSearchGateway.Item item : items) {
            StringBuilder entry = new StringBuilder();
            entry.append(item.index).append(". ").append(SearchInjection.trim(item.title, 80)).append("\uff08").append(item.domain).append("\uff09\n").append(SearchInjection.trim(item.snippet, 200)).append('\n');
            if (out.length() + entry.length() > 4000) break;
            out.append((CharSequence)entry);
            ++fitted;
        }
        return fitted == 0 ? "" : out.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String value, int cap) {
        String text = SearchInjection.nullToEmpty(value);
        return text.length() <= cap ? text : text.substring(0, cap);
    }
}

