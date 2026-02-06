package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.service.dlq.ListDlqUseCase;
import com.acme.scheduler.service.dlq.ReplayDlqUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dlq")
@Tag(name = "DLQ APIs")
public class DlqController {

  private final ListDlqUseCase list;
  private final ReplayDlqUseCase replay;

  public DlqController(ListDlqUseCase list, ReplayDlqUseCase replay) {
    this.list = list;
    this.replay = replay;
  }

  @GetMapping
  public ApiResponse<?> list(@RequestParam(defaultValue = "1") int pageNo,
                             @RequestParam(defaultValue = "100") int pageSize) {
    int offset = Math.max(0, (pageNo - 1) * pageSize);
    return ApiResponse.ok(list.handle("default", offset, Math.max(1, pageSize)));
  }

  @Operation(summary = "Replay a DLQ task",
      description = "Replays a DLQ entry by creating a new task attempt and scheduling an immediate trigger for the owning workflow instance.")
  @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Replay accepted.")})
  @PostMapping("/{dlqId}/replay")
  public ApiResponse<Void> replay(@PathVariable long dlqId) {
    replay.handle(dlqId);
    return ApiResponse.ok(null);
  }
}
