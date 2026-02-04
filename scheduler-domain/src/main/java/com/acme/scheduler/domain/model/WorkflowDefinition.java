package com.acme.scheduler.domain.model;

import com.acme.scheduler.domain.DomainValidationException;
import com.acme.scheduler.domain.ids.ProjectId;
import com.acme.scheduler.domain.ids.TenantId;
import com.acme.scheduler.domain.ids.WorkflowCode;
import com.acme.scheduler.domain.params.Parameter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable workflow template.
 * Bound to a code + version; instances execute a specific version.
 */
public record WorkflowDefinition(
 WorkflowCode code,
 int version,
 String name,
 String description,
 TenantId tenantId,
 ProjectId projectId,
 List<Parameter> globalParams,
 String locationsJson,
 Instant createdAt,
 Instant updatedAt
) {
 public WorkflowDefinition {
 Objects.requireNonNull(code, "code");
 if (version <= 0) throw new DomainValidationException("version must be positive");
 if (name == null || name.isBlank()) throw new DomainValidationException("name must be non-empty");
 Objects.requireNonNull(tenantId, "tenantId");
 Objects.requireNonNull(projectId, "projectId");
 globalParams = globalParams == null ? List.of() : List.copyOf(globalParams);
 locationsJson = locationsJson == null ? "{}" : locationsJson;
 createdAt = createdAt == null ? Instant.now() : createdAt;
 updatedAt = updatedAt == null ? createdAt : updatedAt;
 }
}
