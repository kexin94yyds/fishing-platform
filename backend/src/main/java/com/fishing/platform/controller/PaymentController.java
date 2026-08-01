package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.Payment;
import com.fishing.platform.dto.ApiDtos.PaymentConfirmRequest;
import com.fishing.platform.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Payment>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<Payment> confirm(@PathVariable @Positive Long id,
                                        @Valid @RequestBody(required = false) PaymentConfirmRequest request) {
        String method = request == null ? null : request.method();
        return ApiResponse.ok("收款已确认", service.confirm(id, method));
    }
}
