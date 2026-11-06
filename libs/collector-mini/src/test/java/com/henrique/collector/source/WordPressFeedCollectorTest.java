package com.henrique.collector.source;

import static org.junit.jupiter.api.Assertions.*;

import com.henrique.collector.feed.RssFeedParser;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WordPressFeedCollectorTest {
    private final PublicHttpsFeedUrlPolicy policy = new PublicHttpsFeedUrlPolicy(host ->
            new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})});

    @Test
    void acceptsPublicHttpsAndParsesAtom() {
        byte[] xml;
        try (var input = getClass().getResourceAsStream("/feeds/wordpress-atom.xml")) {
            xml = input.readAllBytes();
        } catch (Exception e) { throw new AssertionError(e); }
        var collector = new WordPressFeedCollector(uri -> xml, new RssFeedParser(), policy);
        var items = collector.collect("https://blog.example/feed/atom");
        assertEquals(1, items.size());
        assertEquals(CollectorSource.WORDPRESS, items.getFirst().source());
    }

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
}
