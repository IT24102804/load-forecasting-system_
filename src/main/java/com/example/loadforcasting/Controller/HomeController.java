package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.User;
import com.example.loadforcasting.Service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @Autowired
    private UserService userService;

    /**
     * Main landing page after login.
     * Redirects to login if not authenticated.
     */
    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        Integer userId = (Integer) session.getAttribute("userid");
        if (userId == null) return "redirect:/";

        // Put username in session so navbar fragment can display it
        if (session.getAttribute("userName") == null) {
            User user = userService.findById(userId);
            if (user != null) {
                session.setAttribute("userName", user.getName());
            }
        }

        return "home";
    }

    /**
     * After login, redirect /UserInput.html requests to the proper Thymeleaf route.
     * This fixes the old hardcoded link.
     */
    @GetMapping("/UserInput.html")
    public String redirectLegacyUserInput() {
        return "redirect:/home";
    }


    @GetMapping("/forecast/dashboard")
    public String forecastDashboard(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userid");
        if (userId == null) return "redirect:/";
        return "forecast/forecast";
    }
}
