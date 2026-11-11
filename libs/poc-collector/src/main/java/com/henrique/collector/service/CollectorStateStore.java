package com.henrique.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.henrique.collector.feed.FeedItem;
import com.henrique.collector.source.CollectorSource;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CollectorStateStore {
    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<FeedItem> items = new ArrayList<>();
    private final List<ScheduledFeed> feeds = new ArrayList<>();

    public CollectorStateStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
        load();
    }

    public synchronized void addItems(List<FeedItem> newItems) {
        Set<String> known = new java.util.HashSet<>();
        for (FeedItem item : items) known.add(identity(item));
        for (FeedItem item : newItems) {
            if (known.add(identity(item))) items.add(item);
        }
        persist();
    }

    public synchronized List<FeedItem> items(CollectorSource source, int limit) {
        return items.stream()
                .filter(item -> source == null || item.source() == source)
                .sorted(Comparator.comparing(FeedItem::retrievedAt).reversed())
                .limit(limit)
                .toList();
    }

    public synchronized int deleteExpired(Instant cutoff) {
        int before = items.size();
        items.removeIf(item -> item.retrievedAt().isBefore(cutoff));
        int removed = before - items.size();
        if (removed > 0) persist();
        return removed;
    }

    public synchronized ScheduledFeed createFeed(CollectorSource source, String encryptedInput,
                                                   long intervalSeconds, Instant nextRunAt) {
        ScheduledFeed feed = new ScheduledFeed(UUID.randomUUID().toString(), source, encryptedInput,
                intervalSeconds, nextRunAt, true, "NEVER_RUN");
        feeds.add(feed);
        persist();
        return feed;
    }

    public synchronized List<ScheduledFeed> feeds() {
        return List.copyOf(feeds);
    }

    public synchronized List<ScheduledFeed> dueFeeds(Instant now) {
        return feeds.stream().filter(ScheduledFeed::enabled)
                .filter(feed -> !feed.nextRunAt().isAfter(now)).toList();
    }

    public synchronized void completeFeed(String id, Instant nextRunAt, String status) {
        for (int i = 0; i < feeds.size(); i++) {
            ScheduledFeed feed = feeds.get(i);
            if (feed.id().equals(id)) {
                feeds.set(i, new ScheduledFeed(feed.id(), feed.source(), feed.encryptedInput(),
                        feed.intervalSeconds(), nextRunAt, feed.enabled(), status));
                persist();
                return;
            }
        }
    }

    public synchronized boolean deleteFeed(String id) {
        boolean removed = feeds.removeIf(feed -> feed.id().equals(id));
        if (removed) persist();
        return removed;
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            JsonNode root = mapper.readTree(file.toFile());
            for (JsonNode node : root.path("items")) {
                JsonNode published = node.get("publishedAt");
                items.add(new FeedItem(
                        CollectorSource.valueOf(node.path("source").asText()),
                        node.path("sourceId").asText(),
                        node.path("title").asText(),
                        URI.create(node.path("link").asText()),
                        published == null || published.isNull() ? null : Instant.parse(published.asText()),
                        Instant.parse(node.path("retrievedAt").asText())));
            }
            for (JsonNode node : root.path("feeds")) {
                feeds.add(new ScheduledFeed(
                        node.path("id").asText(), CollectorSource.valueOf(node.path("source").asText()),
                        node.path("encryptedInput").asText(), node.path("intervalSeconds").asLong(),
                        Instant.parse(node.path("nextRunAt").asText()), node.path("enabled").asBoolean(),
                        node.path("lastStatus").asText("NEVER_RUN")));
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Collector data file is invalid; refusing to overwrite it.");
        }
    }

    private void persist() {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode itemArray = root.putArray("items");
        for (FeedItem item : items) {
            ObjectNode node = itemArray.addObject();
            node.put("source", item.source().name());
            node.put("sourceId", item.sourceId());
            node.put("title", item.title());
            node.put("link", item.link().toString());
            if (item.publishedAt() == null) node.putNull("publishedAt");
            else node.put("publishedAt", item.publishedAt().toString());
            node.put("retrievedAt", item.retrievedAt().toString());
        }
        ArrayNode feedArray = root.putArray("feeds");
        for (ScheduledFeed feed : feeds) {
            ObjectNode node = feedArray.addObject();
            node.put("id", feed.id());
            node.put("source", feed.source().name());
            node.put("encryptedInput", feed.encryptedInput());
            node.put("intervalSeconds", feed.intervalSeconds());
            node.put("nextRunAt", feed.nextRunAt().toString());
            node.put("enabled", feed.enabled());
            node.put("lastStatus", feed.lastStatus());
        }
        Path parent = file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
            restrictPermissions(temporary);
            mapper.writeValue(temporary.toFile(), root);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(file);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save collector data.", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private static void restrictPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            path.toFile().setReadable(false, false);
            path.toFile().setWritable(false, false);
            path.toFile().setReadable(true, true);
            path.toFile().setWritable(true, true);
        }
    }

    private static String identity(FeedItem item) {
        return item.source().name() + "\u0000" + item.sourceId();
    }
}
