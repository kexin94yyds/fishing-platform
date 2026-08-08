package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Product;
import com.fishing.platform.dto.ApiDtos.ProductRequest;
import com.fishing.platform.dto.ApiDtos.ProductUpdateRequest;
import com.fishing.platform.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public Product update(Long id, ProductUpdateRequest request) {
        if (mapper.findById(id) == null) {
            throw new NotFoundException("商品不存在");
        }
        int changed = mapper.update(id, request.sku().trim(), request.name().trim(),
                request.category().trim(), request.price(), request.stockQuantity(),
                request.status() == null ? "ACTIVE" : request.status(), request.version());
        if (changed == 0) {
            throw new BusinessException("商品信息或库存已发生变化，请刷新后重试");
        }
        return mapper.findById(id);
    }
}
