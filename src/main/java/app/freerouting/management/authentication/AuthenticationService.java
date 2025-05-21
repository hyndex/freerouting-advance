package app.freerouting.management.authentication;

import app.freerouting.logger.FRLogger;
import app.freerouting.management.gson.GsonProvider;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * Simple client for the Freerouting authentication service.
 */
public class AuthenticationService {
  private final String endpoint;

  public AuthenticationService(String endpoint) {
    this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
  }

  /**
   * Resolve a user id from an e-mail address.
   */
  public UUID resolveUserId(String email) {
    try {
      String url = endpoint + "/resolve?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
      HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        JsonObject obj = GsonProvider.GSON.fromJson(response.body(), JsonObject.class);
        if (obj.has("id")) {
          return UUID.fromString(obj.get("id").getAsString());
        }
      }
    } catch (Exception e) {
      FRLogger.error("Failed to resolve user id: " + e.getMessage(), e);
    }
    return null;
  }

  /**
   * Validate that the given user exists.
   */
  public boolean validateUser(UUID userId, String email) {
    try {
      JsonObject payload = new JsonObject();
      if (userId != null) {
        payload.addProperty("id", userId.toString());
      }
      if (email != null && !email.isEmpty()) {
        payload.addProperty("email", email);
      }
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(endpoint + "/validate"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(GsonProvider.GSON.toJson(payload)))
          .build();
      HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (Exception e) {
      FRLogger.error("Failed to validate user: " + e.getMessage(), e);
      return false;
    }
  }
}
