package com.acme.scheduler.domain.model;

import com.acme.scheduler.domain.DomainValidationException;
import com.acme.scheduler.domain.ids.TaskCode;
import com.acme.scheduler.domain.params.Parameter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable task template.
 * Task "type" is implemented by runtime task plugins; params are task-specific.
 */
public record TaskDefinition(
 TaskCode code,
 int version,
 String name,
 String description,
 String taskType,
 String taskParamsJson,
 Duration timeout,
 int maxRetryTimes,
 Duration retryInterval,
 List<Parameter> localParams,
 Instant createdAt,
 Instant updatedAt
) {
 public TaskDefinition {
 Objects.requireNonNull(code, "code");
 if (version <= 0) throw new DomainValidationException("version must be positive");
 if (name == null || name.isBlank()) throw new DomainValidationException("name must be non-empty");
 if (taskType == null || taskType.isBlank()) throw new DomainValidationException("taskType must be non-empty");
 taskParamsJson = taskParamsJson == null ? "{}" : taskParamsJson;
 timeout = timeout == null ? Duration.ZERO : timeout;
 if (maxRetryTimes < 0) throw new DomainValidationException("maxRetryTimes must be >= 0");
 retryInterval = retryInterval == null ? Duration.ZERO : retryInterval;
 localParams = localParams == null ? List.of() : List.copyOf(localParams);
 createdAt = createdAt == null ? Instant.now() : createdAt;
 updatedAt = updatedAt == null ? createdAt : updatedAt;
 }
}
