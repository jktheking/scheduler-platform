package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.api.dto.PageResponse;
import com.acme.scheduler.api.dto.admin.UserDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scheduler/users")
@Tag(name = "Administration APIs")
public class AdminController {

 @GetMapping("/list")
 public ApiResponse<PageResponse<UserDto>> list(@RequestParam(defaultValue = "1") int pageNo,
 @RequestParam(defaultValue = "10") int pageSize) {
 var users = List.of(new UserDto(100, "jk", "ADMIN", "default"));
 return ApiResponse.ok(new PageResponse<>(1, pageNo, pageSize, users));
 }
}
