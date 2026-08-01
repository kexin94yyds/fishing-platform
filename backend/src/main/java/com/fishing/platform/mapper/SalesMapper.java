package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.SalesOrder;
import com.fishing.platform.domain.DomainModels.SalesOrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

public interface SalesMapper {

    String ORDER_SELECT = """
            SELECT o.id, o.order_no, o.member_id, m.name AS member_name,
                   o.total_amount, o.status, o.payment_status, o.created_at, o.updated_at
            FROM sales_order o
            LEFT JOIN member m ON m.id = o.member_id
            """;

    @Select(ORDER_SELECT + " ORDER BY o.created_at DESC, o.id DESC")
    List<SalesOrder> findAll();

    @Select(ORDER_SELECT + " WHERE o.id = #{id}")
    SalesOrder findById(@Param("id") Long id);

    @Select(ORDER_SELECT + " WHERE o.order_no = #{orderNo}")
    SalesOrder findByNo(@Param("orderNo") String orderNo);

    @Select("SELECT id FROM sales_order WHERE order_no = #{orderNo}")
    Long findIdByNo(@Param("orderNo") String orderNo);

    @Insert("""
            INSERT INTO sales_order
                (order_no, member_id, total_amount, status, payment_status, created_by)
            VALUES
                (#{orderNo}, #{memberId}, #{totalAmount}, 'PENDING_PAYMENT', 'PENDING', #{createdBy})
            """)
    int insertOrder(@Param("orderNo") String orderNo,
                    @Param("memberId") Long memberId,
                    @Param("totalAmount") BigDecimal totalAmount,
                    @Param("createdBy") Long createdBy);

    @Insert("""
            INSERT INTO sales_order_item
                (order_id, product_id, product_name, quantity, unit_price, line_amount)
            VALUES
                (#{orderId}, #{productId}, #{productName}, #{quantity}, #{unitPrice}, #{lineAmount})
            """)
    int insertItem(@Param("orderId") Long orderId,
                   @Param("productId") Long productId,
                   @Param("productName") String productName,
                   @Param("quantity") Integer quantity,
                   @Param("unitPrice") BigDecimal unitPrice,
                   @Param("lineAmount") BigDecimal lineAmount);

    @Select("""
            SELECT id, order_id, product_id, product_name, quantity, unit_price, line_amount
            FROM sales_order_item
            WHERE order_id = #{orderId}
            ORDER BY id
            """)
    List<SalesOrderItem> findItems(@Param("orderId") Long orderId);

    @Update("""
            UPDATE sales_order
            SET payment_status = 'PAID', status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND payment_status = 'PENDING' AND status = 'PENDING_PAYMENT'
            """)
    int markPaid(@Param("id") Long id);
}
