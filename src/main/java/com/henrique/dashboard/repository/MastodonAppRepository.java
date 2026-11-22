package com.henrique.dashboard.repository;

import com.henrique.dashboard.domain.MastodonApp;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MastodonAppRepository extends JpaRepository<MastodonApp, String> {
}
