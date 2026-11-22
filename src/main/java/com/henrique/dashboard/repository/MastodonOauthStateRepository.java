package com.henrique.dashboard.repository;

import com.henrique.dashboard.domain.MastodonOauthState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MastodonOauthStateRepository extends JpaRepository<MastodonOauthState, String> {
}
