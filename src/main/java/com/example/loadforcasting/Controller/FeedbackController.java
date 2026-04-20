package com.example.loadforcasting.Controller;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Repository.UserRepository;
import com.example.loadforcasting.Service.FeedbackService;
import jakarta.servlet.http.HttpSession;
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

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/home")
    public String home() {
        return "feedback/home";
    }

    @GetMapping("/submit")
    public String showSubmitForm(@RequestParam(required = false) Long predictionId,
                                 @RequestParam(required = false) Long anomalyId,
                                 @RequestParam(required = false) FeedbackType feedbackType,
                                 HttpSession session,
                                 Model model) {
        Feedback feedback = new Feedback();
        Integer userId = (Integer) session.getAttribute("userid");
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                feedback.setUserName(user.getName());
                feedback.setUserEmail(user.getEmail());
            });
        }
        if (predictionId != null) {
            feedback.setPredictionId(predictionId);
        }
        if (feedbackType != null) {
            feedback.setFeedbackType(feedbackType);
        } else if (predictionId != null) {
            feedback.setFeedbackType(FeedbackType.ANOMALY_FEEDBACK);
        }

        model.addAttribute("feedback", feedback);
        model.addAttribute("feedbackTypes", FeedbackType.values());
        model.addAttribute("anomalyContext", predictionId != null);
        model.addAttribute("contextPredictionId", predictionId);
        model.addAttribute("contextAnomalyId", anomalyId);
        model.addAttribute("prefillAnomalyId", anomalyId);
        model.addAttribute("messageHint",
                predictionId != null
                        ? "Describe why this anomaly alert looks plausible or needs review."
                        : "Describe the issue, anomaly, or suggestion in detail...");
        return "feedback/submit";
    }

    @PostMapping("/submit")
    public String submitFeedback(@ModelAttribute Feedback feedback,
                                 @RequestParam(required = false) Long anomalyId,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Integer userId = (Integer) session.getAttribute("userid");

        if (feedback.getPredictionId() != null) {
            feedback.setFeedbackType(FeedbackType.ANOMALY_FEEDBACK);
        }

        if (feedback.getUserEmail() == null ||
                !feedback.getUserEmail().matches("^[\\w.%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Please enter a valid email address.");
            return "redirect:" + buildSubmitRedirect(feedback.getPredictionId(), anomalyId);
        }

        try {
            if (userId != null) {
                feedbackService.saveFeedback(feedback, userId);
            } else {
                feedbackService.saveFeedback(feedback);
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Your feedback has been submitted successfully!");
            String email = userId != null
                    ? userRepository.findById(userId).map(u -> u.getEmail()).orElse(feedback.getUserEmail())
                    : feedback.getUserEmail();
            return "redirect:/feedback/my-feedback?email=" + email;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error submitting feedback: " + e.getMessage());
            return "redirect:" + buildSubmitRedirect(feedback.getPredictionId(), anomalyId);
        }
    }

    @GetMapping("/my-feedback")
    public String viewMyFeedback(@RequestParam(required = false) String email,
                                 HttpSession session,
                                 Model model) {
        Integer userId = (Integer) session.getAttribute("userid");
        List<Feedback> userFeedback;
        String displayEmail;
        if (userId != null) {
            userFeedback = feedbackService.getFeedbackByUserId(userId);
            displayEmail = userRepository.findById(userId).map(u -> u.getEmail()).orElse(email);
        } else {
            userFeedback = feedbackService.getFeedbackByEmail(email);
            displayEmail = email;
        }

        model.addAttribute("feedbacks", userFeedback);
        model.addAttribute("email", displayEmail);
        model.addAttribute("hasFeedback", !userFeedback.isEmpty());
        model.addAttribute("searchValue", displayEmail);
        model.addAttribute("displayIdentifier", displayEmail);
        return "feedback/my-feedback";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id,
                               @RequestParam(required = false) String email,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        Integer userId = (Integer) session.getAttribute("userid");

        try {
            Feedback feedback = feedbackService.getFeedbackById(id)
                    .orElseThrow(() -> new RuntimeException("Feedback not found"));

            boolean owned = userId != null
                    ? feedbackService.belongsToUser(id, userId)
                    : feedback.getUserEmail() != null && feedback.getUserEmail().equals(email);
            if (!owned) {
                throw new RuntimeException("You can only edit your own feedback");
            }
            if (!feedbackService.isEditable(id)) {
                throw new RuntimeException("Cannot edit feedback after admin has replied");
            }

            model.addAttribute("feedback", feedback);
            model.addAttribute("feedbackTypes", FeedbackType.values());
            model.addAttribute("email", userId != null ? userRepository.findById(userId).map(u -> u.getEmail()).orElse(email) : email);
            return "feedback/edit";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/feedback/my-feedback";
        }
    }

    @PostMapping("/update")
    public String updateFeedback(@ModelAttribute Feedback feedback,
                                 @RequestParam(required = false) String email,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Integer userId = (Integer) session.getAttribute("userid");
        try {
            boolean owned = userId != null
                    ? feedbackService.belongsToUser(feedback.getId(), userId)
                    : feedbackService.getFeedbackById(feedback.getId())
                    .map(existing -> existing.getUserEmail() != null && existing.getUserEmail().equals(email))
                    .orElse(false);
            if (!owned) {
                throw new RuntimeException("You can only edit your own feedback");
            }
            if (!feedbackService.isEditable(feedback.getId())) {
                throw new RuntimeException("Cannot edit - admin has already replied");
            }

            if (userId != null) {
                feedbackService.updateFeedback(feedback, userId);
            } else {
                feedbackService.updateFeedback(feedback);
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Feedback updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        String redirectEmail = userId != null
                ? userRepository.findById(userId).map(u -> u.getEmail()).orElse(email)
                : email;
        return redirectEmail != null
                ? "redirect:/feedback/my-feedback?email=" + redirectEmail
                : "redirect:/feedback/my-feedback";
    }

    @PostMapping("/delete/{id}")
    public String deleteFeedback(@PathVariable Long id,
                                 @RequestParam(required = false) String email,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        Integer userId = (Integer) session.getAttribute("userid");
        try {
            boolean owned = userId != null
                    ? feedbackService.belongsToUser(id, userId)
                    : feedbackService.getFeedbackById(id)
                    .map(feedback -> feedback.getUserEmail() != null && feedback.getUserEmail().equals(email))
                    .orElse(false);
            if (!owned) {
                throw new RuntimeException("You can only delete your own feedback");
            }
            if (!feedbackService.isDeletable(id)) {
                throw new RuntimeException("Cannot delete feedback after admin has replied");
            }

            if (userId != null) {
                feedbackService.deleteFeedback(id, userId);
            } else {
                feedbackService.deleteFeedback(id);
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    "Feedback deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Cannot delete: " + e.getMessage());
        }

        String redirectEmail = userId != null
                ? userRepository.findById(userId).map(u -> u.getEmail()).orElse(email)
                : email;
        return redirectEmail != null
                ? "redirect:/feedback/my-feedback?email=" + redirectEmail
                : "redirect:/feedback/my-feedback";
    }

    private String buildSubmitRedirect(Long predictionId, Long anomalyId) {
        if (predictionId == null) {
            return "/feedback/submit";
        }

        String redirect = "/feedback/submit?predictionId=" + predictionId + "&feedbackType=" + FeedbackType.ANOMALY_FEEDBACK.name();
        if (anomalyId != null) {
            redirect += "&anomalyId=" + anomalyId;
        }
        return redirect;
    }
}
