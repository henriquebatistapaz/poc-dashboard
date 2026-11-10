package com.henrique.collector.source;

public record SourceCapability(CollectorSource source, CapabilityStatus status, String reason) { }
