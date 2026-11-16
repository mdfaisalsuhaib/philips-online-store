package com.philips.onlinestore.exception;

import com.philips.onlinestore.dto.ApiErrorResponse;
import com.philips.onlinestore.service.impl.ProductImageStorageService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ProductImageStorageService.ImageNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductImageNotFound(
            ProductImageStorageService.ImageNotFoundException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ProductImageStorageService.InvalidImageException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidProductImage(
            ProductImageStorageService.InvalidImageException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ProductImageStorageService.StorageException.class)
    public ResponseEntity<ApiErrorResponse> handleProductImageStorageFailure(
            ProductImageStorageService.StorageException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Product image storage failed.",
                request
        );
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidCartQuantityException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCartQuantity(
            InvalidCartQuantityException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidPaymentRequest(
            InvalidPaymentRequestException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentConflictException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentConflict(
            PaymentConflictException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler({
            EmptyCartException.class,
            InactiveProductException.class,
            InsufficientStockException.class
    })
    public ResponseEntity<ApiErrorResponse> handleCartConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return createResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return createResponse(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return createResponse(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return createResponse(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data or a related resource.",
                request
        );
    }

    private ResponseEntity<ApiErrorResponse> createResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(response);
    }
}
