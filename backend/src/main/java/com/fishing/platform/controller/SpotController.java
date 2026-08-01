package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.Spot;
import com.fishing.platform.dto.ApiDtos.SpotRequest;
import com.fishing.platform.service.SpotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/spots")
public class SpotController {
    private final SpotService service;

    public SpotController(SpotService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Spot>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @GetMapping("/map")
    public ApiResponse<List<Spot>> map() {
        return ApiResponse.ok(service.mapSpots());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Spot> create(@Valid @RequestBody SpotRequest request) {
        return ApiResponse.ok("钓位已创建", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Spot> update(@PathVariable @Positive Long id,
                                    @Valid @RequestBody SpotRequest request) {
        return ApiResponse.ok("钓位已更新", service.update(id, request));
    }
}
