package com.ecommerce.recommendation_app.repository;

import com.ecommerce.recommendation_app.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByCategory(String category);

    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.id != :productId")
    List<Product> findByCategoryAndNotId(@Param("category") String category, @Param("productId") int productId);
}