package app.freerouting.api;

import app.freerouting.Freerouting;
import app.freerouting.settings.GlobalSettings;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AuthenticationIntegrationTest {
  private Server apiServer;
  private HttpServer authServer;
  private int port;
  private UUID resolvedId;

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

    apiServer = Freerouting.InitializeAPI(gs.apiServerSettings);
    Thread.sleep(500);
    port = ((ServerConnector) apiServer.getConnectors()[0]).getLocalPort();
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
  void authenticatedRequest() throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/v1/sessions/list"))
        .header("Freerouting-Profile-Email", "test@example.com")
        .build();
    HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, resp.statusCode());
  }

  @Test
  void unauthenticatedRequest() throws Exception {
    HttpRequest req = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/v1/sessions/list"))
        .build();
    HttpResponse<String> resp = HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(401, resp.statusCode());
  }
}
