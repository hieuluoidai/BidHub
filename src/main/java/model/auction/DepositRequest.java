package model.auction;

import java.time.LocalDateTime;

public class DepositRequest {
    public enum Status { PENDING, APPROVED, REJECTED }

    private String requestId;
    private String userId;
    private String username;
    private double amount;
    private Status status;
    private String adminNote;
    private LocalDateTime createdAt;

    public DepositRequest() {}

    public String getRequestId() {
        return requestId;
    }
    public void setRequestId(String v) {
        this.requestId = v;
    }

    public String getUserId() {
        return userId;
    }
    public void setUserId(String v) {
        this.userId = v;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String v) {
        this.username = v;
    }

    public double getAmount() {
        return amount;
    }
    public void setAmount(double v) {
        this.amount = v;
    }

    public Status getStatus() {
        return status;
    }
    public void setStatus(Status v) {
        this.status = v;
    }

    public String getAdminNote() {
        return adminNote;
    }
    public void setAdminNote(String v) {
        this.adminNote = v;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(LocalDateTime v) {
        this.createdAt = v;
    }
}
