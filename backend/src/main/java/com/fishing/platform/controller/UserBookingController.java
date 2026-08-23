package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.SpotAvailability;
import com.fishing.platform.dto.ApiDtos.UserBookingRequest;
import com.fishing.platform.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/user")
public class UserBookingController {
    private final BookingService service;

    public UserBookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping("/availability")
    public ApiResponse<List<SpotAvailability>> availability(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false)
            @Pattern(regexp = "MORNING|AFTERNOON|EVENING",
                    message = "必须是 MORNING、AFTERNOON 或 EVENING") String timeSlot,
            @RequestParam(required = false) @Positive Long spotId) {
        return ApiResponse.ok(service.availabilityForCurrentUser(date, timeSlot, spotId));
    }

    @GetMapping("/bookings")
    public ApiResponse<List<Booking>> bookings() {
        return ApiResponse.ok(service.findCurrentUserBookings());
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Booking> create(@Valid @RequestBody UserBookingRequest request) {
        return ApiResponse.ok("预约成功", service.createForCurrentUser(request));
    }

    @PostMapping("/bookings/{id}/cancel")
    public ApiResponse<Booking> cancel(@PathVariable @Positive Long id) {
        return ApiResponse.ok("预约已取消", service.cancelCurrentUserBooking(id));
    }
}
