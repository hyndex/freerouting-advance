package app.freerouting.api;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.Session;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.management.SessionManager;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.settings.RouterSettings;
import com.sun.net.httpserver.HttpServer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JobCancellationIntegrationTest {
  private Server apiServer;
  private HttpServer authServer;
  private int port;
  private UUID resolvedId;
  private RoutingJobScheduler scheduler;
  private RoutingJob job;

  @BeforeAll
  void setup() throws Exception {
    // start mock auth server
    resolvedId = UUID.randomUUID();
    authServer = HttpServer.create(new InetSocketAddress(0), 0);
    authServer.createContext("/resolve", exchange -> {
      String response = "{\"id\":\"" + resolvedId + "\"}";
      exchange.sendResponseHeaders(200, response.getBytes().length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(response.getBytes());
      }
    });
    authServer.createContext("/validate", exchange -> {
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    authServer.start();

    GlobalSettings gs = new GlobalSettings();
    gs.apiServerSettings.isEnabled = true;
    gs.apiServerSettings.isHttpAllowed = true;
    gs.apiServerSettings.endpoints = new String[]{"http://localhost:0"};
    gs.authServiceSettings.endpoint = "http://localhost:" + authServer.getAddress().getPort();
    Freerouting.globalSettings = gs;

    scheduler = RoutingJobScheduler.getInstance();
    apiServer = Freerouting.InitializeAPI(gs.apiServerSettings);
    Thread.sleep(500);
    port = ((ServerConnector) apiServer.getConnectors()[0]).getLocalPort();

    // Create session and job directly
    Session session = SessionManager.getInstance().createSession(resolvedId, "Freerouting/IntegrationTest");
    job = new RoutingJob(session.id);
    byte[] data = Files.readAllBytes(Path.of("tests/Issue026-J2_reference.dsn"));
    job.setInput(data);
    RouterSettings settings = new RouterSettings(job.input.statistics.layers.totalCount);
    settings.setRunFanout(false);
    settings.setRunOptimizer(false);
    settings.maxPasses = 1;
    job.setSettings(settings);
    scheduler.enqueueJob(job);
    job.state = RoutingJobState.READY_TO_START;

    // wait until job thread is running
    while (job.thread == null) {
      Thread.sleep(50);
    }
    while (job.state != RoutingJobState.RUNNING) {
      Thread.sleep(50);
    }
  }

  @AfterAll
  void tearDown() throws Exception {
    if (apiServer != null) {
      apiServer.stop();
    }
    if (authServer != null) {
      authServer.stop(0);
    }
  }

  @Test
  void cancelRunningJob() throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/v1/jobs/" + job.id + "/cancel"))
        .header("Freerouting-Profile-Email", "test@example.com")
        .PUT(HttpRequest.BodyPublishers.noBody())
        .build();
    HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, resp.statusCode());

    RoutingJob result = GsonProvider.GSON.fromJson(resp.body(), RoutingJob.class);
    assertEquals(RoutingJobState.CANCELLED, result.state);

    // give scheduler a little time to cleanup
    Thread.sleep(200);
    assertNull(job.thread);
    assertEquals(RoutingJobState.CANCELLED, scheduler.getJob(job.id.toString()).state);
  }
}
