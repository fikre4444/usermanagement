package com.usermanagement.otp;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OtpRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findFirstBySubjectIdAndPurposeOrderByCreatedAtDesc(UUID subjectId, OtpPurpose purpose);

    @Modifying
    @Query("""
            update OtpCode o set o.consumedAt = :now
            where o.subjectId = :subjectId and o.purpose = :purpose and o.consumedAt is null""")
    int invalidateActive(@Param("subjectId") UUID subjectId, @Param("purpose") OtpPurpose purpose,
                         @Param("now") Instant now);

    @Modifying
    @Query("delete from OtpCode o where o.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
