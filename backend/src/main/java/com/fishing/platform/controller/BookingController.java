package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.Booking;
import com.fishing.platform.domain.DomainModels.BookingAudit;
import com.fishing.platform.domain.DomainModels.SlotInventory;
import com.fishing.platform.domain.DomainModels.SpotAvailability;
import com.fishing.platform.dto.ApiDtos.BookingRequest;
import com.fishing.platform.dto.ApiDtos.SlotInventoryRequest;
import com.fishing.platform.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Booking>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Positive Long memberId) {
        return ApiResponse.ok(service.findAll(status, date, memberId));
    }

    @GetMapping("/availability")
    public ApiResponse<List<SpotAvailability>> availability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false)
            @Pattern(regexp = "MORNING|AFTERNOON|EVENING",
                    message = "必须是 MORNING、AFTERNOON 或 EVENING") String timeSlot,
            @RequestParam(required = false) @Positive Long spotId) {
        return ApiResponse.ok(service.availability(date, timeSlot, spotId));
    }

    @GetMapping("/slots")
    public ApiResponse<List<SlotInventory>> slots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Positive Long spotId) {
        return ApiResponse.ok(service.findSlotInventories(date, spotId));
    }

    @GetMapping("/{id}/audits")
    public ApiResponse<List<BookingAudit>> audits(
            @PathVariable @Positive Long id,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(service.audits(id, limit));
    }

    @PutMapping("/slots/{spotId}")
    public ApiResponse<SlotInventory> configureSlot(
            @PathVariable @Positive Long spotId,
            @Valid @RequestBody SlotInventoryRequest request) {
        return ApiResponse.ok("时段配置已保存", service.configureSlot(spotId, request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Booking> create(@Valid @RequestBody BookingRequest request) {
        return ApiResponse.ok("预订成功", service.create(request));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Booking> cancel(@PathVariable @Positive Long id) {
        return ApiResponse.ok("预订已取消", service.cancel(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<Booking> complete(@PathVariable @Positive Long id) {
        return ApiResponse.ok("预订已完成结单", service.complete(id));
    }

    @PostMapping("/{id}/no-show")
    public ApiResponse<Booking> noShow(@PathVariable @Positive Long id) {
        return ApiResponse.ok("预订已标记爽约", service.noShow(id));
    }
}
