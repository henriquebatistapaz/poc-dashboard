package dev.henrique.dashboard.repository;

import dev.henrique.dashboard.domain.MastodonConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MastodonConnectionRepository extends JpaRepository<MastodonConnection, String> {
}
