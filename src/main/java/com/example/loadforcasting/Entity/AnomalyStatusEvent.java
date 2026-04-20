package com.example.loadforcasting.Entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "anomaly_status_events")
public class AnomalyStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anomaly_id", nullable = false)
    private Anomaly anomaly;

    @Column(name = "old_status", nullable = false, length = 50)
    private String oldStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @Column(name = "note", length = 1000)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "acted_by_user_id", nullable = false)
    private User actedByUser;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt;

    @PrePersist
    protected void onCreate() {
        if (actedAt == null) {
            actedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Anomaly getAnomaly() {
        return anomaly;
    }

    public void setAnomaly(Anomaly anomaly) {
        this.anomaly = anomaly;
    }

    public String getOldStatus() {
        return oldStatus;
    }

    public void setOldStatus(String oldStatus) {
        this.oldStatus = oldStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public User getActedByUser() {
        return actedByUser;
    }

    public void setActedByUser(User actedByUser) {
        this.actedByUser = actedByUser;
    }

    public LocalDateTime getActedAt() {
        return actedAt;
    }

    public void setActedAt(LocalDateTime actedAt) {
        this.actedAt = actedAt;
    }
}
