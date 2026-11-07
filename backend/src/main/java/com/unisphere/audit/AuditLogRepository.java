package com.unisphere.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action);

    @Query("select distinct a.entityType from AuditLog a where a.entityType is not null order by a.entityType")
    List<String> distinctEntityTypes();
}
