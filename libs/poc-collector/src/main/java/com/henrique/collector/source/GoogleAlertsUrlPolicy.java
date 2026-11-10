package com.henrique.collector.source;

import java.net.URI;
import java.util.regex.Pattern;

public final class GoogleAlertsUrlPolicy {
    private static final Pattern FEED_PATH = Pattern.compile("^/alerts/feeds/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/?$");

    public URI validate(String value) {
        if (value == null || value.isBlank() || value.length() > 2_048) {
            throw new IllegalArgumentException("A Google Alerts feed URL is required.");
        }
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !(host.equalsIgnoreCase("www.google.com") || host.equalsIgnoreCase("google.com"))
                    || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getRawQuery() != null || !FEED_PATH.matcher(uri.getPath()).matches()) {
                throw new IllegalArgumentException("Google Alerts URL must be an HTTPS Google Alerts RSS feed URL.");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Google Alerts URL")) throw e;
            throw new IllegalArgumentException("Google Alerts URL is invalid.");
        }
    }
}
