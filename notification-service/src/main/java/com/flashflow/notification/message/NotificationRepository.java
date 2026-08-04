package com.flashflow.notification.message;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findTop100ByUserIdOrderByCreatedAtDesc(long userId);
}
