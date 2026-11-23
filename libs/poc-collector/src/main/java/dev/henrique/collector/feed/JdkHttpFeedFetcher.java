package dev.henrique.collector.feed;

import dev.henrique.collector.source.DnsResolver;
import dev.henrique.collector.source.PublicHttpsFeedUrlPolicy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class JdkHttpFeedFetcher implements FeedFetcher {
    public static final int MAX_RESPONSE_BYTES = 1_000_000;
    private static final int MAX_HEADER_BYTES = 16_384;
    private static final int MAX_LINE_BYTES = 8_192;
    private final DnsResolver resolver;

    public JdkHttpFeedFetcher() {
        this(host -> InetAddress.getAllByName(host));
    }

    public JdkHttpFeedFetcher(DnsResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public byte[] fetch(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new FeedFetchException("Feed request must use public HTTPS on port 443.");
        }
        final InetAddress[] addresses;
        try {
            addresses = PublicHttpsFeedUrlPolicy.resolvePublicAddresses(uri.getHost(), resolver);
        } catch (UnknownHostException e) {
            throw new FeedFetchException("Feed host could not be resolved to a public address.", e);
        }

        FeedFetchException lastError = null;
        for (InetAddress address : addresses) {
            try {
                return fetch(uri, address);
            } catch (IOException e) {
                lastError = new FeedFetchException("Feed request failed.", e);
            }
        }
        throw lastError == null ? new FeedFetchException("Feed request failed.") : lastError;
    }

    private byte[] fetch(URI uri, InetAddress address) throws IOException {
        int port = uri.getPort() == -1 ? 443 : uri.getPort();
        var socket = new java.net.Socket();
        socket.connect(new InetSocketAddress(address, port), 5_000);
        socket.setSoTimeout(10_000);
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket connectedSocket;
        try {
            connectedSocket = (SSLSocket) socketFactory.createSocket(socket, uri.getHost(), port, true);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        try (SSLSocket tls = connectedSocket) {
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            if (!isIpAddress(uri.getHost())) parameters.setServerNames(List.of(new SNIHostName(uri.getHost())));
            tls.setSSLParameters(parameters);
            tls.startHandshake();

            OutputStream output = tls.getOutputStream();
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            String hostHeader = uri.getHost() + (port == 443 ? "" : ":" + port);
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + hostHeader + "\r\n"
                    + "Accept: application/rss+xml, application/atom+xml, application/xml, text/xml\r\n"
                    + "Accept-Encoding: identity\r\n"
                    + "Connection: close\r\n"
                    + "User-Agent: MultiSourceCollectorMini/0.1 (+private project feed reader)\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();

            try (InputStream input = tls.getInputStream()) {
                HttpHeaders response = readHeaders(input);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new FeedFetchException("Feed endpoint returned HTTP " + response.statusCode() + ".");
                }
                String contentEncoding = response.first("content-encoding");
                if (contentEncoding != null && !contentEncoding.equalsIgnoreCase("identity")) {
                    throw new FeedFetchException("Feed response used an unsupported content encoding.");
                }
                String transferEncoding = response.first("transfer-encoding");
                if (transferEncoding != null) {
                    if (!transferEncoding.equalsIgnoreCase("chunked")) {
                        throw new FeedFetchException("Feed response used an unsupported transfer encoding.");
                    }
                    return readChunkedBody(input);
                }
                String contentLength = response.first("content-length");
                if (contentLength != null) return readLengthBody(input, contentLength);
                return readUntilEof(input);
            }
        }
    }

    private static HttpHeaders readHeaders(InputStream input) throws IOException {
        int totalBytes = 0;
        String status = readLine(input, MAX_LINE_BYTES);
        totalBytes += status.length() + 2;
        String[] statusParts = status.split(" ", 3);
        if (statusParts.length < 2 || !statusParts[0].startsWith("HTTP/1.")) {
            throw new FeedFetchException("Feed endpoint returned an invalid HTTP response.");
        }
        final int statusCode;
        try {
            statusCode = Integer.parseInt(statusParts[1]);
        } catch (NumberFormatException e) {
            throw new FeedFetchException("Feed endpoint returned an invalid HTTP status.", e);
        }
        Map<String, List<String>> headers = new LinkedHashMap<>();
        while (true) {
            String line = readLine(input, MAX_LINE_BYTES);
            totalBytes += line.length() + 2;
            if (totalBytes > MAX_HEADER_BYTES) throw new FeedFetchException("Feed response headers exceed the size limit.");
            if (line.isEmpty()) break;
            if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                throw new FeedFetchException("Feed endpoint returned an invalid HTTP header.");
            }
            int separator = line.indexOf(':');
            if (separator <= 0) throw new FeedFetchException("Feed endpoint returned an invalid HTTP header.");
            String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        if (headers.getOrDefault("content-length", List.of()).size() > 1) {
            throw new FeedFetchException("Feed endpoint returned ambiguous content length.");
        }
        if (headers.getOrDefault("transfer-encoding", List.of()).size() > 1
                || headers.getOrDefault("content-encoding", List.of()).size() > 1) {
            throw new FeedFetchException("Feed endpoint returned ambiguous transfer metadata.");
        }
        return new HttpHeaders(statusCode, headers);
    }

    private static byte[] readLengthBody(InputStream input, String value) throws IOException {
        final long length;
        try {
            length = Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new FeedFetchException("Feed endpoint returned an invalid content length.", e);
        }
        if (length < 0 || length > MAX_RESPONSE_BYTES) {
            throw new FeedFetchException("Feed response exceeds the size limit.");
        }
        byte[] bytes = input.readNBytes((int) length);
        if (bytes.length != length) throw new FeedFetchException("Feed response ended before its content length.");
        return bytes;
    }

    private static byte[] readChunkedBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int chunkCount = 0;
        int trailerBytes = 0;
        while (true) {
            if (++chunkCount > 10_000) throw new FeedFetchException("Feed response has too many chunks.");
            String line = readLine(input, MAX_LINE_BYTES);
            int extension = line.indexOf(';');
            String sizeText = (extension < 0 ? line : line.substring(0, extension)).trim();
            final int chunkSize;
            try {
                chunkSize = Integer.parseUnsignedInt(sizeText, 16);
            } catch (NumberFormatException e) {
                throw new FeedFetchException("Feed endpoint returned an invalid chunk.", e);
            }
            if (chunkSize == 0) {
                String trailer;
                while (!(trailer = readLine(input, MAX_LINE_BYTES)).isEmpty()) {
                    trailerBytes += trailer.length() + 2;
                    if (trailerBytes > MAX_HEADER_BYTES) {
                        throw new FeedFetchException("Feed response trailers exceed the size limit.");
                    }
                }
                return output.toByteArray();
            }
            if (output.size() + (long) chunkSize > MAX_RESPONSE_BYTES) {
                throw new FeedFetchException("Feed response exceeds the size limit.");
            }
            byte[] chunk = input.readNBytes(chunkSize);
            if (chunk.length != chunkSize || input.read() != '\r' || input.read() != '\n') {
                throw new FeedFetchException("Feed response contains an incomplete chunk.");
            }
            output.write(chunk);
        }
    }

    private static byte[] readUntilEof(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) throw new FeedFetchException("Feed response exceeds the size limit.");
        return bytes;
    }

    private static String readLine(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        while (line.size() <= maxBytes) {
            int next = input.read();
            if (next < 0) throw new FeedFetchException("Feed response ended unexpectedly.");
            if (previous == '\r' && next == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.ISO_8859_1);
            }
            line.write(next);
            previous = next;
        }
        throw new FeedFetchException("Feed response line exceeds the size limit.");
    }

    private static boolean isIpAddress(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    private record HttpHeaders(int statusCode, Map<String, List<String>> values) {
        String first(String name) {
            List<String> entries = values.get(name);
            return entries == null || entries.isEmpty() ? null : entries.getFirst();
        }
    }
}
