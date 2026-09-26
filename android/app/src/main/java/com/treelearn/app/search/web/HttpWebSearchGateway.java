package com.treelearn.app.search.web;

import com.treelearn.app.search.web.WebSearchGateway;
import com.treelearn.app.ui.SearchSettingsPresenter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class HttpWebSearchGateway
implements WebSearchGateway {
    private static final int MAX_BODY_BYTES = 0x400000;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public WebSearchGateway.Outcome search(SearchSettingsPresenter.Engine engine, char[] key, String query, int resultCount) {
        Endpoint endpoint = HttpWebSearchGateway.endpoint(engine);
        try {
            byte[] body = endpoint.request(query, resultCount).getBytes(StandardCharsets.UTF_8);
            HttpsURLConnection connection = (HttpsURLConnection)new URL(endpoint.url).openConnection();
            try {
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setUseCaches(false);
                connection.setFixedLengthStreamingMode(body.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + new String(key));
                connection.setDoOutput(true);
                connection.getOutputStream().write(body);
                if (connection.getResponseCode() / 100 != 2) {
                    WebSearchGateway.Outcome outcome = WebSearchGateway.Outcome.failed();
                    return outcome;
                }
                WebSearchGateway.Outcome outcome = endpoint.parse(HttpWebSearchGateway.read(connection.getInputStream()));
                return outcome;
            }
            finally {
                connection.disconnect();
            }
        }
        catch (IOException | RuntimeException failed) {
            return WebSearchGateway.Outcome.failed();
        }
    }

    private static String read(InputStream stream) throws IOException {
        int read;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while ((read = stream.read(buffer)) != -1 && out.size() <= 0x400000) {
            out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
    }

    private static Endpoint endpoint(SearchSettingsPresenter.Engine engine) {
        switch (engine) {
            case TAVILY: {
                return new Endpoint("https://api.tavily.com/search"){

                    @Override
                    String request(String query, int resultCount) {
                        return HttpWebSearchGateway.json("query", query, "max_results", resultCount);
                    }

                    @Override
                    WebSearchGateway.Outcome parse(String payload) {
                        return HttpWebSearchGateway.fromArray(payload, "results", "title", "url", "content");
                    }
                };
            }
            case BOCHA: {
                return new Endpoint("https://api.bochaai.com/v1/web-search"){

                    @Override
                    String request(String query, int resultCount) {
                        return HttpWebSearchGateway.json("query", query, "count", resultCount, "summary", false);
                    }

                    @Override
                    WebSearchGateway.Outcome parse(String payload) {
                        try {
                            Object value;
                            JSONObject data = new JSONObject(payload).optJSONObject("data");
                            JSONObject pages = data == null ? null : data.optJSONObject("webPages");
                            Object object = value = pages == null ? null : pages.opt("value");
                            if (!(value instanceof JSONArray)) {
                                return WebSearchGateway.Outcome.failed();
                            }
                            return HttpWebSearchGateway.items((JSONArray)value, "name", "url", "snippet");
                        }
                        catch (RuntimeException | JSONException malformed) {
                            return WebSearchGateway.Outcome.failed();
                        }
                    }
                };
            }
        }
        return new Endpoint("https://open.bigmodel.cn/api/paas/v4/web_search"){

            @Override
            String request(String query, int resultCount) {
                return HttpWebSearchGateway.json("search_engine", "search_std", "search_query", query, "count", resultCount);
            }

            @Override
            WebSearchGateway.Outcome parse(String payload) {
                return HttpWebSearchGateway.fromArray(payload, "search_result", "title", "link", "content");
            }
        };
    }

    private static String json(Object ... pairs) {
        try {
            JSONObject object = new JSONObject();
            int i = 0;
            while (i + 1 < pairs.length) {
                object.put((String)pairs[i], pairs[i + 1]);
                i += 2;
            }
            return object.toString();
        }
        catch (JSONException impossible) {
            throw new IllegalStateException("a fixed shape cannot fail to encode", impossible);
        }
    }

    private static WebSearchGateway.Outcome fromArray(String payload, String arrayField, String titleField, String urlField, String snippetField) {
        try {
            Object entries = new JSONObject(payload).opt(arrayField);
            if (!(entries instanceof JSONArray)) {
                return WebSearchGateway.Outcome.failed();
            }
            return HttpWebSearchGateway.items((JSONArray)entries, titleField, urlField, snippetField);
        }
        catch (RuntimeException | JSONException malformed) {
            return WebSearchGateway.Outcome.failed();
        }
    }

    private static WebSearchGateway.Outcome items(JSONArray entries, String titleField, String urlField, String snippetField) {
        ArrayList<WebSearchGateway.Item> items = new ArrayList<WebSearchGateway.Item>();
        for (int i = 0; i < entries.length(); ++i) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) continue;
            String url = entry.optString(urlField, "");
            items.add(new WebSearchGateway.Item(items.size() + 1, entry.optString(titleField, ""), url, HttpWebSearchGateway.domain(url), entry.optString(snippetField, "")));
        }
        return items.isEmpty() ? WebSearchGateway.Outcome.empty() : WebSearchGateway.Outcome.ok(items);
    }

    private static String domain(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        }
        catch (Exception unreadable) {
            return "";
        }
    }

    private static abstract class Endpoint {
        final String url;

        Endpoint(String url) {
            this.url = url;
        }

        abstract String request(String var1, int var2);

        abstract WebSearchGateway.Outcome parse(String var1);
    }
}

