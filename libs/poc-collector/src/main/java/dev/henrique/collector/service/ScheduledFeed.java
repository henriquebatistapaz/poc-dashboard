package dev.henrique.collector.service;

import dev.henrique.collector.source.CollectorSource;
import java.time.Instant;

public record ScheduledFeed(String id, CollectorSource source, String encryptedInput,
                            long intervalSeconds, Instant nextRunAt, boolean enabled,
                            String lastStatus) { }
