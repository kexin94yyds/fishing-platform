package com.fishing.platform.service;

import com.fishing.platform.common.BusinessException;
import com.fishing.platform.common.NotFoundException;
import com.fishing.platform.domain.DomainModels.Payment;
import com.fishing.platform.mapper.BookingMapper;
import com.fishing.platform.mapper.PaymentMapper;
import com.fishing.platform.mapper.SalesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentService {
    private final PaymentMapper paymentMapper;
    private final SalesMapper salesMapper;
    private final BookingMapper bookingMapper;
    private final CurrentUserService currentUserService;

    public PaymentService(PaymentMapper paymentMapper, SalesMapper salesMapper, BookingMapper bookingMapper,
                          CurrentUserService currentUserService) {
        this.paymentMapper = paymentMapper;
        this.salesMapper = salesMapper;
        this.bookingMapper = bookingMapper;
        this.currentUserService = currentUserService;
    }

    public List<Payment> findAll() {
        return paymentMapper.findAll();
    }

    @Transactional
    public Payment confirm(Long id, String method) {
        Payment snapshot = paymentMapper.findById(id);
        if (snapshot == null) {
            throw new NotFoundException("支付单不存在");
        }
        if ("SALES_ORDER".equals(snapshot.businessType())) {
            if (salesMapper.lockById(snapshot.businessId()) == null) {
                throw new BusinessException("关联销售单不存在，不能确认收款");
            }
            var order = salesMapper.findById(snapshot.businessId());
            if (!"PENDING_PAYMENT".equals(order.status()) || !"PENDING".equals(order.paymentStatus())) {
                throw new BusinessException("关联销售单已处理，不能确认收款");
            }
        } else if ("BOOKING".equals(snapshot.businessType())) {
            if (bookingMapper.lockById(snapshot.businessId()) == null) {
                throw new BusinessException("关联预订不存在，不能确认收款");
            }
            var booking = bookingMapper.findById(snapshot.businessId());
            if ("CANCELLED".equals(booking.status()) || !"PENDING".equals(booking.paymentStatus())) {
                throw new BusinessException("关联预订已处理，不能确认收款");
            }
        } else {
            throw new BusinessException("不支持的收费业务类型");
        }
        Payment payment = paymentMapper.findByIdForUpdate(id);
        if (payment == null || !"PENDING".equals(payment.status())) {
            throw new BusinessException("支付单已处理，不能重复确认");
        }
        if (!snapshot.businessType().equals(payment.businessType())
                || !snapshot.businessId().equals(payment.businessId())) {
            throw new BusinessException("支付单关联信息异常，不能确认收款");
        }
        if (paymentMapper.confirm(id, method) == 0) {
            throw new BusinessException("支付单已处理，不能重复确认");
        }
        if ("SALES_ORDER".equals(payment.businessType()) && salesMapper.markPaid(payment.businessId()) == 0) {
            throw new BusinessException("关联销售单状态异常，收款确认已回滚");
        }
        if ("BOOKING".equals(payment.businessType())) {
            var before = bookingMapper.findById(payment.businessId());
            if (bookingMapper.markPaid(payment.businessId()) == 0) {
                throw new BusinessException("关联预订状态异常，收款确认已回滚");
            }
            var after = bookingMapper.findById(payment.businessId());
            var actor = currentUserService.current();
            bookingMapper.insertAudit(actor.id(), actor.username(), after.id(), after.bookingNo(), "PAYMENT_CONFIRM",
                    before.status(), after.status(), before.paymentStatus(), after.paymentStatus());
        }
        return paymentMapper.findById(id);
    }
}
