package com.example.loadforcasting.Controller; // Update to your package

import com.example.loadforcasting.Entity.FeedbackStatus;
import com.example.loadforcasting.Entity.Feedback; // Update to your package
import com.example.loadforcasting.Repository.FeedbackRepository; // Update to your package
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/feedback") // Unique path so it doesn't conflict with your user pages
public class AdminFeedbackRestController {

    @Autowired
    private FeedbackRepository feedbackRepository;

    // 1. Send all feedback to her admin dashboard
    @GetMapping
    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAll();
    }

    // 2. Save the admin's reply back to your database
    // 2. Save the admin's reply back to your database
    @PutMapping("/{id}")
    public Feedback replyFeedback(@PathVariable Long id, @RequestBody Feedback updatedFeedback) {
        Optional<Feedback> existing = feedbackRepository.findById(id);
        if (existing.isPresent()) {
            Feedback fb = existing.get();
            // Using YOUR column names here
            fb.setAdminReply(updatedFeedback.getAdminReply());

            // THE FIX: Use the Enum instead of a String
            fb.setStatus(FeedbackStatus.RESOLVED);

            return feedbackRepository.save(fb);
        }
        return null;
    }
}