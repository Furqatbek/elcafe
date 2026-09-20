package uz.megahotdog.modules.instagram.repository;

import uz.megahotdog.modules.instagram.entity.InstagramBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstagramBotConfigRepository extends JpaRepository<InstagramBotConfig, Long> {

    Optional<InstagramBotConfig> findByIsActiveTrue();
}
