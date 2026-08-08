package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Payment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentMapper {

    String BASE_SELECT = """
            SELECT p.id, p.payment_no, p.business_type, p.business_id,
                   CASE WHEN p.business_type = 'SALES_ORDER' THEN o.order_no ELSE NULL END AS business_no,
                   p.amount, p.method, p.status, p.confirmed_at, p.created_at, p.updated_at
            FROM payment p
            LEFT JOIN sales_order o
              ON p.business_type = 'SALES_ORDER' AND o.id = p.business_id
            """;

    @Select(BASE_SELECT + " ORDER BY p.created_at DESC, p.id DESC")
    List<Payment> findAll();

    @Select(BASE_SELECT + " WHERE p.id = #{id}")
    Payment findById(@Param("id") Long id);

    @Select("""
            SELECT p.id, p.payment_no, p.business_type, p.business_id,
                   NULL AS business_no, p.amount, p.method, p.status,
                   p.confirmed_at, p.created_at, p.updated_at
            FROM payment p
            WHERE p.id = #{id}
            FOR UPDATE
            """)
    Payment findByIdForUpdate(@Param("id") Long id);

    @Select(BASE_SELECT + """
             WHERE p.business_type = #{businessType} AND p.business_id = #{businessId}
            """)
    Payment findByBusiness(@Param("businessType") String businessType,
                           @Param("businessId") Long businessId);

    @Insert("""
            INSERT INTO payment
                (payment_no, business_type, business_id, amount, method, status)
            VALUES
                (#{paymentNo}, #{businessType}, #{businessId}, #{amount}, #{method}, 'PENDING')
            """)
    int insert(@Param("paymentNo") String paymentNo,
               @Param("businessType") String businessType,
               @Param("businessId") Long businessId,
               @Param("amount") BigDecimal amount,
               @Param("method") String method);

    @Update("""
            UPDATE payment
            SET status = 'PAID',
                method = COALESCE(#{method}, method, 'CASH'),
                confirmed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int confirm(@Param("id") Long id, @Param("method") String method);

    @Update("""
            UPDATE payment
            SET status = 'CANCELLED', updated_at = CURRENT_TIMESTAMP
            WHERE business_type = #{businessType} AND business_id = #{businessId} AND status = 'PENDING'
            """)
    int cancelByBusiness(@Param("businessType") String businessType,
                         @Param("businessId") Long businessId);
}
