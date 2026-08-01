package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.CatchRecord;
import com.fishing.platform.dto.ApiDtos.CatchRequest;
import com.fishing.platform.service.CatchService;
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
@RequestMapping("/api/catches")
public class CatchController {
    private final CatchService service;

    public CatchController(CatchService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CatchRecord>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CatchRecord> create(@Valid @RequestBody CatchRequest request) {
        return ApiResponse.ok("渔获记录已创建", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CatchRecord> update(@PathVariable @Positive Long id,
                                           @Valid @RequestBody CatchRequest request) {
        return ApiResponse.ok("渔获记录已更新", service.update(id, request));
    }
}
