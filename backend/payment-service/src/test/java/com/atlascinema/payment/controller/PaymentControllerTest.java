
package com.atlascinema.payment.controller;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.exception.BookingNotPayableException;
import com.atlascinema.payment.exception.PaymentExceptionHandler;
import com.atlascinema.payment.exception.PaymentIdempotencyConflictException;
import com.atlascinema.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Verifies the HTTP contract for internal payment initiation
 * Mocks application logic while testing request validation and responses
 */
@WebMvcTest(PaymentController.class)
@Import(PaymentExceptionHandler.class)
class PaymentControllerTest {

    private static final UUID BOOKING_ID = UUID.fromString("f5fd3520-c844-4d16-bde3-360e87648dd2");

    private static final String IDEMPOTENCY_KEY = "payment-request-123";

    private static final Instant INITIATED_AT = Instant.parse("2026-10-29T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void initiatePayment_shouldReturnPendingPayment_whenRequestIsValid()
            throws Exception {

        Payment initiatedPayment = createPendingPayment();

        when(paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY
        )).thenReturn(initiatedPayment);

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(validPaymentRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId")
                        .value(initiatedPayment.getId().toString()))
                .andExpect(jsonPath("$.bookingId")
                        .value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.amount").value(59.80))
                .andExpect(jsonPath("$.currencyCode").value("PLN"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt")
                        .value(INITIATED_AT.toString()));

        verify(paymentService).initiatePaymentForBooking(BOOKING_ID, IDEMPOTENCY_KEY);
    }

    @Test
    void initiatePayment_shouldReturnBadRequest_whenBookingIdIsMissing()
            throws Exception {

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiatePayment_shouldReturnBadRequest_whenIdempotencyKeyIsMissing()
            throws Exception {

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPaymentRequestJson()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiatePayment_shouldReturnBadRequest_whenIdempotencyKeyIsBlank()
            throws Exception {

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", " ")
                        .content(validPaymentRequestJson()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void initiatePayment_shouldReturnConflict_whenIdempotencyKeyBelongsToAnotherBooking()
            throws Exception {

        when(paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY
        )).thenThrow(new PaymentIdempotencyConflictException());

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(validPaymentRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void initiatePayment_shouldReturnConflict_whenBookingIsNotPayable()
            throws Exception {

        when(paymentService.initiatePaymentForBooking(
                BOOKING_ID,
                IDEMPOTENCY_KEY
        )).thenThrow(new BookingNotPayableException(
                BOOKING_ID,
                "booking hold has expired"
        ));

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(validPaymentRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    private Payment createPendingPayment() {
        return Payment.initiateForBooking(
                BOOKING_ID,
                new BigDecimal("59.80"),
                "PLN",
                IDEMPOTENCY_KEY,
                INITIATED_AT
        );
    }

    private String validPaymentRequestJson() {
        return """
                {
                  "bookingId": "%s"
                }
                """.formatted(BOOKING_ID);
    }
}
