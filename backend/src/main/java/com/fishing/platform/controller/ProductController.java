package com.fishing.platform.controller;

import com.fishing.platform.common.ApiResponse;
import com.fishing.platform.domain.DomainModels.Product;
import com.fishing.platform.dto.ApiDtos.ProductRequest;
import com.fishing.platform.dto.ApiDtos.ProductUpdateRequest;
import com.fishing.platform.service.ProductService;
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
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Product>> list() {
        return ApiResponse.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable @Positive Long id) {
        return ApiResponse.ok(service.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Product> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok("商品已创建", service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Product> update(@PathVariable @Positive Long id,
                                       @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.ok("商品已更新", service.update(id, request));
    }
}
