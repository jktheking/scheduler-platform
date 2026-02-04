package com.acme.scheduler.domain.model;

import com.acme.scheduler.domain.ids.TaskCode;
import com.acme.scheduler.domain.ids.WorkflowCode;

/**
 * Edge in a workflow DAG: pre -> post.
 * preTaskCode can be 0 in DS to represent workflow start; we model start explicitly in DAG builder later.
 */
public record WorkflowTaskRelation(
 WorkflowCode workflowCode,
 int workflowVersion,
 TaskCode preTaskCode,
 int preTaskVersion,
 TaskCode postTaskCode,
 int postTaskVersion
) {}
