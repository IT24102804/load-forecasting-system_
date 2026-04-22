package com.example.loadforcasting.dto;

public record AdminFeedbackItem(
        Long id,
        String userName,
        String userEmail,
        String message,
        String status,
        String adminReply
) {
}
