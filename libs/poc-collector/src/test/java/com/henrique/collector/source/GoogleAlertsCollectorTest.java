package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import com.henrique.collector.feed.FeedFetchException;
import com.henrique.collector.feed.RssFeedParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GoogleAlertsCollectorTest {
    private final GoogleAlertsUrlPolicy policy = new GoogleAlertsUrlPolicy();

    @Test
    void acceptsOnlyLegacyGoogleAlertsFeedUrl() {
        URI uri = policy.validate("https://www.google.com/alerts/feeds/account-1/alert_2");
        assertEquals("www.google.com", uri.getHost());
    }

    @Test
    void rejectsOtherHostsSchemesAndPaths() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://evil.example/alerts/feeds/a/b"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("http://www.google.com/alerts/feeds/a/b"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://www.google.com/search?q=x"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://www.google.com/alerts/feeds/a/b?secret=x"));
    }

    @Test
    void collectsWithoutEchoingSecretUrlOnFetchFailure() {
        String secretUrl = "https://www.google.com/alerts/feeds/account-id/secret-alert-id";
        var collector = new GoogleAlertsCollector(uri -> { throw new FeedFetchException("URL leaked: " + uri); },
                new RssFeedParser(), policy);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> collector.collect(secretUrl));
        assertFalse(exception.getMessage().contains("secret-alert-id"));
        assertNull(exception.getCause());
    }

    @Test
    void failsClosedOnFetchTimeoutWithoutLeakingTheUrl() {
        String secretUrl = "https://www.google.com/alerts/feeds/account-id/secret-alert-id";
        var collector = new GoogleAlertsCollector(uri -> { throw new FeedFetchException("fixture timeout"); },
                new RssFeedParser(), policy);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> collector.collect(secretUrl));
        assertFalse(exception.getMessage().contains("secret-alert-id"));
        assertNull(exception.getCause());
    }

    @Test
    void parsesUserSuppliedFeedUsingAlertsSourceIdentity() {
        byte[] xml = "<rss><channel><item><title>Alert hit</title><link>https://example.org/a</link><guid>a-1</guid></item></channel></rss>"
                .getBytes(StandardCharsets.UTF_8);
        AtomicReference<URI> request = new AtomicReference<>();
        var collector = new GoogleAlertsCollector(uri -> { request.set(uri); return xml; }, new RssFeedParser(), policy);
        var result = collector.collect("https://google.com/alerts/feeds/u-1/a-1");
        assertEquals("google.com", request.get().getHost());
        assertEquals(CollectorSource.GOOGLE_ALERTS, result.getFirst().source());
    }
}
