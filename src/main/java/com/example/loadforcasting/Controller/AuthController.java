package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.User;
import com.example.loadforcasting.Service.UserService;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    @Autowired
    private UserService service;

    @GetMapping("/")
    public String landingPage() {
        return "landing";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }


    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(User user, Model model) {
        String result = service.register(user);
        if (!result.equals("success")) {
            model.addAttribute("error", result);
            return "register";
        }
        return "redirect:/";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpSession session,
                        Model model) {

        User user = service.login(email, password);

        if (user != null) {
            session.setAttribute("userid", user.getId());
            session.setAttribute("role", user.getRole());

            if ("Admin".equalsIgnoreCase(user.getRole()) || "Administrator".equalsIgnoreCase(user.getRole())) {
                return "redirect:/admin/index.html"; // Routing to your shiny new admin portal
            } else {
                return "redirect:/home"; // Routing to your main prediction page
            }
        }

        model.addAttribute("error", "Invalid Email or Password");
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        // FIX: Changed to Integer
        Integer id = (Integer) session.getAttribute("userid");
        if (id == null) return "redirect:/";

        User user = service.findById(id);
        model.addAttribute("user", user);
        return "dashboard";
    }

    @GetMapping("/admin")
    public String adminPage(HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (!"Admin".equalsIgnoreCase(role)) return "redirect:/";
        return "admin";
    }

    @GetMapping("/viewUsers")
    public String viewUsers(HttpSession session, Model model) {
        String role = (String) session.getAttribute("role");
        if (!"Admin".equalsIgnoreCase(role)) return "redirect:/";
        model.addAttribute("users", service.getAllUsers());
        return "viewUsers";
    }

    // FIX: Changed @PathVariable to Integer
    @GetMapping("/adminDelete/{id}")
    public String adminDelete(@PathVariable Integer id, HttpSession session) {
        String role = (String) session.getAttribute("role");
        if (!"Admin".equalsIgnoreCase(role)) return "redirect:/";

        User user = service.findById(id);
        if (user != null && !"Admin".equalsIgnoreCase(user.getRole())) {
            service.delete(id);
        }

        return "redirect:/viewUsers";
    }

    @GetMapping("/edit")
    public String editPage(HttpSession session, Model model) {
        // FIX: Changed to Integer
        Integer id = (Integer) session.getAttribute("userid");
        if (id == null) return "redirect:/";

        model.addAttribute("user", service.findById(id));
        return "edit";
    }

    @PostMapping("/update")
    public String update(User user, HttpSession session) {
        // FIX: Changed to Integer
        Integer id = (Integer) session.getAttribute("userid");
        if (id == null) return "redirect:/";

        User existing = service.findById(id);
        existing.setName(user.getName());
        existing.setEmail(user.getEmail());
        existing.setPassword(user.getPassword());

        service.update(existing);
        return "redirect:/dashboard";
    }

    @GetMapping("/deleteAccount")
    public String deleteAccount(HttpSession session) {
        // FIX: Changed to Integer
        Integer id = (Integer) session.getAttribute("userid");
        if (id != null) {
            service.delete(id);
            session.invalidate();
        }
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }


    @GetMapping("/public-site")
    public String publicSite(HttpSession session) {
        // Allow admin to view the public forecast page directly
        Integer id = (Integer) session.getAttribute("userid");
        if (id == null) return "redirect:/";
        return "redirect:/home";
    }


}
