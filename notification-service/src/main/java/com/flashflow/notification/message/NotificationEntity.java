package com.flashflow.notification.message;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification")
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(
            UUID eventId,
            Long userId,
            String templateCode,
            String content
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.channel = "IN_APP";
        this.templateCode = templateCode;
        this.content = content;
        this.status = "CREATED";
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getChannel() {
        return channel;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getContent() {
        return content;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
