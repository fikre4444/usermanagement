package com.usermanagement.role;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(String name);

    boolean existsByName(String name);

    List<Role> findByNameIn(Collection<String> names);

    @Query(value = "select count(*) from user_roles where role_id = :roleId", nativeQuery = true)
    long countAssignments(@Param("roleId") UUID roleId);
}
