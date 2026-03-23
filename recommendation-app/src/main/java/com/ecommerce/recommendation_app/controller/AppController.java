package com.ecommerce.recommendation_app.controller;

import com.ecommerce.recommendation_app.model.Product;
import com.ecommerce.recommendation_app.model.Purchase;
import com.ecommerce.recommendation_app.model.User;
import com.ecommerce.recommendation_app.repository.PurchaseRepository;
import com.ecommerce.recommendation_app.repository.UserRepository;
import com.ecommerce.recommendation_app.service.EmailService;
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

    @Autowired
    private EmailService emailService;

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

    // ── Forgot Password ──
    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email,
                                 HttpSession session,
                                 Model model) {
        var userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            model.addAttribute("error", "No account found with that email!");
            return "forgot-password";
        }

        // Generate 4 digit PIN
        String pin = String.valueOf((int)(Math.random() * 9000) + 1000);

        User user = userOpt.get();
        user.setResetToken(pin);
        user.setTokenExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);

        try {
            emailService.sendPinEmail(email, pin);
            session.setAttribute("resetEmail", email);
            model.addAttribute("success", "A 4-digit PIN has been sent to your email!");
            model.addAttribute("showPin", true);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to send email. Please try again.");
        }

        return "forgot-password";
    }

    // ── Verify PIN ──
    @PostMapping("/verify-pin")
    public String verifyPin(@RequestParam String pin,
                            HttpSession session,
                            Model model) {
        String email = (String) session.getAttribute("resetEmail");

        if (email == null) {
            model.addAttribute("error", "Session expired! Please try again.");
            return "forgot-password";
        }

        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            model.addAttribute("error", "Something went wrong. Please try again.");
            return "forgot-password";
        }

        User user = userOpt.get();

        if (!pin.equals(user.getResetToken())) {
            model.addAttribute("error", "Invalid PIN! Please try again.");
            model.addAttribute("showPin", true);
            return "forgot-password";
        }

        if (user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            model.addAttribute("error", "PIN has expired! Please request a new one.");
            return "forgot-password";
        }

        session.setAttribute("resetVerified", true);
        return "reset-password";
    }

    // ── Reset Password ──
    @GetMapping("/reset-password")
    public String resetPasswordPage(HttpSession session) {
        if (session.getAttribute("resetVerified") == null) {
            return "redirect:/forgot-password";
        }
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String password,
                                HttpSession session,
                                Model model) {
        if (session.getAttribute("resetVerified") == null) {
            return "redirect:/forgot-password";
        }

        String email = (String) session.getAttribute("resetEmail");
        var userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            model.addAttribute("error", "Something went wrong!");
            return "forgot-password";
        }

        User user = userOpt.get();
        user.setPassword(password);
        user.setResetToken(null);
        user.setTokenExpiry(null);
        userRepository.save(user);

        session.removeAttribute("resetEmail");
        session.removeAttribute("resetVerified");

        model.addAttribute("success", "Password reset successful! Please login.");
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