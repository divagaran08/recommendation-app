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

            // ─────────────────────────────────────────────
            // 1. COLLABORATIVE FILTERING (x2.0)
            // Similar users bought these products
            // ─────────────────────────────────────────────
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

            // ─────────────────────────────────────────────
            // 2. POPULARITY + RATING
            // Highly rated + frequently bought products
            // ─────────────────────────────────────────────
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

            // ─────────────────────────────────────────────
            // 3. TIME DECAY
            // Recent purchases in same category weighted more
            // ─────────────────────────────────────────────
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

            // ─────────────────────────────────────────────
            // 4. PRICE RANGE MATCHING
            // Recommend products in similar price range
            // ─────────────────────────────────────────────
            String avgPriceQuery = """
                SELECT AVG(p.price) AS avg_price
                FROM purchases pu
                JOIN products p ON pu.product_id = p.id
                WHERE pu.user_id = ?
            """;

            double avgPrice = 0;
            try (PreparedStatement ps = conn.prepareStatement(avgPriceQuery)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) avgPrice = rs.getDouble("avg_price");
            }

            if (avgPrice > 0) {
                double minPrice = avgPrice * 0.4;
                double maxPrice = avgPrice * 2.5;

                String priceQuery = """
                    SELECT id FROM products
                    WHERE price BETWEEN ? AND ?
                    AND id NOT IN (
                        SELECT product_id FROM purchases WHERE user_id = ?
                    )
                """;

                try (PreparedStatement ps = conn.prepareStatement(priceQuery)) {
                    ps.setDouble(1, minPrice);
                    ps.setDouble(2, maxPrice);
                    ps.setInt(3, userId);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        scoreMap.merge(id, 1.5, Double::sum);
                    }
                }
            }

            // ─────────────────────────────────────────────
            // 5. FREQUENTLY BOUGHT TOGETHER
            // Products bought by users who bought same items
            // ─────────────────────────────────────────────
            String fbtQuery = """
                SELECT pu2.product_id AS id, COUNT(*) AS freq
                FROM purchases pu1
                JOIN purchases pu2 ON pu1.user_id = pu2.user_id
                    AND pu1.product_id != pu2.product_id
                WHERE pu1.product_id IN (
                    SELECT product_id FROM purchases WHERE user_id = ?
                )
                AND pu2.product_id NOT IN (
                    SELECT product_id FROM purchases WHERE user_id = ?
                )
                GROUP BY pu2.product_id
                ORDER BY freq DESC
            """;

            try (PreparedStatement ps = conn.prepareStatement(fbtQuery)) {
                ps.setInt(1, userId);
                ps.setInt(2, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    double freq = rs.getDouble("freq");
                    scoreMap.merge(id, freq * 1.5, Double::sum);
                }
            }

            // ─────────────────────────────────────────────
            // 6. TRENDING PRODUCTS (last 7 days)
            // What's hot right now
            // ─────────────────────────────────────────────
            String trendingQuery = """
                SELECT p.id, COUNT(*) AS recent_purchases
                FROM purchases pu
                JOIN products p ON pu.product_id = p.id
                WHERE pu.purchase_date >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                AND p.id NOT IN (
                    SELECT product_id FROM purchases WHERE user_id = ?
                )
                GROUP BY p.id
                ORDER BY recent_purchases DESC
                LIMIT 20
            """;

            try (PreparedStatement ps = conn.prepareStatement(trendingQuery)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    double count = rs.getDouble("recent_purchases");
                    scoreMap.merge(id, count * 1.2, Double::sum);
                }
            }

            // ─────────────────────────────────────────────
            // 7. CONTENT BASED FILTERING
            // Category weight - proportional to purchases
            // ─────────────────────────────────────────────
            String categoryWeightQuery = """
                SELECT p.category, COUNT(*) AS cat_count
                FROM purchases pu
                JOIN products p ON pu.product_id = p.id
                WHERE pu.user_id = ?
                GROUP BY p.category
            """;

            Map<String, Double> categoryWeights = new HashMap<>();
            int totalPurchases = 0;

            try (PreparedStatement ps = conn.prepareStatement(categoryWeightQuery)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String cat = rs.getString("category");
                    int count = rs.getInt("cat_count");
                    categoryWeights.put(cat, (double) count);
                    totalPurchases += count;
                }
            }

            if (totalPurchases > 0) {
                for (String cat : categoryWeights.keySet()) {
                    categoryWeights.put(cat, categoryWeights.get(cat) / totalPurchases);
                }
            }

            String allProductsQuery = """
                SELECT id, category FROM products
                WHERE id NOT IN (
                    SELECT product_id FROM purchases WHERE user_id = ?
                )
            """;

            try (PreparedStatement ps = conn.prepareStatement(allProductsQuery)) {
                ps.setInt(1, userId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String cat = rs.getString("category");
                    double weight = categoryWeights.getOrDefault(cat, 0.0);
                    if (weight > 0) {
                        scoreMap.merge(id, weight * 5.0, Double::sum);
                    }
                }
            }

            // ─────────────────────────────────────────────
            // 8. DIVERSITY FILTER
            // Make sure top 10 has mix of categories
            // ─────────────────────────────────────────────
            List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(scoreMap.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            // Get category of each product
            Map<Integer, String> productCategories = new HashMap<>();
            String catQuery = "SELECT id, category FROM products";
            try (PreparedStatement ps = conn.prepareStatement(catQuery)) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    productCategories.put(rs.getInt("id"), rs.getString("category"));
                }
            }

            // Allow max 4 per category in top 10 for diversity
            Map<String, Integer> catCount = new HashMap<>();
            List<Product> recommendations = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();

            for (int i = 0; i < sorted.size() && recommendations.size() < 10; i++) {
                int productId = sorted.get(i).getKey();
                String cat = productCategories.getOrDefault(productId, "");
                int currentCatCount = catCount.getOrDefault(cat, 0);

                if (currentCatCount < 4 && seen.add(productId)) {
                    productRepository.findById(productId).ifPresent(recommendations::add);
                    catCount.put(cat, currentCatCount + 1);
                }
            }

            return recommendations;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    public List<Product> searchProducts(String query) {
        return productRepository.findByNameContainingIgnoreCase(query);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public Product getProductById(int id) {
        return productRepository.findById(id).orElse(null);
    }
}