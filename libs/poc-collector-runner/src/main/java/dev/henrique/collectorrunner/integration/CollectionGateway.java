package dev.henrique.collectorrunner.integration;

import java.util.List;

@FunctionalInterface
public interface CollectionGateway {
    List<CollectedItemInput> collect(CollectionSource source, String input);
}
