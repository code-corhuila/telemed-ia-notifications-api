package com.telemed.notifications.adapter.out.persistence;

import com.telemed.notifications.domain.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataNotificationRepository
        extends JpaRepository<NotificationJpaEntity, UUID> {

    Optional<NotificationJpaEntity> findBySourceEventIdAndRecipientUserId(
            UUID sourceEventId,
            UUID recipientUserId
    );

    @Query("""
            SELECT n FROM NotificationJpaEntity n
            WHERE n.recipientUserId = :recipient
              AND n.type IN :types
              AND (:unreadOnly = false OR n.readAt IS NULL)
            """)
    Page<NotificationJpaEntity> findInbox(
            @Param("recipient") UUID recipientUserId,
            @Param("types") Collection<NotificationType> types,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable
    );

    long countByRecipientUserIdAndTypeInAndReadAtIsNull(
            UUID recipientUserId,
            Collection<NotificationType> types
    );

    boolean existsByIdAndRecipientUserIdAndTypeIn(
            UUID id,
            UUID recipientUserId,
            Collection<NotificationType> types
    );

    @Modifying
    @Query("""
            UPDATE NotificationJpaEntity n SET n.readAt = :readAt
            WHERE n.id = :id
              AND n.recipientUserId = :recipient
              AND n.type IN :types
              AND n.readAt IS NULL
            """)
    int markRead(
            @Param("id") UUID id,
            @Param("recipient") UUID recipientUserId,
            @Param("types") Collection<NotificationType> types,
            @Param("readAt") OffsetDateTime readAt
    );

    @Modifying
    @Query("""
            UPDATE NotificationJpaEntity n SET n.readAt = :readAt
            WHERE n.recipientUserId = :recipient
              AND n.type IN :types
              AND n.readAt IS NULL
            """)
    int markAllRead(
            @Param("recipient") UUID recipientUserId,
            @Param("types") Collection<NotificationType> types,
            @Param("readAt") OffsetDateTime readAt
    );
}
