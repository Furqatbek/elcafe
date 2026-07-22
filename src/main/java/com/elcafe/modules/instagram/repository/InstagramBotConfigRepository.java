package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstagramBotConfigRepository extends JpaRepository<InstagramBotConfig, Long> {

    Optional<InstagramBotConfig> findByIsActiveTrue();
}
