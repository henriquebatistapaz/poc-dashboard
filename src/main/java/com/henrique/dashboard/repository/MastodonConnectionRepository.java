package com.henrique.dashboard.repository;

import com.henrique.dashboard.domain.MastodonConnection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MastodonConnectionRepository extends JpaRepository<MastodonConnection, String> {
}
