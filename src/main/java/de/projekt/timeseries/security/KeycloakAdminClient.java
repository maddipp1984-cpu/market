package de.projekt.timeseries.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);

    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public KeycloakAdminClient(
            @Value("${keycloak.admin.url}") String baseUrl,
            @Value("${keycloak.admin.realm:timeseries}") String realm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper;
    }

    private synchronized String getToken() throws IOException, InterruptedException {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        String tokenUrl = baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        String body = "grant_type=client_credentials"
            + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
            + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Token request failed: " + response.statusCode() + " " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        cachedToken = json.get("access_token").asText();
        int expiresIn = json.get("expires_in").asInt();
        tokenExpiry = Instant.now().plusSeconds(expiresIn - 30); // 30s buffer
        return cachedToken;
    }

    private String adminUrl() {
        return baseUrl + "/admin/realms/" + realm;
    }

    public String createUser(String username, String email, String password) throws IOException, InterruptedException {
        String token = getToken();

        ObjectNode userNode = objectMapper.createObjectNode();
        userNode.put("username", username);
        userNode.put("email", email);
        userNode.put("enabled", true);

        // Set password
        ObjectNode credential = objectMapper.createObjectNode();
        credential.put("type", "password");
        credential.put("value", password);
        credential.put("temporary", false);
        ArrayNode credentials = objectMapper.createArrayNode();
        credentials.add(credential);
        userNode.set("credentials", credentials);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(adminUrl() + "/users"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(userNode)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201) {
            // Extract user ID from Location header
            String location = response.headers().firstValue("Location").orElse("");
            return location.substring(location.lastIndexOf('/') + 1);
        } else if (response.statusCode() == 409) {
            throw new IllegalStateException("User '" + username + "' existiert bereits in Keycloak");
        } else {
            throw new IOException("Keycloak createUser failed: " + response.statusCode() + " " + response.body());
        }
    }

    public List<Map<String, Object>> listUsers() throws IOException, InterruptedException {
        String token = getToken();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(adminUrl() + "/users?max=1000"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Keycloak listUsers failed: " + response.statusCode());
        }

        JsonNode arr = objectMapper.readTree(response.body());
        List<Map<String, Object>> users = new ArrayList<>();
        for (JsonNode node : arr) {
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", node.get("id").asText());
            user.put("username", node.has("username") ? node.get("username").asText() : null);
            user.put("email", node.has("email") ? node.get("email").asText() : null);
            user.put("enabled", node.has("enabled") && node.get("enabled").asBoolean());
            users.add(user);
        }
        return users;
    }

    public void setEnabled(String userId, boolean enabled) throws IOException, InterruptedException {
        String token = getToken();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("enabled", enabled);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(adminUrl() + "/users/" + userId))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) {
            throw new IOException("Keycloak setEnabled failed: " + response.statusCode());
        }
    }

    public void resetPassword(String userId, String newPassword) throws IOException, InterruptedException {
        String token = getToken();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", "password");
        body.put("value", newPassword);
        body.put("temporary", false);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(adminUrl() + "/users/" + userId + "/reset-password"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) {
            throw new IOException("Keycloak resetPassword failed: " + response.statusCode());
        }
    }
}
