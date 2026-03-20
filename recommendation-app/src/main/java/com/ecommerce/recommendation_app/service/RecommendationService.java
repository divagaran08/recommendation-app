package com.ecommerce.recommendation_app.service;

import com.ecommerce.recommendation_app.model.Product;
import com.ecommerce.recommendation_app.repository.ProductRepository;
import com.ecommerce.recommendation_app.repository.PurchaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
public class RecommendationService {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    public List<Product> getRecommendations(int userId) {

        Map<Integer, Double> scoreMap = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {

            // 1. COLLABORATIVE FILTERING
            String collabQuery = """
                SELECT p.id, COUNT(*) AS freq
                FROM purchases pu
                JOIN products p ON pu.product_id = p.id
                WHERE pu.user_id IN (
                    SELECT DISTINCT pu2.user_id
                    FROM purchases pu1
                    JOIN purchases pu2 ON pu1.product_id = pu2.product_id
                    WHERE pu1.user_id = ? AND pu2.user_id != ?
                )
                AND pu.product_id NOT IN (
                    SELECT product_id FROM purchases WHERE user_id = ?
                )
                GROUP BY p.id
            """;

            try (PreparedStatement ps = conn.prepareStatement(collabQuery)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ps.setInt(3, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    double freq = rs.getDouble("freq");
                    scoreMap.merge(id, freq * 2.0, Double::sum);
                }
            }

            // 2. POPULARITY + RATING
            String popularityQuery = """
                SELECT p.id, p.rating, COUNT(pu.product_id) AS purchase_count
                FROM products p
                LEFT JOIN purchases pu ON p.id = pu.product_id
                WHERE p.id NOT IN (
                    SELECT product_id FROM purchases WHERE user_id = ?
                )
                GROUP BY p.id, p.rating
            """;

            try (PreparedStatement ps = conn.prepareStatement(popularityQuery)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    double rating = rs.getDouble("rating");
                    double count = rs.getDouble("purchase_count");
                    double score = rating * Math.log(count + 1);
                    scoreMap.merge(id, score, Double::sum);
                }
            }

            // 3. TIME DECAY
            String timeDecayQuery = """
                SELECT p2.id,
                       DATEDIFF(NOW(), pu.purchase_date) AS days_ago
                FROM purchases pu
                JOIN products p1 ON pu.product_id = p1.id
                JOIN products p2 ON p1.category = p2.category
                WHERE pu.user_id = ?
                AND p2.id NOT IN (
                    SELECT product_id FROM purchases WHERE user_id = ?
                )
            """;

            try (PreparedStatement ps = conn.prepareStatement(timeDecayQuery)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int daysAgo = rs.getInt("days_ago");
                    double score = Math.exp(-0.01 * daysAgo);
                    scoreMap.merge(id, score, Double::sum);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // SORT by score, return top 10 products
        List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(scoreMap.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Product> recommendations = new ArrayList<>();
        for (int i = 0; i < sorted.size() && recommendations.size() < 10; i++) {
            int productId = sorted.get(i).getKey();
            productRepository.findById(productId).ifPresent(recommendations::add);
        }

        return recommendations;
    }

    public List<Product> searchProducts(String query) {
        return productRepository.findByNameContainingIgnoreCase(query);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
}
