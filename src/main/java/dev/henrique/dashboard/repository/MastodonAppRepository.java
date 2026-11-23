package dev.henrique.dashboard.repository;

import dev.henrique.dashboard.domain.MastodonApp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MastodonAppRepository extends JpaRepository<MastodonApp, String> {
}
