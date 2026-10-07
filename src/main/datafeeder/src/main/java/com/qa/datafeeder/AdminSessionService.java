package com.qa.datafeeder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminSessionService {

    private static final Duration SESSION_LIFETIME = Duration.ofHours(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String adminUsername;
    private final String adminPassword;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public AdminSessionService(
            @Value("${datafeeder.admin.username:admin}") String adminUsername,
            @Value("${datafeeder.admin.password:admin123}") String adminPassword) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public String login(String username, String password) {
        if (!matches(username, adminUsername) || !matches(password, adminPassword)) {
            return null;
        }

        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        sessions.put(token, Instant.now().plus(SESSION_LIFETIME));
        return token;
    }

    public boolean isValid(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        Instant expiresAt = sessions.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            sessions.remove(token, expiresAt);
            return false;
        }
        return true;
    }

    private boolean matches(String candidate, String expected) {
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
