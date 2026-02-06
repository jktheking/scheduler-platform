package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import com.acme.scheduler.api.dto.resource.UploadResourceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/resources")
@Tag(name = "Resources", description = "Upload and manage resources (for script tasks, etc.).")
public class ResourceController {

 @Operation(summary = "Upload a resource", description = "Uploads a file resource and returns its logical name. (Demo placeholder implementation)")
 @ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Upload accepted.")})
 @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
 public ApiResponse<UploadResourceResponse> upload(@RequestParam("file") MultipartFile file,
 @RequestParam("fullName") String fullName,
 @RequestParam(value = "description", required = false) String description) {
 return ApiResponse.ok(new UploadResourceResponse(fullName, "FILE", "SUCCESS"));
 }
}
