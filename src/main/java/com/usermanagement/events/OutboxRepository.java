package com.usermanagement.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Locks the next due events; {@code skip locked} lets several instances relay in parallel safely. */
    @Query(value = """
            select * from outbox_events
            where published_at is null and failed_at is null and next_attempt_at <= :now
            order by occurred_at
            limit :limit
            for update skip locked""", nativeQuery = true)
    List<OutboxEvent> lockNextBatch(@Param("now") Instant now, @Param("limit") int limit);

    List<OutboxEvent> findByEventTypeOrderByOccurredAtAsc(String eventType);

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
