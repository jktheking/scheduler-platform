package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.api.dto.schedule.CreateScheduleRequest;
import com.acme.scheduler.api.dto.schedule.ScheduleDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScheduleController} without Spring MVC test slice.
 *
 * <p>These controller methods are demo placeholders; tests validate response shapes
 * without requiring spring-boot-test-autoconfigure.
 */
class ScheduleControllerUnitTest {

  @Test
  void create_returns_offline_dto() {
    var ctrl = new ScheduleController();

    // CreateScheduleRequest has many fields (mirrors DolphinScheduler-like API surface).
    // For unit testing controller shape, populate required fields with minimal values.
    var req = new CreateScheduleRequest(
        1L,                 // projectCode
        900001L,            // processDefinitionCode
        "0 0 2 * * ?",       // crontab
        "Asia/Kolkata",     // timezoneId
        null,               // startTime
        null,               // endTime
        "END",              // failureStrategy
        "NONE",             // warningType
        0L,                 // warningGroupId
        "default",          // workerGroup
        0L                  // environmentCode
    );

    ApiResponse<ScheduleDto> resp = ctrl.create(1L, req);
    assertThat(resp.code()).isEqualTo(0);
    assertThat(resp.data()).isNotNull();
    assertThat(resp.data().processDefinitionCode()).isEqualTo(900001L);
    assertThat(resp.data().crontab()).isEqualTo("0 0 2 * * ?");
    assertThat(resp.data().state()).isEqualTo("OFFLINE");
  }

  @Test
  void list_returns_page_items() {
    var ctrl = new ScheduleController();
    var resp = ctrl.list(1L, 2, 5);
    assertThat(resp.code()).isEqualTo(0);
    assertThat(resp.data()).isNotNull();
    assertThat(resp.data().items()).isNotEmpty();
  }
}
