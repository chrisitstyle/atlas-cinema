package com.atlascinema.booking.service;

import com.atlascinema.booking.client.CinemaClient;
import com.atlascinema.booking.client.ScreeningClient;
import com.atlascinema.booking.client.dto.ScreeningClientResponse;
import com.atlascinema.booking.client.dto.ScreeningClientStatus;
import com.atlascinema.booking.client.dto.SeatClientResponse;
import com.atlascinema.booking.config.BookingProperties;
import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.dto.BookingPaymentDetailsResponse;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.event.BookingCancelledEvent;
import com.atlascinema.booking.exception.*;
import com.atlascinema.booking.outbox.OutboxEventWriter;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.repository.ConfirmedSeatRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ConfirmedSeatRepository confirmedSeatRepository;

    @Mock
    private ScreeningClient screeningClient;

    @Mock
    private CinemaClient cinemaClient;

    @Mock
    private RedisSeatHoldService redisSeatHoldService;

    @Mock
    private OutboxEventWriter outboxEventWriter;


    private Clock clock;
    private BookingProperties bookingProperties;

    private BookingService bookingService;

    private UUID screeningId;
    private UUID hallId;
    private UUID seatId;

    private LocalDateTime now;

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);
    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @BeforeEach
    void setUp() {
        screeningId = UUID.randomUUID();
        hallId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        clock = Clock.fixed(Instant.parse("2030-01-10T12:00:00Z"),
                ZoneOffset.UTC);

        now = LocalDateTime.of(2030, 1, 10, 12, 0);

        bookingProperties = new BookingProperties(HOLD_DURATION);

        bookingService = new BookingService(
                        bookingRepository,
                        confirmedSeatRepository,
                        screeningClient,
                        cinemaClient,
                        redisSeatHoldService,
                        bookingProperties,
                        clock,
                        outboxEventWriter);
    }

    @Test
    void createBooking_shouldCreateBooking_whenRequestIsValid() {
        CreateBookingRequest request = createRequest(List.of(seatId));

        when(screeningClient.getScreening(screeningId))
                .thenReturn(createAvailableScreening());

        when(cinemaClient.getSeat(hallId, seatId))
                .thenReturn(new SeatClientResponse(
                                seatId,
                                hallId));

        when(redisSeatHoldService.holdSeat(
                eq(screeningId),
                eq(seatId),
                any(UUID.class),
                eq(HOLD_DURATION)
        )).thenReturn(true);

        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(request);

        assertThat(response.id()).isNotNull();

        assertThat(response.screeningId())
                .isEqualTo(screeningId);

        assertThat(response.status())
                .isEqualTo(BookingStatus.HELD);

        assertThat(response.createdAt())
                .isEqualTo(now);

        assertThat(response.expiresAt())
                .isEqualTo(now.plusMinutes(10));

        assertThat(response.totalPrice())
                .isEqualByComparingTo("29.90");

        assertThat(response.seats())
                .hasSize(1);

        assertThat(response.seats().getFirst().seatId())
                .isEqualTo(seatId);

        verify(screeningClient)
                .getScreening(screeningId);

        verify(cinemaClient)
                .getSeat(hallId, seatId);

        verify(bookingRepository)
                .saveAndFlush(any(Booking.class));

        verify(redisSeatHoldService)
                .holdSeat(
                        eq(screeningId),
                        eq(seatId),
                        any(UUID.class),
                        eq(HOLD_DURATION));
    }

    @Test
    void createBooking_shouldUseFixedClock_whenBookingIsCreated() {
        when(screeningClient.getScreening(screeningId))
                .thenReturn(createAvailableScreening());

        when(cinemaClient.getSeat(hallId, seatId))
                .thenReturn(new SeatClientResponse(seatId, hallId));

        when(redisSeatHoldService.holdSeat(
                eq(screeningId),
                eq(seatId),
                any(UUID.class),
                eq(HOLD_DURATION)
        )).thenReturn(true);

        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(createRequest(List.of(seatId)));

        assertThat(response.createdAt())
                .isEqualTo(LocalDateTime.of(2030, 1, 10, 12, 0));

        assertThat(response.expiresAt())
                .isEqualTo(LocalDateTime.of(2030, 1, 10, 12, 10));
    }

    @Test
    void createBooking_shouldThrowScreeningNotFoundException_whenScreeningDoesNotExist() {
        FeignException.NotFound exception = mock(FeignException.NotFound.class);

        when(screeningClient.getScreening(screeningId))
                .thenThrow(exception);

        assertThatThrownBy(() ->
                bookingService.createBooking(
                        createRequest(List.of(seatId))))
                .isInstanceOf(ScreeningNotFoundException.class)
                .hasMessageContaining(screeningId.toString());

        verifyNoInteractions(cinemaClient);
        verifyNoInteractions(redisSeatHoldService);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_shouldThrowScreeningUnavailableException_whenScreeningIsCancelled() {
        ScreeningClientResponse screening = new ScreeningClientResponse(
                        screeningId,
                        hallId,
                        now.plusHours(2),
                        new BigDecimal("29.90"),
                        ScreeningClientStatus.CANCELLED);

        when(screeningClient.getScreening(screeningId))
                .thenReturn(screening);

        assertThatThrownBy(() -> bookingService.createBooking(
                        createRequest(List.of(seatId))))
                .isInstanceOf(ScreeningUnavailableException.class);

        verifyNoInteractions(cinemaClient);
        verifyNoInteractions(redisSeatHoldService);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_shouldThrowScreeningUnavailableException_whenScreeningHasAlreadyStarted() {
        ScreeningClientResponse screening = new ScreeningClientResponse(
                        screeningId,
                        hallId,
                        now.minusMinutes(1),
                        new BigDecimal("29.90"),
                        ScreeningClientStatus.SCHEDULED);

        when(screeningClient.getScreening(screeningId))
                .thenReturn(screening);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(List.of(seatId))))
                .isInstanceOf(ScreeningUnavailableException.class);

        verifyNoInteractions(cinemaClient);
        verifyNoInteractions(redisSeatHoldService);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_shouldThrowScreeningUnavailableException_whenScreeningStartsNow() {
        ScreeningClientResponse screening = new ScreeningClientResponse(
                        screeningId,
                        hallId,
                        now,
                        new BigDecimal("29.90"),
                        ScreeningClientStatus.SCHEDULED);

        when(screeningClient.getScreening(screeningId))
                .thenReturn(screening);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(List.of(seatId))))
                .isInstanceOf(ScreeningUnavailableException.class);

        verifyNoInteractions(cinemaClient);
        verifyNoInteractions(redisSeatHoldService);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void createBooking_shouldThrowSeatNotFoundException_whenSeatDoesNotExistInScreeningHall() {
        when(screeningClient.getScreening(screeningId))
                .thenReturn(createAvailableScreening());

        FeignException.NotFound exception = mock(FeignException.NotFound.class);

        when(cinemaClient.getSeat(hallId, seatId))
                .thenThrow(exception);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(List.of(seatId))))
                .isInstanceOf(SeatNotFoundException.class)
                .hasMessageContaining(seatId.toString());

        verify(bookingRepository, never())
                .saveAndFlush(any(Booking.class));

        // redis must not be touched before all seats are validated
        verifyNoInteractions(redisSeatHoldService);
    }

    @Test
    void createBooking_shouldAddAllSeats_whenMultipleSeatsAreProvided() {
        UUID secondSeatId = UUID.randomUUID();
        UUID thirdSeatId = UUID.randomUUID();

        List<UUID> seatIds = List.of(
                seatId,
                secondSeatId,
                thirdSeatId);

        when(screeningClient.getScreening(screeningId))
                .thenReturn(createAvailableScreening());

        for (UUID currentSeatId : seatIds) {
            when(cinemaClient.getSeat(
                    hallId,
                    currentSeatId)).thenReturn(
                    new SeatClientResponse(currentSeatId, hallId));
        }

        when(redisSeatHoldService.holdSeat(
                eq(screeningId),
                any(UUID.class),
                any(UUID.class),
                eq(HOLD_DURATION)
        )).thenReturn(true);

        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(
                        createRequest(seatIds));

        assertThat(response.seats())
                .hasSize(3);

        assertThat(response.seats())
                .extracting(seat -> seat.seatId())
                .containsExactlyElementsOf(seatIds);

        assertThat(response.totalPrice())
                .isEqualByComparingTo("89.70");

        for (UUID currentSeatId : seatIds) {
            verify(cinemaClient)
                    .getSeat(hallId, currentSeatId);

            verify(redisSeatHoldService)
                    .holdSeat(
                            eq(screeningId),
                            eq(currentSeatId),
                            any(UUID.class),
                            eq(HOLD_DURATION));
        }
    }

    @Test
    void createBooking_shouldReleasePreviouslyAcquiredSeats_whenLaterSeatCannotBeAcquired() {
        UUID secondSeatId = UUID.randomUUID();

        List<UUID> seatIds = List.of(seatId, secondSeatId);

        when(screeningClient.getScreening(screeningId))
                .thenReturn(createAvailableScreening());

        for (UUID currentSeatId : seatIds) {
            when(cinemaClient.getSeat(hallId, currentSeatId
            )).thenReturn(
                    new SeatClientResponse(currentSeatId, hallId));
        }

        when(redisSeatHoldService.holdSeat(
                eq(screeningId),
                eq(seatId),
                any(UUID.class),
                eq(HOLD_DURATION)
        )).thenReturn(true);

        when(redisSeatHoldService.holdSeat(
                eq(screeningId),
                eq(secondSeatId),
                any(UUID.class),
                eq(HOLD_DURATION)
        )).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(seatIds)))
                .isInstanceOf(SeatAlreadyReservedException.class);

        // the first hold must be released because the booking cannot be completed
        verify(redisSeatHoldService)
                .releaseSeat(
                        eq(screeningId),
                        eq(seatId),
                        any(UUID.class));

        // the second seat was never acquired so there is nothing to release
        verify(redisSeatHoldService, never())
                .releaseSeat(
                        eq(screeningId),
                        eq(secondSeatId),
                        any(UUID.class));

        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
    }

    @Test
    void createBooking_shouldThrowSeatAlreadyReservedException_whenRedisHoldCannotBeAcquired() {
        when(screeningClient.getScreening(screeningId))
                .thenReturn(createAvailableScreening());

        when(cinemaClient.getSeat(hallId, seatId))
                .thenReturn(new SeatClientResponse(seatId, hallId));

        when(redisSeatHoldService.holdSeat(
                eq(screeningId),
                eq(seatId),
                any(UUID.class),
                eq(HOLD_DURATION)
        )).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(List.of(seatId))))
                .isInstanceOf(SeatAlreadyReservedException.class)
                .hasMessageContaining(screeningId.toString());

        verify(bookingRepository, never())
                .saveAndFlush(any(Booking.class));
    }

    @Test
    void createBooking_shouldReleaseAcquiredSeat_whenDatabaseSaveFails() {
        when(screeningClient.getScreening(screeningId))
                .thenReturn(createAvailableScreening());

        when(cinemaClient.getSeat(hallId, seatId))
                .thenReturn(new SeatClientResponse(seatId, hallId));

        when(redisSeatHoldService.holdSeat(
                eq(screeningId),
                eq(seatId),
                any(UUID.class),
                eq(HOLD_DURATION)
        )).thenReturn(true);

        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new IllegalStateException("Database unavailable"));

        assertThatThrownBy(() -> bookingService.createBooking(createRequest(List.of(seatId))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");

        // redis is outside the database transaction so the hold must be released manually
        verify(redisSeatHoldService)
                .releaseSeat(
                        eq(screeningId),
                        eq(seatId),
                        any(UUID.class));
    }

    @Test
    void cancelBooking_shouldCancelBooking_whenBookingIsHeldAndScreeningHasNotStarted() {
        Booking booking = Booking.create(
                null,
                "guest@example.com",
                screeningId,
                now.minusMinutes(1));

        LocalDateTime expiresAt = now.plusMinutes(10);

        booking.addSeat(seatId, new BigDecimal("29.90"));

        booking.markAsHeld(expiresAt);

        when(bookingRepository.findByIdForUpdate(booking.getId()))
                .thenReturn(Optional.of(booking));

        when(screeningClient.getScreening(screeningId))
                .thenReturn(
                        new ScreeningClientResponse(
                                screeningId,
                                hallId,
                                now.plusHours(2),
                                new BigDecimal("29.90"),
                                ScreeningClientStatus.SCHEDULED));

        BookingResponse response = bookingService.cancelBooking(booking.getId());

        assertThat(response.status()).isEqualTo(BookingStatus.CANCELLED);

        assertThat(booking.getSeats()).hasSize(1);

        verify(bookingRepository).findByIdForUpdate(booking.getId());

        verify(screeningClient).getScreening(screeningId);

        verify(bookingRepository, never()).save(any(Booking.class));
        verify(bookingRepository).flush();

        verify(redisSeatHoldService).releaseSeat(screeningId, seatId, booking.getId());

        ArgumentCaptor<BookingCancelledEvent> eventCaptor = ArgumentCaptor.forClass(
                        BookingCancelledEvent.class);

        verify(outboxEventWriter)
                .saveCancelled(eventCaptor.capture(), eq(now));

        BookingCancelledEvent bookingCancelledEvent = eventCaptor.getValue();

        assertThat(bookingCancelledEvent.eventId()).isNotNull();

        assertThat(bookingCancelledEvent.bookingId()).isEqualTo(booking.getId());

        assertThat(bookingCancelledEvent.customerId()).isEqualTo(booking.getCustomerId());

        assertThat(bookingCancelledEvent.contactEmail()).isEqualTo(booking.getContactEmail());

        assertThat(bookingCancelledEvent.screeningId()).isEqualTo(screeningId);

        assertThat(bookingCancelledEvent.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void getBookingPaymentDetails_shouldReturnDetails_whenBookingExists() {
        Booking heldBooking = Booking.create(
                null,
                "guest@example.com",
                screeningId,
                now);

        heldBooking.addSeat(seatId, new BigDecimal("29.90"));
        heldBooking.markAsHeld(now.plus(HOLD_DURATION));

        when(bookingRepository.findById(heldBooking.getId()))
                .thenReturn(Optional.of(heldBooking));

        BookingPaymentDetailsResponse paymentDetails = bookingService.getBookingPaymentDetails(heldBooking.getId());

        assertThat(paymentDetails.bookingId()).isEqualTo(heldBooking.getId());

        assertThat(paymentDetails.customerId()).isNull();

        assertThat(paymentDetails.status()).isEqualTo(BookingStatus.HELD);

        assertThat(paymentDetails.totalPrice()).isEqualByComparingTo("29.90");

        assertThat(paymentDetails.expiresAt()).isEqualTo(NOW.plus(HOLD_DURATION));

        verify(bookingRepository).findById(heldBooking.getId());
    }

    @Test
    void getBookingPaymentDetails_shouldThrowException_whenBookingDoesNotExist() {
        UUID missingBookingId = UUID.randomUUID();

        when(bookingRepository.findById(missingBookingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingPaymentDetails(missingBookingId)).isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void cancelBooking_shouldThrowBookingNotFoundException_whenBookingDoesNotExist() {
        UUID bookingId = UUID.randomUUID();

        when(bookingRepository.findByIdForUpdate(bookingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(bookingId))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessageContaining(bookingId.toString());

        verifyNoInteractions(screeningClient);
        verifyNoInteractions(redisSeatHoldService);

        verify(bookingRepository, never()).flush();
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    void cancelBooking_shouldThrowBookingCancellationNotAllowedException_whenScreeningHasStarted() {
        Booking booking = Booking.create(
                null,
                "guest@example.com",
                screeningId,
                now.minusMinutes(20));

        LocalDateTime expiresAt = now.plusMinutes(10);

        booking.addSeat(seatId, new BigDecimal("29.90"));

        booking.markAsHeld(expiresAt);

        when(bookingRepository.findByIdForUpdate(booking.getId()))
                .thenReturn(Optional.of(booking));

        when(screeningClient.getScreening(screeningId))
                .thenReturn(
                        new ScreeningClientResponse(
                                screeningId,
                                hallId,
                                now.minusMinutes(1),
                                new BigDecimal("29.90"),
                                ScreeningClientStatus.SCHEDULED));

        assertThatThrownBy(() -> bookingService.cancelBooking(booking.getId()))
                .isInstanceOf(BookingCancellationNotAllowedException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);

        // failed cancellation must not release the Redis hold
        verifyNoInteractions(redisSeatHoldService);

        verifyNoInteractions(outboxEventWriter);
        verify(bookingRepository, never()).flush();
    }

    private CreateBookingRequest createRequest(
            List<UUID> seatIds
    ) {
        return new CreateBookingRequest(
                null,
                "guest@example.com",
                screeningId,
                seatIds);
    }

    private ScreeningClientResponse createAvailableScreening() {
        return new ScreeningClientResponse(
                screeningId,
                hallId,
                now.plusHours(2),
                new BigDecimal("29.90"),
                ScreeningClientStatus.SCHEDULED);
    }
}
