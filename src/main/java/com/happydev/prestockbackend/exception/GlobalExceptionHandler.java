package com.happydev.prestockbackend.exception;


import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler { // Extiende ResponseEntityExceptionHandler

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class); // Logger

    // 404 Not Found
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDetails> resourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        String message = toSpanishNotFoundMessage(ex);
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "RESOURCE_NOT_FOUND",
                message,
                request.getDescription(false)
        );
        logger.warn("ResourceNotFoundException: {}", ex.getMessage());
        return new ResponseEntity<>(errorDetails, HttpStatus.NOT_FOUND);
    }

    private static String toSpanishNotFoundMessage(ResourceNotFoundException ex) {
        String resource = ex.getResourceName() != null ? ex.getResourceName() : "";
        return switch (resource) {
            case "User" -> "No se encontró el usuario solicitado.";
            case "Customer" -> "No se encontró el cliente solicitado.";
            case "Product" -> "No se encontró el producto solicitado.";
            case "Sale" -> "No se encontró la venta solicitada.";
            case "Category" -> "No se encontró la categoría solicitada.";
            case "Supplier" -> "No se encontró el proveedor solicitado.";
            default -> "El recurso solicitado no existe.";
        };
    }

    // 400 Bad Request - Validation Errors
    @Override // Sobreescribe el método de ResponseEntityExceptionHandler
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        Map<String, List<String>> errors = new HashMap<>(); // Usa un Map<String, List<String>>

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>()).add(fieldError.getDefaultMessage());
        }
        // También se pueden agregar errores globales si los hay:
        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            errors.computeIfAbsent(error.getObjectName(), k -> new ArrayList<>()).add(error.getDefaultMessage());;
        });

        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "VALIDATION_FAILED",
                "Hay errores de validación en los datos enviados.",
                errors,
                request.getDescription(false)
        );
        logger.warn("Validation errors: {}", errors);
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // 400 Bad Request - Data Integrity Violation (e.g., unique constraint)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDetails> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        String friendlyMessage = "No se pudo guardar la información. Verifica los datos e inténtalo de nuevo.";
        String rootMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        if (rootMessage.contains("products_sku_key")) {
            friendlyMessage = "El SKU ya existe en el sistema.";
        } else if (rootMessage.contains("products_barcode_key")) {
            friendlyMessage = "El código de barras ya existe en el sistema.";
        } else if (rootMessage.contains("file_name")) {
            friendlyMessage = "La imagen del producto no es válida. Intenta nuevamente.";
        }

        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "DATA_INTEGRITY_VIOLATION",
                friendlyMessage,
                request.getDescription(false)
        );
        logger.error("DataIntegrityViolationException: ", ex);  // Log completo
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }


    // 400 Bad Request - Illegal Argument
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDetails> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "ILLEGAL_ARGUMENT",
                ex.getMessage(),
                request.getDescription(false)
        );
        logger.warn("IllegalArgumentException: {}", ex.getMessage());
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // 400 Bad Request - Illegal State
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDetails> handleIllegalStateException(IllegalStateException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "ILLEGAL_STATE",
                ex.getMessage(),
                request.getDescription(false)
        );
        logger.warn("IllegalStateException: {}", ex.getMessage());
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // 403 Forbidden - Access Denied
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDetails> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "ACCESS_DENIED",
                "No tienes permiso para realizar esta acción.",
                request.getDescription(false)
        );
        logger.warn("AccessDeniedException: {}", ex.getMessage());
        return new ResponseEntity<>(errorDetails, HttpStatus.FORBIDDEN);
    }
    // 400 - ConstraintViolationException (Validaciones a nivel de entidad)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDetails> handleConstraintViolationException(ConstraintViolationException ex, WebRequest request) {

        Map<String, List<String>> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.computeIfAbsent(propertyPath, k-> new ArrayList<>()).add(message);
        });

        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "VALIDATION_FAILED",
                "Algunos datos no cumplen las reglas de validación.",
                errors,
                request.getDescription(false)
        );
        logger.warn("ConstraintViolationException: {}", errors);
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    // 500 Internal Server Error - Catch-all
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> globalExceptionHandler(Exception ex, WebRequest request) {
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "INTERNAL_SERVER_ERROR",
                "Ocurrió un error inesperado. Intenta de nuevo más tarde.",
                request.getDescription(false)
        );
        logger.error("Exception: ", ex); // Log *COMPLETO* de la excepción
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}