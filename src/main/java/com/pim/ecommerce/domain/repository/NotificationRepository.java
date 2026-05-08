package com.pim.ecommerce.domain.repository;

import com.pim.ecommerce.domain.entity.Notification;
import com.pim.ecommerce.domain.entity.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);

    long countByUserIdAndTypeAndReadFalse(Long userId, NotificationType type);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.read = true,
                n.readAt = CURRENT_TIMESTAMP
            WHERE n.user.id = :userId
              AND n.read = false
            """)
    int markAllAsReadByUserId(@Param("userId") Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}