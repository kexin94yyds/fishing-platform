package com.fishing.platform.service;

import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Product;
import com.fishing.platform.dto.ApiDtos.ProductRequest;
import com.fishing.platform.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {
    private final ProductMapper mapper;

    public ProductService(ProductMapper mapper) {
        this.mapper = mapper;
    }

    public List<Product> findAll() {
        return mapper.findAll();
    }

    public Product findById(Long id) {
        Product product = mapper.findById(id);
        if (product == null) {
            throw new NotFoundException("商品不存在");
        }
        return product;
    }

    public Product create(ProductRequest request) {
        mapper.insert(request.sku().trim(), request.name().trim(), request.category().trim(),
                request.price(), request.stockQuantity(),
                request.status() == null ? "ACTIVE" : request.status());
        return mapper.findBySku(request.sku().trim());
    }

    public Product update(Long id, ProductRequest request) {
        int changed = mapper.update(id, request.sku().trim(), request.name().trim(),
                request.category().trim(), request.price(), request.stockQuantity(),
                request.status() == null ? "ACTIVE" : request.status());
        if (changed == 0) {
            throw new NotFoundException("商品不存在");
        }
        return mapper.findById(id);
    }
}
