package com.ecommerce.recommendation_app.repository;

import com.ecommerce.recommendation_app.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Integer> {
    List<Purchase> findByUserId(int userId);
}