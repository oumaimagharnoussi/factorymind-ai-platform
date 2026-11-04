package com.factorymind.notification.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByEventKey(String eventKey);

    /** The feed of a user: what their roles may see, newest first, optionally only what they have not read. */
    @Query(value = """
            select n from Notification n
            where exists (select 1 from Notification v join v.audience r where v = n and r in :roles)
              and (:unreadOnly = false or not exists (
                    select 1 from NotificationRead nr where nr.notificationId = n.id and nr.username = :username))
            order by n.createdAt desc
            """,
            countQuery = """
            select count(n) from Notification n
            where exists (select 1 from Notification v join v.audience r where v = n and r in :roles)
              and (:unreadOnly = false or not exists (
                    select 1 from NotificationRead nr where nr.notificationId = n.id and nr.username = :username))
            """)
    Page<Notification> feed(@Param("roles") Collection<String> roles, @Param("username") String username,
                            @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    @Query(value = """
            select count(*) from notification n
            where exists (select 1 from notification_audience a where a.notification_id = n.id and a.role in (:roles))
              and not exists (select 1 from notification_read r where r.notification_id = n.id and r.username = :username)
            """, nativeQuery = true)
    long countUnread(@Param("roles") Collection<String> roles, @Param("username") String username);

    @Query("select nr.notificationId from NotificationRead nr where nr.username = :username and nr.notificationId in :ids")
    List<UUID> readAmong(@Param("username") String username, @Param("ids") Collection<UUID> ids);

    @Modifying
    @Query(value = """
            insert into notification_read (notification_id, username, read_at) values (:id, :username, :now)
            on conflict do nothing
            """, nativeQuery = true)
    int markRead(@Param("id") UUID id, @Param("username") String username, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            insert into notification_read (notification_id, username, read_at)
            select n.id, :username, :now from notification n
            where exists (select 1 from notification_audience a where a.notification_id = n.id and a.role in (:roles))
            on conflict do nothing
            """, nativeQuery = true)
    int markAllRead(@Param("roles") Collection<String> roles, @Param("username") String username,
                    @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
