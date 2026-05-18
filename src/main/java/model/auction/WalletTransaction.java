package model.auction;

import java.time.LocalDateTime;

/**
 * Model đại diện cho một giao dịch tài chính thực tế trong ví người dùng.
 */
public class WalletTransaction {
    public enum TransactionType {
        TOPUP,      // Nạp tiền
        PAYMENT,    // Thanh toán khi thắng đấu giá
        EARNING,    // Người bán nhận tiền
        REFUND      // Hoàn tiền (nếu có)
    }

    private String transactionId;
    private String userId;
    private double amount;
    private TransactionType type;
    private String description;
    private LocalDateTime createdAt;

    public WalletTransaction(String transactionId, String userId, double amount, TransactionType type, String description, LocalDateTime createdAt) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.createdAt = createdAt;
    }

    // Getters
    public String getTransactionId() { return transactionId; }
    public String getUserId() { return userId; }
    public double getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
