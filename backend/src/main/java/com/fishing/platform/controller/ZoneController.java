package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.Zone;
import com.fishing.platform.dto.ApiDtos.ZoneRequest;
import com.fishing.platform.service.ZoneService;
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
@RequestMapping("/api/zones")
public class ZoneController {
    private final ZoneService service;

    public ZoneController(ZoneService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Zone>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Zone> create(@Valid @RequestBody ZoneRequest request) {
        return ApiResponse.ok("运营分区已创建", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Zone> update(@PathVariable @Positive Long id,
                                    @Valid @RequestBody ZoneRequest request) {
        return ApiResponse.ok("运营分区已更新", service.update(id, request));
    }
}
