package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Service.FeedbackService;
import com.example.loadforcasting.dto.AdminFeedbackItem;
import com.example.loadforcasting.dto.AdminFeedbackReplyRequest;
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
    public List<AdminFeedbackItem> getAllFeedback(HttpSession session) {
        requireAdmin(session);
        return feedbackService.getAllFeedback().stream()
                .map(this::toAdminFeedbackItem)
                .toList();
    }

    @PutMapping("/{id}")
    public AdminFeedbackItem replyFeedback(@PathVariable Long id,
                                  @RequestBody AdminFeedbackReplyRequest request,
                                  HttpSession session) {
        requireAdmin(session);
        Integer userId = (Integer) session.getAttribute("userid");
        if (request == null || request.adminReply() == null || request.adminReply().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin reply is required.");
        }
        try {
            Feedback saved = feedbackService.replyFeedback(id, request.adminReply().trim(), userId);
            return toAdminFeedbackItem(saved);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private AdminFeedbackItem toAdminFeedbackItem(Feedback feedback) {
        return new AdminFeedbackItem(
                feedback.getId(),
                feedback.getUserName(),
                feedback.getUserEmail(),
                feedback.getMessage(),
                feedback.getStatus() != null ? feedback.getStatus().name() : null,
                feedback.getAdminReply()
        );
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
