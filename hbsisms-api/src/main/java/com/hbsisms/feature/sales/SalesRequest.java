package com.hbsisms.feature.sales;

import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Incoming B2B enquiry (phone / Zalo / web) before it becomes a quote or order. */
@Entity
@Table(name = "sales_request", indexes = @Index(name = "idx_request_status", columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
public class SalesRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** Free-text name when the caller is not a known customer yet. */
    @Column(length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleChannel channel;

    @Column(columnDefinition = "text")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.NEW;

    @Column(nullable = false)
    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private AppUser handledBy;

    @Column(columnDefinition = "text")
    private String note;

    /**
     * Kết quả khả thi do Sales ghi: {@code true}=Có, {@code false}=Không, {@code null}=chưa đánh giá.
     */
    @Column
    private Boolean feasible;

    /** SL (khay) Sales xác nhận đáp ứng được. */
    @Column
    private Integer fulfillableQty;

    /** Phần thiếu (khay) so với nhu cầu khách. */
    @Column
    private Integer shortageQty;

    /** Ngày dự kiến có hàng / giao được phần còn thiếu. */
    @Column
    private LocalDate expectedDate;

    /** Lý do từ chối — bắt buộc khi {@link RequestStatus#REJECTED}. */
    @Column(columnDefinition = "text")
    private String rejectReason;

    @PrePersist
    void defaultReceivedAt() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
