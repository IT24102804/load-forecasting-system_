package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Service.FeedbackService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackRestController {

    @Autowired
    private FeedbackService feedbackService;

    @GetMapping
    public List<Feedback> getAllFeedback(HttpSession session) {
        requireAdmin(session);
        return feedbackService.getAllFeedback();
    }

    @PutMapping("/{id}")
    public Feedback replyFeedback(@PathVariable Long id,
                                  @RequestBody Feedback updatedFeedback,
                                  HttpSession session) {
        requireAdmin(session);
        Integer userId = (Integer) session.getAttribute("userid");
        try {
            return feedbackService.replyFeedback(id, updatedFeedback.getAdminReply(), userId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private void requireAdmin(HttpSession session) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin session is required.");
        }
        String role = (String) session.getAttribute("role");
        if (role == null || (!"Admin".equalsIgnoreCase(role) && !"Administrator".equalsIgnoreCase(role))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin session is required.");
        }
    }
}
