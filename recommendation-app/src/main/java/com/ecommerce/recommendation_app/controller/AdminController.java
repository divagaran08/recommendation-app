package com.ecommerce.recommendation_app.controller;

import com.ecommerce.recommendation_app.model.Product;
import com.ecommerce.recommendation_app.model.Purchase;
import com.ecommerce.recommendation_app.model.User;
import com.ecommerce.recommendation_app.repository.ProductRepository;
import com.ecommerce.recommendation_app.repository.PurchaseRepository;
import com.ecommerce.recommendation_app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    // ── Check Admin ──
    private boolean isAdmin(HttpSession session) {
        User user = (User) session.getAttribute("user");
        return user != null && user.isAdmin();
    }

    // ── Dashboard ──
    @GetMapping
    public String dashboard(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/";

        long totalUsers = userRepository.count();
        long totalProducts = productRepository.count();
        long totalPurchases = purchaseRepository.count();

        List<User> recentUsers = userRepository.findAll()
                .stream().limit(5).toList();

        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalProducts", totalProducts);
        model.addAttribute("totalPurchases", totalPurchases);
        model.addAttribute("recentUsers", recentUsers);

        return "admin/dashboard";
    }

    // ── Products ──
    @GetMapping("/products")
    public String products(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/";
        model.addAttribute("products", productRepository.findAll());
        return "admin/products";
    }

    @PostMapping("/products/add")
    public String addProduct(@RequestParam String name,
                             @RequestParam String category,
                             @RequestParam double price,
                             @RequestParam double rating,
                             HttpSession session) {
        if (!isAdmin(session)) return "redirect:/";

        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setPrice(price);
        product.setRating(rating);
        productRepository.save(product);

        return "redirect:/admin/products";
    }

    @PostMapping("/products/delete/{id}")
    public String deleteProduct(@PathVariable int id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/";
        productRepository.deleteById(id);
        return "redirect:/admin/products";
    }

    // ── Users ──
    @GetMapping("/users")
    public String users(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/";
        model.addAttribute("users", userRepository.findAll());
        return "admin/users";
    }

    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable int id, HttpSession session) {
        if (!isAdmin(session)) return "redirect:/";
        userRepository.deleteById(id);
        return "redirect:/admin/users";
    }

    // ── Purchases ──
    @GetMapping("/purchases")
    public String purchases(HttpSession session, Model model) {
        if (!isAdmin(session)) return "redirect:/";
        List<Purchase> purchases = purchaseRepository.findAll();
        model.addAttribute("purchases", purchases);
        return "admin/purchases";
    }
}