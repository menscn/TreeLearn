package com.treelearn.app.search.web;

import com.treelearn.app.ui.KeySnapshot;
import com.treelearn.app.ui.SearchSettingsPresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * The search-grounding doubles shared by the T-092 suites: a preference store answered from fields,
 * a credential source answered from a buffer, and a gateway that records what it was asked and
 * returns what a test scripted.
 */
public final class SearchTestDoubles {

    private SearchTestDoubles() {
    }

    /** The search settings port answered from public fields, with every write recorded. */
    public static final class FakeStore implements SearchSettingsPresenter.Store {

        public boolean searchEnabled;
        public SearchSettingsPresenter.Engine engine = SearchSettingsPresenter.Engine.ZHIPU;
        public SearchSettingsPresenter.Tier tier = SearchSettingsPresenter.Tier.TEN;
        public boolean hasKey;
        public int setSearchEnabledCalls;

        @Override
        public boolean searchEnabled() {
            return searchEnabled;
        }

        @Override
        public void setSearchEnabled(boolean enabled) {
            searchEnabled = enabled;
            setSearchEnabledCalls++;
        }

        @Override
        public SearchSettingsPresenter.Engine engine() {
            return engine;
        }

        @Override
        public void setEngine(SearchSettingsPresenter.Engine value) {
            engine = value;
        }

        @Override
        public SearchSettingsPresenter.Tier tier() {
            return tier;
        }

        @Override
        public void setTier(SearchSettingsPresenter.Tier value) {
            tier = value;
        }

        @Override
        public boolean hasKey(SearchSettingsPresenter.Engine candidate) {
            return hasKey;
        }

        @Override
        public KeySnapshot readKey(SearchSettingsPresenter.Engine candidate) {
            return KeySnapshot.missing();
        }

        @Override
        public void saveKey(SearchSettingsPresenter.Engine candidate, char[] secret) {
        }

        @Override
        public void deleteKey(SearchSettingsPresenter.Engine candidate) {
        }
    }

    /** The credential source: hands out the scripted buffer (or null) and records the ask. */
    public static final class FakeCredentials implements SearchCredentials {

        public char[] key = new char[] {'k', 'e', 'y'};
        public int calls;

        @Override
        public char[] keyFor(SearchSettingsPresenter.Engine engine) {
            calls++;
            return key;
        }
    }

    /** The gateway seam: records the request, answers with the scripted outcome. */
    public static final class FakeGateway implements WebSearchGateway {

        public int calls;
        public SearchSettingsPresenter.Engine lastEngine;
        public String lastQuery;
        public int lastCount;
        public Outcome next = Outcome.empty();
        public RuntimeException throwNext;

        @Override
        public Outcome search(SearchSettingsPresenter.Engine engine, char[] key, String query,
                int resultCount) {
            calls++;
            lastEngine = engine;
            lastQuery = query;
            lastCount = resultCount;
            if (throwNext != null) {
                throw throwNext;
            }
            return next;
        }
    }

    public static WebSearchGateway.Item item(int index, String title, String domain,
            String snippet) {
        return new WebSearchGateway.Item(index, title, "https://" + domain + "/page-" + index,
                domain, snippet);
    }

    public static List<WebSearchGateway.Item> items(int count, String snippet) {
        List<WebSearchGateway.Item> list = new ArrayList<WebSearchGateway.Item>();
        for (int i = 1; i <= count; i++) {
            list.add(item(i, "title " + i, "example.com", snippet + " of " + i));
        }
        return list;
    }
}
