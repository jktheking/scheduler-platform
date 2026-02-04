package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.api.dto.resource.UploadResourceResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/scheduler/resources")
@Tag(name = "Resource Management APIs")
public class ResourceController {

 @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
 public ApiResponse<UploadResourceResponse> upload(@RequestParam("file") MultipartFile file,
 @RequestParam("fullName") String fullName,
 @RequestParam(value = "description", required = false) String description) {
 return ApiResponse.ok(new UploadResourceResponse(fullName, "FILE", "SUCCESS"));
 }
}
