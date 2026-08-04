package com.flashflow.trade.aftersale;

import java.time.Instant;

import com.flashflow.trade.common.BusinessException;
import com.flashflow.trade.common.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "return_request")
public class ReturnRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_no", nullable = false, unique = true, length = 40)
    private String returnNo;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "order_no", nullable = false, length = 40)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReturnStatus status;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ReturnRequestEntity() {
    }

    public ReturnRequestEntity(
            String returnNo,
            Long orderId,
            String orderNo,
            Long userId,
            String reason
    ) {
        this.returnNo = returnNo;
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.reason = reason;
        this.status = ReturnStatus.PENDING;
        this.requestedAt = Instant.now();
    }

    public void approve(String note, Instant now) {
        decide(ReturnStatus.APPROVED, note, now);
    }

    public void reject(String note, Instant now) {
        decide(ReturnStatus.REJECTED, note, now);
    }

    private void decide(ReturnStatus target, String note, Instant now) {
        if (status != ReturnStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "该退款申请已经处理");
        }
        status = target;
        adminNote = note;
        processedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getReturnNo() {
        return returnNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
