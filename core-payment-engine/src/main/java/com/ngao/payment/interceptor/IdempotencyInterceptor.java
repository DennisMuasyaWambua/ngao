package com.ngao.payment.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;

/**
 * Fast, race-free idempotency gate backed by Redis.
 *
 * <p>Every mutating payment request must carry an {@code X-Idempotency-Key}
 * header (a client-generated UUID). On the first sighting of a key we atomically
 * reserve it in Redis with a TTL; any later request bearing the same key is
 * rejected with HTTP 409 before it can reach the controller or the ledger.
 *
 * <p>The reservation uses {@code SET key value NX EX ttl} (via
 * {@link org.springframework.data.redis.core.ValueOperations#setIfAbsent}) which
 * is a single atomic Redis operation — avoiding the check-then-set race a naive
 * GET-then-SET would introduce under concurrent retries.
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);

    public static final String IDEMPOTENCY_HEADER = "X-Idempotency-Key";
    private static final String REDIS_KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public IdempotencyInterceptor(StringRedisTemplate redisTemplate,
                                  @Value("${app.idempotency.ttl-seconds:86400}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        String key = request.getHeader(IDEMPOTENCY_HEADER);

        if (key == null || key.isBlank()) {
            return reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required header: " + IDEMPOTENCY_HEADER, null);
        }

        String redisKey = REDIS_KEY_PREFIX + key;

        // Atomic "reserve if new": returns TRUE only when the key did not exist.
        Boolean firstSeen = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "RESERVED", ttl);

        if (Boolean.FALSE.equals(firstSeen)) {
            log.warn("Duplicate request rejected: idempotency key '{}' already seen", key);
            return reject(response, HttpServletResponse.SC_CONFLICT,
                    "Duplicate request: idempotency key already processed", key);
        }

        log.debug("Idempotency key '{}' reserved for {}", key, ttl);
        // Expose the validated key to downstream handlers.
        request.setAttribute(IDEMPOTENCY_HEADER, key);
        return true;
    }

    private boolean reject(HttpServletResponse response, int status, String message, String key)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = (key == null)
                ? "{\"error\":\"" + message + "\"}"
                : "{\"error\":\"" + message + "\",\"idempotencyKey\":\"" + key + "\"}";
        response.getWriter().write(body);
        return false; // short-circuit: never reach the controller
    }
}
