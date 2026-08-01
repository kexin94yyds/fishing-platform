package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.domain.DomainModels.Product;
import com.fishing.platform.domain.DomainModels.SalesOrder;
import com.fishing.platform.dto.ApiDtos.SalesOrderRequest;
import com.fishing.platform.mapper.PaymentMapper;
import com.fishing.platform.mapper.ProductMapper;
import com.fishing.platform.mapper.SalesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesService {
    private final SalesMapper salesMapper;
    private final ProductMapper productMapper;
    private final PaymentMapper paymentMapper;
    private final CurrentUserService currentUserService;

    public SalesService(SalesMapper salesMapper,
                        ProductMapper productMapper,
                        PaymentMapper paymentMapper,
                        CurrentUserService currentUserService) {
        this.salesMapper = salesMapper;
        this.productMapper = productMapper;
        this.paymentMapper = paymentMapper;
        this.currentUserService = currentUserService;
    }

    public List<SalesOrder> findAll() {
        return salesMapper.findAll();
    }

    @Transactional
    public Map<String, Object> create(SalesOrderRequest request) {
        record PricedItem(Product product, int quantity, BigDecimal lineAmount) {
        }

        List<PricedItem> pricedItems = request.items().stream().map(item -> {
            Product product = productMapper.findById(item.productId());
            if (product == null || !"ACTIVE".equals(product.status())) {
                throw new BusinessException("商品 " + item.productId() + " 不存在或已停用");
            }
            BigDecimal lineAmount = product.price().multiply(BigDecimal.valueOf(item.quantity()));
            return new PricedItem(product, item.quantity(), lineAmount);
        }).toList();

        for (PricedItem item : pricedItems) {
            if (productMapper.decrementStock(item.product().id(), item.quantity()) == 0) {
                throw new BusinessException("商品“" + item.product().name() + "”库存不足");
            }
        }

        BigDecimal total = pricedItems.stream()
                .map(PricedItem::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String orderNo = BusinessNumbers.next("SO");
        salesMapper.insertOrder(orderNo, request.memberId(), total, currentUserService.current().id());
        Long orderId = salesMapper.findIdByNo(orderNo);

        for (PricedItem item : pricedItems) {
            salesMapper.insertItem(orderId, item.product().id(), item.product().name(), item.quantity(),
                    item.product().price(), item.lineAmount());
        }

        String paymentNo = BusinessNumbers.next("PAY");
        paymentMapper.insert(paymentNo, "SALES_ORDER", orderId, total, request.paymentMethod());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", salesMapper.findById(orderId));
        result.put("items", salesMapper.findItems(orderId));
        result.put("payment", paymentMapper.findByBusiness("SALES_ORDER", orderId));
        return result;
    }
}
