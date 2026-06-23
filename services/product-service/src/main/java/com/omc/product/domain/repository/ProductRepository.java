package com.omc.product.domain.repository;

import com.omc.product.domain.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("""
        SELECT p FROM Product p
        WHERE (:category IS NULL OR p.category = :category)
          AND (:brand IS NULL OR p.brand = :brand)
          AND (:keyword IS NULL OR p.name LIKE %:keyword%)
          AND p.status = 'ACTIVE'
        """)
    Page<Product> findAllWithFilter(
            @Param("category") String category,
            @Param("brand") String brand,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}