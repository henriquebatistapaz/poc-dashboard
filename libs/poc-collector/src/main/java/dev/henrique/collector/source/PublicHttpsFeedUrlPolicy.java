package dev.henrique.collector.source;

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
            resolvePublicAddresses(host, resolver);
            return uri;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Feed host could not be resolved.");
        }
    }

    public static InetAddress[] resolvePublicAddresses(String host, DnsResolver resolver) throws UnknownHostException {
        InetAddress[] addresses = resolver.resolve(host);
        if (addresses.length == 0) throw new UnknownHostException("Feed host has no address.");
        for (InetAddress address : addresses) {
            if (!isPublic(address)) throw new UnknownHostException("Feed host resolves to a non-public address.");
        }
        return addresses;
    }

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int a = bytes[0] & 255;
            int b = bytes[1] & 255;
            int c = bytes[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && ((b == 0 && (c == 0 || c == 2)) || b == 168))
                    || (a == 198 && (b == 18 || b == 19 || b == 51))
                    || (a == 192 && b == 88 && c == 99) || (a == 203 && b == 0 && c == 113));
        }
        int first = bytes[0] & 255;
        int second = bytes[1] & 255;
        int third = bytes[2] & 255;
        int fourth = bytes[3] & 255;
        boolean globalUnicast = (first & 0xE0) == 0x20;
        boolean protocolAssignment = first == 0x20 && second == 0x01 && (third & 0xFE) == 0;
        boolean documentation = first == 0x20 && second == 0x01 && third == 0x0D && fourth == 0xB8;
        boolean sixToFour = first == 0x20 && second == 0x02;
        boolean documentationV2 = first == 0x3F && second == 0xFF && (third & 0xF0) == 0;
        boolean nat64WellKnown = first == 0x00 && second == 0x64 && third == 0xFF && fourth == 0x9B;
        boolean nat64LocalUse = nat64WellKnown && bytes[4] == 0 && bytes[5] == 1;
        return globalUnicast && !protocolAssignment && !documentation && !sixToFour
                && !documentationV2 && !nat64WellKnown && !nat64LocalUse;
    }
}
