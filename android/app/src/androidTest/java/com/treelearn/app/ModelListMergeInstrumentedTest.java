package com.treelearn.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.treelearn.ai.gateway.AiGateway;
import com.treelearn.ai.gateway.AiGatewayError;
import com.treelearn.ai.gateway.GatewayEndpoints;
import com.treelearn.ai.gateway.GatewayPorts;
import com.treelearn.ai.gateway.ProviderEndpoint;
import com.treelearn.ai.gateway.ProviderJson;
import com.treelearn.data.db.TreeLearnDatabaseHelper;
import com.treelearn.data.db.TreeLearnSchema;
import com.treelearn.data.db.dao.ModelDao;
import com.treelearn.data.db.dao.ProviderDao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * T-2026-09-23-086 acceptance, device half: the {@code /models} fetch through the real platform
 * {@code org.json} decoder (the JVM suites script the decoder, per repo convention), and the
 * {@link ModelDao#mergeRemote} write on real Android SQLite — insert-only, case-exact, duplicates
 * silently skipped, the store never reduced.
 */
@RunWith(AndroidJUnit4.class)
public final class ModelListMergeInstrumentedTest {

    private static final long T0 = 1_756_000_000_000L;

    private Context context;
    private TreeLearnDatabaseHelper helper;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
        helper = new TreeLearnDatabaseHelper(context);
    }

    @After
    public void tearDown() {
        if (helper != null) {
            helper.close();
        }
        context.deleteDatabase(TreeLearnDatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void listModelsParsesTheDataIdsThroughTheRealPlatformDecoder() {
        FakeTransport transport = new FakeTransport();
        transport.body = "{\"object\":\"list\",\"data\":[{\"id\":\"deepseek-chat\","
                + "\"object\":\"model\"},{\"id\":\"deepseek-reasoner\"}]}";
        AiGateway gateway = new AiGateway(transport, (model, request) -> new byte[0],
                payload -> GatewayPorts.Delta.EMPTY, payload -> true,
                new ProviderJson.ModelList(), (name, details) -> { }, Runnable::run);

        AiGateway.Models models = gateway.listModels("key",
                ProviderEndpoint.of("https://api.other.example/v1", "m"));

        assertTrue(models.ok());
        assertEquals(List.of("deepseek-chat", "deepseek-reasoner"), models.ids);
        assertEquals("https://api.other.example/v1" + GatewayEndpoints.MODELS_PATH,
                transport.requestedUrl);
    }

    @Test
    public void aMissingDataArrayIsRefusedAsAParseFailureWithNoIds() {
        FakeTransport transport = new FakeTransport();
        transport.body = "{\"models\":[]}";
        AiGateway gateway = new AiGateway(transport, (model, request) -> new byte[0],
                payload -> GatewayPorts.Delta.EMPTY, payload -> true,
                new ProviderJson.ModelList(), (name, details) -> { }, Runnable::run);

        AiGateway.Models models = gateway.listModels("key",
                ProviderEndpoint.of("https://api.other.example/v1", "m"));

        assertTrue(models.ids.isEmpty());
        assertNotNull(models.error);
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, models.error.kind);
    }

    @Test
    public void anEntryWithoutAStringIdIsRefusedAsAParseFailure() {
        FakeTransport transport = new FakeTransport();
        transport.body = "{\"data\":[{\"id\":\"ok\"},{\"nope\":true}]}";
        AiGateway gateway = new AiGateway(transport, (model, request) -> new byte[0],
                payload -> GatewayPorts.Delta.EMPTY, payload -> true,
                new ProviderJson.ModelList(), (name, details) -> { }, Runnable::run);

        AiGateway.Models models = gateway.listModels("key",
                ProviderEndpoint.of("https://api.other.example/v1", "m"));

        assertTrue(models.ids.isEmpty());
        assertEquals(AiGatewayError.Kind.PARSE_FAILED, models.error.kind);
    }

    @Test
    public void mergeRemoteInsertsOnlyThePlannedRowsAndNeverReducesTheStore() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ProviderDao.insert(db, new ProviderDao.Row("p-fetch", "FetchProv",
                "https://fetch.example", true, false, T0, T0));
        ModelDao.insert(db, new ModelDao.Row("m-kept", "p-fetch", "kept-model", "Kept", T0));

        AtomicInteger uuid = new AtomicInteger();
        int added = ModelDao.mergeRemote(db, "p-fetch",
                List.of("kept-model", "remote-a", "remote-b", "remote-a", "KEPT-MODEL"),
                () -> "m-gen-" + uuid.incrementAndGet());

        assertEquals("only the unseen, case-exact ids land, remote duplicates skipped", 3, added);
        List<String> modelIds = modelIds(db, "p-fetch");
        assertEquals(List.of("kept-model", "remote-a", "remote-b", "KEPT-MODEL"), modelIds);

        // A later fetch that comes back shorter keeps every local entry (只增不删).
        assertEquals(0, (long) ModelDao.mergeRemote(db, "p-fetch", List.of("remote-a"),
                () -> "m-gen-" + uuid.incrementAndGet()));
        assertEquals(4, ModelDao.findByProvider(db, "p-fetch").size());
    }

    @Test
    public void aFailedFetchNeverTouchesTheModelTable() {
        SQLiteDatabase db = helper.getWritableDatabase();
        ProviderDao.insert(db, new ProviderDao.Row("p-off", "OffProv",
                "https://off.example", true, false, T0, T0));
        ModelDao.insert(db, new ModelDao.Row("m-x", "p-off", "model-x", "X", T0));

        // The [OFF] semantics: a caller merges only on ok(); a failed result carries no ids,
        // so the merge call — and therefore any write — never happens.
        FakeTransport transport = new FakeTransport();
        transport.status = 500;
        AiGateway gateway = new AiGateway(transport, (model, request) -> new byte[0],
                payload -> GatewayPorts.Delta.EMPTY, payload -> true,
                new ProviderJson.ModelList(), (name, details) -> { }, Runnable::run);
        AiGateway.Models models = gateway.listModels("key",
                ProviderEndpoint.of("https://off.example", "m"));

        assertTrue(models.ids.isEmpty());
        assertEquals(AiGatewayError.Kind.PROVIDER_UNAVAILABLE, models.error.kind);
        assertEquals(1, ModelDao.findByProvider(db, "p-off").size());
    }

    private static List<String> modelIds(SQLiteDatabase db, String providerId) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (ModelDao.Row row : ModelDao.findByProvider(db, providerId)) {
            ids.add(row.modelId);
        }
        return ids;
    }

    /** One scripted HTTP response (DeepSeekStreamInstrumentedTest pattern). */
    private static final class FakeTransport implements GatewayPorts.Transport {

        String body = "";
        int status = 200;
        String requestedUrl;
        String requestedKey;

        @Override
        public GatewayPorts.Response post(String url, String apiKey, byte[] body) {
            throw new UnsupportedOperationException("the model list fetch must use GET");
        }

        @Override
        public GatewayPorts.Response get(String url, String apiKey) {
            requestedUrl = url;
            requestedKey = apiKey;
            return new GatewayPorts.Response() {
                @Override
                public int status() {
                    return status;
                }

                @Override
                public InputStream body() {
                    return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public void close() {
                    // The fixture holds no connection.
                }
            };
        }
    }
}
