package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.telegram.entity.TelegramBotCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramBotCommandRepository extends JpaRepository<TelegramBotCommand, Long> {

    Optional<TelegramBotCommand> findByCommand(String command);

    List<TelegramBotCommand> findByIsActiveTrue();

    @Modifying
    @Query("UPDATE TelegramBotCommand c SET c.usageCount = c.usageCount + 1 WHERE c.command = :command")
    void incrementUsageCount(@Param("command") String command);
}
