package com.example.loadforcasting.Controller;

 // Adjust package name if you put it in a different folder

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component // This tells Spring Boot to activate this filter automatically
public class AdminSecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        HttpSession session = req.getSession(false);

        String requestURI = req.getRequestURI();

        // 1. Check if the user is trying to access ANY file inside the /admin/ folder
        if (requestURI.startsWith("/admin/") || requestURI.startsWith("/api/admin/")) {
            boolean isAuthorized = false;

            // 2. Check if they have an active session AND their role is Admin
            if (session != null) {
                String role = (String) session.getAttribute("role");
                if ("Admin".equalsIgnoreCase(role) || "Administrator".equalsIgnoreCase(role)) {
                    isAuthorized = true;
                }
            }

            // 3. If they are not an Admin, kick them back to the login page
            if (!isAuthorized) {
                res.sendRedirect("/");
                return; // Stop processing the request immediately
            }
        }

        // 4. If they are an Admin, or if they are just accessing normal public files, let them through
        chain.doFilter(request, response);
    }
}
