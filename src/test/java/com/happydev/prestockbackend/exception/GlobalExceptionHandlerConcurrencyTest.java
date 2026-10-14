package com.happydev.prestockbackend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.context.request.ServletWebRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerConcurrencyTest {

    @Test
    void concurrentModification_returns409InsteadOf500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(new MockEnvironment());
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("PUT", "/api/sales/200"));

        ResponseEntity<ErrorDetails> response = handler.handleOptimisticLockingFailure(
                new ObjectOptimisticLockingFailureException("SaleItem", 176876L), request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("CONCURRENT_MODIFICATION", response.getBody().getCode());
    }
}
