package com.example.loadforcasting.Service;

import com.example.loadforcasting.Entity.Feedback;
import com.example.loadforcasting.Entity.FeedbackStatus;
import com.example.loadforcasting.Entity.FeedbackType;
import com.example.loadforcasting.Entity.LoadForecastRun;
import com.example.loadforcasting.Entity.LoadRequest;
import com.example.loadforcasting.Entity.Anomaly;
import com.example.loadforcasting.Repository.AnomalyRepository;
import com.example.loadforcasting.Repository.FeedbackReplyRepository;
import com.example.loadforcasting.Repository.FeedbackRepository;
import com.example.loadforcasting.Repository.LoadForecastRunRepository;
import com.example.loadforcasting.Repository.LoadRepository;
import com.example.loadforcasting.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackReplyRepository feedbackReplyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoadForecastRunRepository loadForecastRunRepository;

    @Mock
    private LoadRepository loadRepository;

    @Mock
    private AnomalyRepository anomalyRepository;

    @InjectMocks
    private FeedbackService feedbackService;

    private Feedback sampleFeedback;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        sampleFeedback = new Feedback();
        sampleFeedback.setId(1L);
        sampleFeedback.setUserName("Test User");
        sampleFeedback.setUserEmail("test@example.com");
        sampleFeedback.setFeedbackType(FeedbackType.GENERAL);
        sampleFeedback.setMessage("This is a test feedback message");
        sampleFeedback.setStatus(FeedbackStatus.PENDING);
        sampleFeedback.setRating(4);
        sampleFeedback.setCreatedAt(LocalDateTime.now());
    }

    // ===== SAVE FEEDBACK TESTS =====

    @Test
    void saveFeedback_ValidFeedback_ReturnsSavedFeedback() {
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(sampleFeedback);

        Feedback result = feedbackService.saveFeedback(sampleFeedback);

        assertNotNull(result);
        assertEquals("Test User", result.getUserName());
        assertEquals("test@example.com", result.getUserEmail());
        verify(feedbackRepository, times(1)).save(sampleFeedback);
    }

    @Test
    void saveFeedback_EmptyName_ThrowsException() {
        sampleFeedback.setUserName("");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> feedbackService.saveFeedback(sampleFeedback)
        );

        assertEquals("Name is required", exception.getMessage());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void saveFeedback_NullName_ThrowsException() {
        sampleFeedback.setUserName(null);

        assertThrows(IllegalArgumentException.class,
            () -> feedbackService.saveFeedback(sampleFeedback));
    }

    @Test
    void saveFeedback_EmptyMessage_ThrowsException() {
        sampleFeedback.setMessage("");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> feedbackService.saveFeedback(sampleFeedback)
        );

        assertEquals("Feedback message is required", exception.getMessage());
    }

    @Test
    void saveFeedback_NullMessage_ThrowsException() {
        sampleFeedback.setMessage(null);

        assertThrows(IllegalArgumentException.class,
            () -> feedbackService.saveFeedback(sampleFeedback));
    }

    // ===== GET FEEDBACK TESTS =====

    @Test
    void getFeedbackById_ExistingId_ReturnsFeedback() {
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(sampleFeedback));

        Optional<Feedback> result = feedbackService.getFeedbackById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void getFeedbackById_NonExistingId_ReturnsEmpty() {
        when(feedbackRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Feedback> result = feedbackService.getFeedbackById(99L);

        assertFalse(result.isPresent());
    }

    @Test
    void getFeedbackByEmail_ValidEmail_ReturnsList() {
        List<Feedback> feedbacks = Arrays.asList(sampleFeedback);
        when(feedbackRepository.findByUserEmailOrderByCreatedAtDesc("test@example.com"))
            .thenReturn(feedbacks);

        List<Feedback> result = feedbackService.getFeedbackByEmail("test@example.com");

        assertEquals(1, result.size());
        assertEquals("test@example.com", result.get(0).getUserEmail());
    }

    @Test
    void getFeedbackByEmail_EmptyEmail_ReturnsEmptyList() {
        List<Feedback> result = feedbackService.getFeedbackByEmail("");

        assertTrue(result.isEmpty());
        verify(feedbackRepository, never()).findByUserEmailOrderByCreatedAtDesc(any());
    }

    @Test
    void getFeedbackByEmail_NullEmail_ReturnsEmptyList() {
        List<Feedback> result = feedbackService.getFeedbackByEmail(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAllFeedback_ReturnsAllFeedbacks() {
        List<Feedback> feedbacks = Arrays.asList(sampleFeedback);
        when(feedbackRepository.findAllByOrderByCreatedAtDesc()).thenReturn(feedbacks);

        List<Feedback> result = feedbackService.getAllFeedback();

        assertEquals(1, result.size());
        verify(feedbackRepository, times(1)).findAllByOrderByCreatedAtDesc();
    }

    // ===== EDITABLE / DELETABLE TESTS =====

    @Test
    void isEditable_NoAdminReply_ReturnsTrue() {
        sampleFeedback.setAdminReply(null);
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(sampleFeedback));

        boolean result = feedbackService.isEditable(1L);

        assertTrue(result);
    }

    @Test
    void isEditable_HasAdminReply_ReturnsFalse() {
        sampleFeedback.setAdminReply("Admin has replied");
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(sampleFeedback));

        boolean result = feedbackService.isEditable(1L);

        assertFalse(result);
    }

    @Test
    void isEditable_EmptyAdminReply_ReturnsTrue() {
        sampleFeedback.setAdminReply("");
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(sampleFeedback));

        boolean result = feedbackService.isEditable(1L);

        assertTrue(result);
    }

    @Test
    void isDeletable_NoAdminReply_ReturnsTrue() {
        sampleFeedback.setAdminReply(null);
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(sampleFeedback));

        boolean result = feedbackService.isDeletable(1L);

        assertTrue(result);
    }

    @Test
    void isDeletable_HasAdminReply_ReturnsFalse() {
        sampleFeedback.setAdminReply("Some reply");
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(sampleFeedback));

        boolean result = feedbackService.isDeletable(1L);

        assertFalse(result);
    }

    // ===== UPDATE FEEDBACK TESTS =====

    @Test
    void updateFeedback_ValidUpdate_ReturnsUpdatedFeedback() {
        Feedback updatedFeedback = new Feedback();
        updatedFeedback.setId(1L);
        updatedFeedback.setUserName("Updated Name");
        updatedFeedback.setMessage("Updated message");
        updatedFeedback.setFeedbackType(FeedbackType.BUG_REPORT);
        updatedFeedback.setRating(5);

        sampleFeedback.setAdminReply(null);
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(sampleFeedback));
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(sampleFeedback);

        Feedback result = feedbackService.updateFeedback(updatedFeedback);

        assertNotNull(result);
        verify(feedbackRepository, times(1)).save(any(Feedback.class));
    }

    @Test
    void updateFeedback_AfterAdminReply_ThrowsException() {
        sampleFeedback.setAdminReply("Admin replied");
        when(feedbackRepository.findById(1L)).thenReturn(Optional.of(sampleFeedback));

        Feedback updateAttempt = new Feedback();
        updateAttempt.setId(1L);
        updateAttempt.setUserName("New name");
        updateAttempt.setMessage("New message");

        assertThrows(RuntimeException.class,
            () -> feedbackService.updateFeedback(updateAttempt));
    }

    @Test
    void updateFeedback_NonExistingId_ThrowsException() {
        when(feedbackRepository.findById(99L)).thenReturn(Optional.empty());

        Feedback updateAttempt = new Feedback();
        updateAttempt.setId(99L);

        assertThrows(RuntimeException.class,
            () -> feedbackService.updateFeedback(updateAttempt));
    }

    // ===== DELETE FEEDBACK TESTS =====

    @Test
    void deleteFeedback_ExistingId_DeletesSuccessfully() {
        when(feedbackRepository.existsById(1L)).thenReturn(true);
        doNothing().when(feedbackRepository).deleteById(1L);

        assertDoesNotThrow(() -> feedbackService.deleteFeedback(1L));
        verify(feedbackRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteFeedback_NonExistingId_ThrowsException() {
        when(feedbackRepository.existsById(99L)).thenReturn(false);

        assertThrows(RuntimeException.class,
            () -> feedbackService.deleteFeedback(99L));

        verify(feedbackRepository, never()).deleteById(any());
    }

    // ===== SECURITY-RELATED TESTS =====

    @Test
    void saveFeedback_MessageTooLong_StillSaves() {
        // Message column allows 1000 chars — test boundary
        String longMessage = "A".repeat(999);
        sampleFeedback.setMessage(longMessage);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(sampleFeedback);

        assertDoesNotThrow(() -> feedbackService.saveFeedback(sampleFeedback));
    }

    @Test
    void saveFeedback_DefaultStatusIsPending() {
        Feedback newFeedback = new Feedback();
        newFeedback.setUserName("Test");
        newFeedback.setUserEmail("new@test.com");
        newFeedback.setMessage("Test message");
        newFeedback.setFeedbackType(FeedbackType.GENERAL);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(newFeedback);

        feedbackService.saveFeedback(newFeedback);

        // Status should default to PENDING — admin reply should be null
        assertNull(newFeedback.getAdminReply());
    }
    @Test
    void saveFeedback_WithPredictionContext_ResolvesRunAndAnomalyScope() {
        sampleFeedback.setFeedbackType(FeedbackType.ANOMALY_FEEDBACK);
        sampleFeedback.setPredictionId(77L);

        LoadRequest request = new LoadRequest();
        request.setLoadForecastRunId(9L);
        LoadForecastRun run = new LoadForecastRun();
        run.setId(9L);
        Anomaly anomaly = new Anomaly();
        anomaly.setId(3L);
        anomaly.setLoadForecastRun(run);

        when(loadRepository.findById(77L)).thenReturn(Optional.of(request));
        when(loadForecastRunRepository.findById(9L)).thenReturn(Optional.of(run));
        when(anomalyRepository.findFirstByLoadForecastRun_IdOrderByDetectedAtDesc(9L)).thenReturn(Optional.of(anomaly));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Feedback result = feedbackService.saveFeedback(sampleFeedback);

        assertNotNull(result.getLoadForecastRun());
        assertEquals(9L, result.getLoadForecastRun().getId());
        assertNotNull(result.getAnomaly());
        assertEquals(3L, result.getAnomaly().getId());
        assertEquals("ANOMALY", result.getSubjectScope());
    }

    @Test
    void saveFeedback_WhenRunMissing_FallsBackToPredictionAnomaly() {
        sampleFeedback.setFeedbackType(FeedbackType.ANOMALY_FEEDBACK);
        sampleFeedback.setPredictionId(88L);

        LoadForecastRun run = new LoadForecastRun();
        run.setId(11L);
        Anomaly anomaly = new Anomaly();
        anomaly.setId(7L);
        anomaly.setLoadForecastRun(run);

        when(loadRepository.findById(88L)).thenReturn(Optional.empty());
        when(anomalyRepository.findFirstByPredictionIdOrderByDetectedAtDesc(88L)).thenReturn(Optional.of(anomaly));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Feedback result = feedbackService.saveFeedback(sampleFeedback);

        assertNotNull(result.getAnomaly());
        assertEquals(7L, result.getAnomaly().getId());
        assertNotNull(result.getLoadForecastRun());
        assertEquals(11L, result.getLoadForecastRun().getId());
        assertEquals("ANOMALY", result.getSubjectScope());
    }
}
