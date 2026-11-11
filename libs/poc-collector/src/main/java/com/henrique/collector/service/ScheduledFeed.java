package com.henrique.collector.service;

import com.henrique.collector.source.CollectorSource;
import java.time.Instant;

public record ScheduledFeed(String id, CollectorSource source, String encryptedInput,
                            long intervalSeconds, Instant nextRunAt, boolean enabled,
                            String lastStatus) { }
