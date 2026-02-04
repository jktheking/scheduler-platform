package com.acme.scheduler.api.controller;

import com.acme.scheduler.api.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

 @ExceptionHandler(MethodArgumentNotValidException.class)
 @ResponseStatus(HttpStatus.BAD_REQUEST)
 public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
 String msg = ex.getBindingResult().getAllErrors().stream()
 .findFirst()
 .map(e -> e.getDefaultMessage())
 .orElse("validation error");
 return ApiResponse.error(400, msg);
 }

 @ExceptionHandler(ConstraintViolationException.class)
 @ResponseStatus(HttpStatus.BAD_REQUEST)
 public ApiResponse<Void> handleConstraint(ConstraintViolationException ex) {
 return ApiResponse.error(400, ex.getMessage());
 }

 @ExceptionHandler(Exception.class)
 @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
 public ApiResponse<Void> handleAny(Exception ex) {
 return ApiResponse.error(500, ex.getClass().getSimpleName() + ": " + ex.getMessage());
 }
}
