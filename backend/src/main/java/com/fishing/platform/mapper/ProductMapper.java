package com.fishing.platform.mapper;

import com.fishing.platform.domain.DomainModels.Product;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

public interface ProductMapper {

    String BASE_SELECT = """
            SELECT id, sku, name, category, price, stock_quantity, status, version, created_at, updated_at
            FROM product
            """;

    @Select(BASE_SELECT + " ORDER BY id")
    List<Product> findAll();

    @Select(BASE_SELECT + " WHERE id = #{id}")
    Product findById(@Param("id") Long id);

    @Select(BASE_SELECT + " WHERE id = #{id} FOR UPDATE")
    Product findByIdForUpdate(@Param("id") Long id);

    @Select(BASE_SELECT + " WHERE sku = #{sku}")
    Product findBySku(@Param("sku") String sku);

    @Insert("""
            INSERT INTO product (sku, name, category, price, stock_quantity, status)
            VALUES (#{sku}, #{name}, #{category}, #{price}, #{stockQuantity}, #{status})
            """)
    int insert(@Param("sku") String sku,
               @Param("name") String name,
               @Param("category") String category,
               @Param("price") BigDecimal price,
               @Param("stockQuantity") Integer stockQuantity,
               @Param("status") String status);

    @Update("""
            UPDATE product
            SET sku = #{sku}, name = #{name}, category = #{category}, price = #{price},
                stock_quantity = #{stockQuantity}, status = #{status},
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND version = #{version}
            """)
    int update(@Param("id") Long id,
               @Param("sku") String sku,
               @Param("name") String name,
               @Param("category") String category,
               @Param("price") BigDecimal price,
               @Param("stockQuantity") Integer stockQuantity,
               @Param("status") String status,
               @Param("version") Long version);

    @Update("""
            UPDATE product
            SET stock_quantity = stock_quantity - #{quantity},
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND status = 'ACTIVE'
              AND stock_quantity >= #{quantity}
            """)
    int decrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Update("""
            UPDATE product
            SET stock_quantity = stock_quantity + #{quantity},
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int incrementStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
