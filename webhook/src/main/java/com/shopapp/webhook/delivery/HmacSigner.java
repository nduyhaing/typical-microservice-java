package com.shopapp.webhook.delivery;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code X-Hub-Signature-256} value: {@code sha256=} + lower-case hex HMAC-SHA256 of the raw body bytes, keyed with
 * the webhook's secret (UTF-8). YAS turned the raw MAC bytes into a String (not verifiable by receivers) and signed
 * {@code JsonNode.toString()} rather than the bytes sent (decisions.md 5-33).
 */
public final class HmacSigner {

    public static final String HEADER = "X-Hub-Signature-256";
    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigner() {
    }

    public static String sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
