package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    // ========== EXISTING METHODS ==========

    @GetMapping("/home")
    public String home() {
        return "feedback/home";
    }

    @GetMapping("/submit")
    public String showSubmitForm(Model model) {
        model.addAttribute("feedback", new Feedback());
        model.addAttribute("feedbackTypes", FeedbackType.values());
        return "feedback/submit";
    }






    @PostMapping("/submit")
    public String submitFeedback(@ModelAttribute Feedback feedback,
                                 RedirectAttributes redirectAttributes) {
        // Server-side email validation
        if (feedback.getUserEmail() == null ||
                !feedback.getUserEmail().matches("^[\\w.%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please enter a valid email address.");
            return "redirect:/feedback/submit";
        }

        try {
            feedbackService.saveFeedback(feedback);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Your feedback has been submitted successfully!");
            if (feedback.getUserEmail() != null && !feedback.getUserEmail().isEmpty()) {
                return "redirect:/feedback/my-feedback?email=" + feedback.getUserEmail();
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error submitting feedback: " + e.getMessage());
        }
        return "redirect:/feedback/submit";
    }






    @GetMapping("/my-feedback")
    public String viewMyFeedback(@RequestParam(required = false) String email,
                                 Model model) {
        model.addAttribute("hasFeedback", false);
        if (email != null && !email.isEmpty()) {
            List<Feedback> userFeedback = feedbackService.getFeedbackByEmail(email);
            model.addAttribute("feedbacks", userFeedback);
            model.addAttribute("email", email);
            model.addAttribute("hasFeedback", !userFeedback.isEmpty());
            model.addAttribute("searchValue", email);
            model.addAttribute("displayIdentifier", email);
        }
        return "feedback/my-feedback";
    }

    // ========== NEW EDIT METHODS ==========

    /**
     * SHOW EDIT FORM
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id,
                               @RequestParam String email,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        try {
            Feedback feedback = feedbackService.getFeedbackById(id)
                    .orElseThrow(() -> new RuntimeException("Feedback not found"));

            // Verify this feedback belongs to this user
            if (!feedback.getUserEmail().equals(email)) {
                throw new RuntimeException("You can only edit your own feedback");
            }

            // Check if editable (no admin reply)
            if (!feedbackService.isEditable(id)) {
                throw new RuntimeException("Cannot edit feedback after admin has replied");
            }

            model.addAttribute("feedback", feedback);
            model.addAttribute("feedbackTypes", FeedbackType.values());
            model.addAttribute("email", email);

            return "feedback/edit";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "❌ " + e.getMessage());
            return "redirect:/feedback/my-feedback?email=" + email;
        }
    }

    /**
     * PROCESS EDIT FORM
     */
    @PostMapping("/update")
    public String updateFeedback(@ModelAttribute Feedback feedback,
                                 @RequestParam String email,
                                 RedirectAttributes redirectAttributes) {
        try {
            // Get original feedback to check if editable
            Feedback original = feedbackService.getFeedbackById(feedback.getId())
                    .orElseThrow(() -> new RuntimeException("Feedback not found"));

            // Verify ownership
            if (!original.getUserEmail().equals(email)) {
                throw new RuntimeException("You can only edit your own feedback");
            }

            // Check if still editable
            if (!feedbackService.isEditable(feedback.getId())) {
                throw new RuntimeException("Cannot edit - admin has already replied");
            }

            // Update the feedback
            feedbackService.updateFeedback(feedback);

            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Feedback updated successfully");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "❌ " + e.getMessage());
        }

        return "redirect:/feedback/my-feedback?email=" + email;
    }

    // ========== DELETE METHOD ==========

    @PostMapping("/delete/{id}")
    public String deleteFeedback(@PathVariable Long id,
                                 @RequestParam String email,
                                 RedirectAttributes redirectAttributes) {
        try {
            Feedback feedback = feedbackService.getFeedbackById(id)
                    .orElseThrow(() -> new RuntimeException("Feedback not found"));

            if (!feedback.getUserEmail().equals(email)) {
                throw new RuntimeException("You can only delete your own feedback");
            }

            if (!feedbackService.isDeletable(id)) {
                throw new RuntimeException("Cannot delete feedback after admin has replied");
            }

            feedbackService.deleteFeedback(id);

            redirectAttributes.addFlashAttribute("successMessage",
                    "✅ Feedback deleted successfully");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "❌ Cannot delete: " + e.getMessage());
        }

        return "redirect:/feedback/my-feedback?email=" + email;
    }
}

