package com.example.loadforcasting.Controller; // Make sure this matches your package!

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


// IMPORT YOUR FEEDBACK ENTITY HERE (Adjust the package name if needed)
import com.example.loadforcasting.Entity.Feedback;

import java.util.Arrays;
import java.util.List;

@Controller
public class ViewController {

    // This creates the bridge to your submit.html page
    @GetMapping("/submit-feedback")
    public String submitFeedbackPage(Model model) {
        // 1. Give the page the empty object for the form data
        model.addAttribute("feedback", new Feedback());

        // 2. Create the list using underscores to match the HTML logic
        List<String> types = Arrays.asList(
                "PREDICTION_ACCURACY",
                "SYSTEM_BUG",
                "FEATURE_REQUEST",
                "UI_UX_IMPROVEMENT",
                "OTHER"
        );

        // 3. Pass it to the HTML using the exact variable name it expects: "feedbackTypes"
        model.addAttribute("feedbackTypes", types);

        return "feedback/submit";
    }

    // You can do the same for the other files in that folder!
    @GetMapping("/my-feedback")
    public String myFeedbackPage() {
        return "feedback/my-feedback";
    }

    @GetMapping("/feedback-home")
    public String feedbackHomePage() {
        return "feedback/home";
    }
}