package ru.iktk.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.iktk.auth.model.AuditEvent;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditRepository extends JpaRepository<AuditEvent, String> {

    @Query(value = "SELECT * FROM iktk_audit.audit_log WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<AuditEvent> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId, @Param("limit") int limit);

    @Query(value = "SELECT * FROM iktk_audit.audit_log WHERE action = :action ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<AuditEvent> findByActionOrderByCreatedAtDesc(@Param("action") String action, @Param("limit") int limit);

    @Query(value = "SELECT * FROM iktk_audit.audit_log WHERE user_id = :userId AND action = :action ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<AuditEvent> findByUserIdAndActionOrderByCreatedAtDesc(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("limit") int limit);

    @Query(value = "SELECT * FROM iktk_audit.audit_log WHERE user_id = :userId AND result = :result ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<AuditEvent> findByUserIdAndResultOrderByCreatedAtDesc(
            @Param("userId") String userId,
            @Param("result") String result,
            @Param("limit") int limit);

    @Query(value = "SELECT * FROM iktk_audit.audit_log WHERE created_at BETWEEN :from AND :to ORDER BY created_at DESC LIMIT :limit", nativeQuery = true)
    List<AuditEvent> findByCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM iktk_audit.audit_log WHERE user_id = :userId AND action = :action AND result = :result AND created_at > :since", nativeQuery = true)
    long countByUserIdAndActionAndResultAndCreatedAtAfter(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("result") String result,
            @Param("since") Instant since);
}