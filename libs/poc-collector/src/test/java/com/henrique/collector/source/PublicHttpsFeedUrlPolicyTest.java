package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import com.henrique.collector.feed.FeedFetchException;
import com.henrique.collector.feed.JdkHttpFeedFetcher;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PublicHttpsFeedUrlPolicyTest {
    private final PublicHttpsFeedUrlPolicy policy = new PublicHttpsFeedUrlPolicy(host ->
            new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})});

    @Test
    void rejectsPrivateAndNonHttpsTargets() {
        var privatePolicy = new PublicHttpsFeedUrlPolicy(host ->
                new InetAddress[]{InetAddress.getByAddress(new byte[]{10, 0, 0, 5})});
        assertThrows(IllegalArgumentException.class, () -> privatePolicy.validate("https://internal.example/feed"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("http://blog.example/feed"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://localhost/feed"));
    }

    @Test
    void rejectsUrlWithUserInfoOrUnexpectedPort() {
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://user:pass@blog.example/feed"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://blog.example:8443/feed"));
    }

    @Test
    void rejectsReservedAndTunneledIpRanges() throws Exception {
        assertFalse(PublicHttpsFeedUrlPolicy.isPublic(InetAddress.getByName("192.0.0.1")));
        assertFalse(PublicHttpsFeedUrlPolicy.isPublic(InetAddress.getByName("192.0.2.1")));
        assertTrue(PublicHttpsFeedUrlPolicy.isPublic(InetAddress.getByName("192.0.78.17")));
        assertFalse(PublicHttpsFeedUrlPolicy.isPublic(InetAddress.getByName("192.88.99.1")));
        assertFalse(PublicHttpsFeedUrlPolicy.isPublic(InetAddress.getByName("2001:db8::1")));
        assertFalse(PublicHttpsFeedUrlPolicy.isPublic(InetAddress.getByName("2002:0808:0808::1")));
        assertFalse(PublicHttpsFeedUrlPolicy.isPublic(InetAddress.getByName("64:ff9b::808:808")));
        assertTrue(PublicHttpsFeedUrlPolicy.isPublic(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void rejectsDnsRebindingBeforeOpeningConnection() {
        AtomicInteger resolutions = new AtomicInteger();
        DnsResolver rebindingResolver = host -> {
            if (resolutions.getAndIncrement() == 0) {
                return new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})};
            }
            return new InetAddress[]{InetAddress.getByAddress(new byte[]{10, 0, 0, 5})};
        };
        var fetcher = new JdkHttpFeedFetcher(rebindingResolver);
        var uri = new PublicHttpsFeedUrlPolicy(rebindingResolver).validate("https://blog.example/feed/atom");

        assertThrows(FeedFetchException.class, () -> fetcher.fetch(uri));
        assertEquals(2, resolutions.get());
    }
}
