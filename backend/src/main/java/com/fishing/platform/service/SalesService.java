package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Product;
import com.fishing.platform.domain.DomainModels.SalesOrder;
import com.fishing.platform.domain.DomainModels.SalesOrderDetail;
import com.fishing.platform.dto.ApiDtos.SalesOrderRequest;
import com.fishing.platform.mapper.MemberMapper;
import com.fishing.platform.mapper.PaymentMapper;
import com.fishing.platform.mapper.ProductMapper;
import com.fishing.platform.mapper.SalesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class SalesService {
    private final SalesMapper salesMapper;
    private final ProductMapper productMapper;
    private final PaymentMapper paymentMapper;
    private final MemberMapper memberMapper;
    private final CurrentUserService currentUserService;

    public SalesService(SalesMapper salesMapper,
                        ProductMapper productMapper,
                        PaymentMapper paymentMapper,
                        MemberMapper memberMapper,
                        CurrentUserService currentUserService) {
        this.salesMapper = salesMapper;
        this.productMapper = productMapper;
        this.paymentMapper = paymentMapper;
        this.memberMapper = memberMapper;
        this.currentUserService = currentUserService;
    }

    public List<SalesOrder> findAll() {
        return salesMapper.findAll();
    }

    public SalesOrderDetail findDetail(Long id) {
        SalesOrder order = salesMapper.findById(id);
        if (order == null) {
            throw new NotFoundException("销售单不存在");
        }
        return new SalesOrderDetail(order, salesMapper.findItems(id));
    }

    @Transactional
    public Map<String, Object> create(SalesOrderRequest request) {
        record PricedItem(Product product, int quantity, BigDecimal lineAmount) {
        }

        if (request.memberId() != null) {
            var member = memberMapper.findByIdForUpdate(request.memberId());
            if (member == null || !"ACTIVE".equals(member.status())) {
                throw new BusinessException("会员不存在或已停用，不能创建销售单");
            }
        }

        Map<Long, Integer> quantities = new TreeMap<>();
        try {
            request.items().forEach(item -> quantities.merge(item.productId(), item.quantity(), Math::addExact));
        } catch (ArithmeticException exception) {
            throw new BusinessException("商品数量超出允许范围");
        }

        List<PricedItem> pricedItems = new ArrayList<>();
        for (var entry : quantities.entrySet()) {
            Product product = productMapper.findByIdForUpdate(entry.getKey());
            if (product == null || !"ACTIVE".equals(product.status())) {
                throw new BusinessException("商品 " + entry.getKey() + " 不存在或已停用");
            }
            int quantity = entry.getValue();
            if (product.stockQuantity() < quantity
                    || productMapper.decrementStock(product.id(), quantity) == 0) {
                throw new BusinessException("商品“" + product.name() + "”库存不足");
            }
            pricedItems.add(new PricedItem(product, quantity,
                    product.price().multiply(BigDecimal.valueOf(quantity))));
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

    @Transactional
    public SalesOrder cancel(Long id) {
        if (salesMapper.lockById(id) == null) {
            throw new NotFoundException("销售单不存在");
        }
        SalesOrder order = salesMapper.findById(id);
        if (!"PENDING_PAYMENT".equals(order.status()) || !"PENDING".equals(order.paymentStatus())) {
            throw new BusinessException("销售单已处理，不能取消");
        }
        var paymentSnapshot = paymentMapper.findByBusiness("SALES_ORDER", id);
        if (paymentSnapshot == null) {
            throw new BusinessException("关联支付单不存在，不能取消销售单");
        }
        var payment = paymentMapper.findByIdForUpdate(paymentSnapshot.id());
        if (payment == null || !"PENDING".equals(payment.status())) {
            throw new BusinessException("关联支付单已处理，不能取消销售单");
        }

        Map<Long, Integer> quantities = new TreeMap<>();
        try {
            salesMapper.findItems(id).forEach(item ->
                    quantities.merge(item.productId(), item.quantity(), Math::addExact));
        } catch (ArithmeticException exception) {
            throw new BusinessException("销售单商品数量异常，取消操作已回滚");
        }
        if (salesMapper.markCancelled(id) == 0
                || paymentMapper.cancelByBusiness("SALES_ORDER", id) == 0) {
            throw new BusinessException("销售单已被处理，取消操作已回滚");
        }
        for (var entry : quantities.entrySet()) {
            if (productMapper.incrementStock(entry.getKey(), entry.getValue()) == 0) {
                throw new BusinessException("商品库存回补失败，取消操作已回滚");
            }
        }
        return salesMapper.findById(id);
    }
}
