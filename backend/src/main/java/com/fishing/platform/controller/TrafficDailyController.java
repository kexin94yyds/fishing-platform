package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.TrafficDailyEntry;
import com.fishing.platform.dto.ApiDtos.TrafficDailyUpsertRequest;
import com.fishing.platform.service.TrafficDailyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/traffic-daily")
public class TrafficDailyController {
    private final TrafficDailyService service;

    public TrafficDailyController(TrafficDailyService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<TrafficDailyEntry>> recent(
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return ApiResponse.ok(service.findRecent(days));
    }

    @PutMapping("/{statDate}")
    public ApiResponse<TrafficDailyEntry> upsert(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            @Valid @RequestBody TrafficDailyUpsertRequest request) {
        return ApiResponse.ok("客流日汇总已保存", service.upsert(statDate, request));
    }
}
