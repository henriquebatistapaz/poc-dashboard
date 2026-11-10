package com.henrique.collector.source;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

public final class PublicHttpsFeedUrlPolicy {
    private final DnsResolver resolver;

    public PublicHttpsFeedUrlPolicy() {
        this(host -> InetAddress.getAllByName(host));
    }

    public PublicHttpsFeedUrlPolicy(DnsResolver resolver) {
        this.resolver = resolver;
    }

    public URI validate(String value) {
        if (value == null || value.isBlank() || value.length() > 2_048) {
            throw new IllegalArgumentException("A public HTTPS feed URL is required.");
        }
        final URI uri;
        try {
            uri = URI.create(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Feed URL is invalid.");
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                || uri.getFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || host.equalsIgnoreCase("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            throw new IllegalArgumentException("Feed URL must use public HTTPS without credentials or a custom port.");
        }
        try {
            InetAddress[] addresses = resolver.resolve(host);
            if (addresses.length == 0) throw new IllegalArgumentException("Feed host has no public address.");
            for (InetAddress address : addresses) {
                if (!isPublic(address)) throw new IllegalArgumentException("Feed host resolves to a non-public address.");
            }
            return uri;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Feed host could not be resolved.");
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = bytes[0] & 255;
            int b = bytes[1] & 255;
            int c = bytes[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && (b == 0 || b == 168)) || (a == 198 && (b == 18 || b == 19 || b == 51))
                    || (a == 203 && b == 0 && c == 113));
        }
        // IPv6 unique-local (fc00::/7) and documentation (2001:db8::/32) are not public destinations.
        return !((bytes[0] & 0xFE) == 0xFC || ((bytes[0] & 255) == 0x20 && (bytes[1] & 255) == 0x01
                && (bytes[2] & 255) == 0x0D && (bytes[3] & 255) == 0xB8));
    }
}
