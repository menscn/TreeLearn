package com.treelearn.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * TDD target for task T-2026-10-10-003: the readiness contract is served over
 * real HTTP on the loopback port, not through a mock MVC layer, so the test
 * fails while the endpoint is missing and passes only when a real server
 * answers it.
 *
 * <p>T-2026-10-10-009 adds desktop storage startup to the context, so this test
 * points the data directory at a temporary location to keep the real user data
 * directory untouched. The readiness assertions are unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReadyEndpointTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void pointAtTemporaryDataDir(DynamicPropertyRegistry registry) {
        registry.add("treelearn.data-dir", () -> dataDir.toString());
    }

    @LocalServerPort
    private int port;

    @Test
    void readyEndpointAnswersOverRealHttpOnTheLoopbackPort() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/health/ready"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), "body was: " + response.body());
        assertTrue(response.body().contains("\"status\":\"ready\""),
                "body was: " + response.body());
    }
}
