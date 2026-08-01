package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.SalesOrder;
import com.fishing.platform.dto.ApiDtos.SalesOrderRequest;
import com.fishing.platform.service.SalesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {
    private final SalesService service;

    public SalesOrderController(SalesService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SalesOrder>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody SalesOrderRequest request) {
        return ApiResponse.ok("销售单已创建，等待收款", service.create(request));
    }
}
