package dev.henrique.collectoroauth;

import java.time.Instant;

/** Post público retornado pela busca. O id é local à instância e deve ser tratado como texto. */
public record MastodonPost(String id, String url, String accountAcct, String text, Instant createdAt) {
}
