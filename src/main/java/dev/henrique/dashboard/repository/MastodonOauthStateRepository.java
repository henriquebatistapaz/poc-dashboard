package dev.henrique.dashboard.repository;

import dev.henrique.dashboard.domain.MastodonOauthState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MastodonOauthStateRepository extends JpaRepository<MastodonOauthState, String> {
}
