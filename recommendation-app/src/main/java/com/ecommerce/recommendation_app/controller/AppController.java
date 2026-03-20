package com.ecommerce.recommendation_app.controller;

import com.ecommerce.recommendation_app.model.Product;
import com.ecommerce.recommendation_app.model.Purchase;
import com.ecommerce.recommendation_app.model.User;
import com.ecommerce.recommendation_app.repository.PurchaseRepository;
import com.ecommerce.recommendation_app.repository.UserRepository;
import com.ecommerce.recommendation_app.service.RecommendationService;
import com.ecommerce.recommendation_app.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.List;

@Controller
public class AppController {

    @Autowired
    private UserService userService;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private UserRepository userRepository;

    // ── Login Page ──
    @GetMapping("/")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {
        var user = userService.login(username, password);
        if (user.isPresent()) {
            session.setAttribute("user", user.get());
            return "redirect:/home";
        } else {
            model.addAttribute("error", "Invalid username or password!");
            return "login";
        }
    }

    // ── Register ──
    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam String email,
                           Model model) {
        if (userRepository.findByUsername(username).isPresent()) {
            model.addAttribute("error", "Username already exists!");
            return "login";
        }
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setPassword(password);
        newUser.setEmail(email);
        userRepository.save(newUser);
        model.addAttribute("success", "Account created! Please login.");
        return "login";
    }

    // ── Home Page ──
    @GetMapping("/home")
    public String homePage(@RequestParam(required = false) String category,
                           HttpSession session,
                           Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";

        List<Product> products = (category != null && !category.isEmpty())
                ? recommendationService.getProductsByCategory(category)
                : recommendationService.getAllProducts();

        List<Product> recommendations = recommendationService.getRecommendations(user.getId());
        List<Purchase> userPurchases = purchaseRepository.findByUserId(user.getId());
        List<Integer> purchasedIds = userPurchases.stream().map(Purchase::getProductId).toList();

        model.addAttribute("user", user);
        model.addAttribute("products", products);
        model.addAttribute("recommendations", recommendations);
        model.addAttribute("purchasedIds", purchasedIds);
        model.addAttribute("selectedCategory", category);

        return "home";
    }

    // ── Search ──
    @GetMapping("/search")
    public String search(@RequestParam String query,
                         HttpSession session,
                         Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";

        List<Product> results = recommendationService.searchProducts(query);
        List<Product> recommendations = recommendationService.getRecommendations(user.getId());
        List<Purchase> userPurchases = purchaseRepository.findByUserId(user.getId());
        List<Integer> purchasedIds = userPurchases.stream().map(Purchase::getProductId).toList();

        model.addAttribute("user", user);
        model.addAttribute("products", results);
        model.addAttribute("recommendations", recommendations);
        model.addAttribute("purchasedIds", purchasedIds);
        model.addAttribute("query", query);

        return "home";
    }

    // ── Buy Product ──
    @PostMapping("/buy/{productId}")
    public String buyProduct(@PathVariable int productId,
                             HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";

        List<Purchase> existing = purchaseRepository.findByUserId(user.getId());
        boolean alreadyBought = existing.stream()
                .anyMatch(p -> p.getProductId() == productId);

        if (!alreadyBought) {
            Purchase purchase = new Purchase();
            purchase.setUserId(user.getId());
            purchase.setProductId(productId);
            purchase.setPurchaseDate(LocalDateTime.now());
            purchaseRepository.save(purchase);
        }

        return "redirect:/home";
    }

    // ── Purchase History ──
    @GetMapping("/history")
    public String history(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/";

        List<Purchase> purchases = purchaseRepository.findByUserId(user.getId());
        List<Product> boughtProducts = purchases.stream()
                .map(p -> recommendationService.getProductById(p.getProductId()))
                .filter(p -> p != null)
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("boughtProducts", boughtProducts);

        return "history";
    }

    // ── Logout ──
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }
}