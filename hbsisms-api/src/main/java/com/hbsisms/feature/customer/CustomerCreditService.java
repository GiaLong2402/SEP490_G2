package com.hbsisms.feature.customer;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.customer.dto.CustomerCreditRequest;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 72 — bật/tắt mua nợ, hạn mức, kỳ hạn.
 * Bật mua nợ hoặc tăng hạn mức → mở {@link ApprovalType#CREDIT_TERMS}; giảm/tắt áp dụng ngay.
 */
@Service
@Transactional(readOnly = true)
public class CustomerCreditService {

    public static final String REF_TYPE = "customer";

    private final CustomerRepository customerRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final UserRepository userRepository;

    public CustomerCreditService(
            CustomerRepository customerRepository,
            ApprovalTaskRepository approvalTaskRepository,
            UserRepository userRepository) {
        this.customerRepository = customerRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void updateCredit(Long customerId, CustomerCreditRequest request, Long actorUserId) {
        Customer customer = customerRepository
                .findWithAccountById(customerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));

        NormalizedCredit wanted = normalize(customer.getCustomerType(), request, customer);
        applyOrPropose(customer, wanted, actorUserId);
        customerRepository.save(customer);
    }

    /**
     * Dùng từ {@link CustomerService#update}: cùng rule duyệt khi đổi hạn mức trên form khách.
     */
    @Transactional
    public void applyOrPropose(
            Customer customer, boolean creditEnabled, BigDecimal creditLimit, short netDays, Long actorUserId) {
        NormalizedCredit wanted = new NormalizedCredit(creditEnabled, creditLimit, netDays);
        validateWanted(customer.getCustomerType(), wanted);
        applyOrPropose(customer, wanted, actorUserId);
    }

    @Transactional
    public void applyApprovalDecision(Long customerId, boolean approved) {
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        if (!customer.hasPendingCreditTerms()) {
            throw ApiException.conflict("Khách không có đề xuất hạn mức chờ duyệt");
        }
        if (approved) {
            if (customer.getPendingCreditEnabled() != null) {
                customer.setCreditEnabled(customer.getPendingCreditEnabled());
            }
            if (customer.getPendingCreditLimit() != null) {
                customer.setCreditLimit(customer.getPendingCreditLimit());
            }
            if (customer.getPendingNetDays() != null) {
                customer.setNetDays(customer.getPendingNetDays());
            }
            if (customer.getCustomerType() == CustomerType.B2B) {
                customer.setCreditEnabled(true);
            }
            if (!customer.isCreditEnabled()) {
                customer.setCreditLimit(BigDecimal.ZERO);
                customer.setNetDays((short) 0);
            }
        }
        customer.clearPendingCreditTerms();
        customerRepository.save(customer);
    }

    private void applyOrPropose(Customer customer, NormalizedCredit wanted, Long actorUserId) {
        if (sameAsLive(customer, wanted)) {
            return;
        }
        if (customer.hasPendingCreditTerms()) {
            throw ApiException.conflict("Đang có đề xuất hạn mức chờ Chủ duyệt");
        }

        if (needsOwnerApproval(customer, wanted)) {
            customer.setPendingCreditEnabled(wanted.creditEnabled());
            customer.setPendingCreditLimit(wanted.creditLimit());
            customer.setPendingNetDays(wanted.netDays());
            openApprovalTask(customer, wanted, actorUserId);
            return;
        }

        customer.setCreditEnabled(wanted.creditEnabled());
        customer.setCreditLimit(wanted.creditLimit());
        customer.setNetDays(wanted.netDays());
    }

    static boolean needsOwnerApproval(Customer customer, NormalizedCredit wanted) {
        boolean enabling = !customer.isCreditEnabled() && wanted.creditEnabled();
        boolean increasingLimit = wanted.creditLimit().compareTo(customer.getCreditLimit()) > 0;
        return enabling || increasingLimit;
    }

    private static boolean sameAsLive(Customer customer, NormalizedCredit wanted) {
        return customer.isCreditEnabled() == wanted.creditEnabled()
                && customer.getCreditLimit().compareTo(wanted.creditLimit()) == 0
                && customer.getNetDays() == wanted.netDays();
    }

    private NormalizedCredit normalize(CustomerType type, CustomerCreditRequest request, Customer live) {
        boolean creditEnabled = request.creditEnabled() == null
                ? live.isCreditEnabled()
                : Boolean.TRUE.equals(request.creditEnabled());
        BigDecimal creditLimit = request.creditLimit() == null ? live.getCreditLimit() : request.creditLimit();
        short netDays = request.netDays() == null ? live.getNetDays() : request.netDays();

        if (type == CustomerType.B2B) {
            creditEnabled = true;
        }
        if (!creditEnabled) {
            creditLimit = BigDecimal.ZERO;
            netDays = 0;
        }
        NormalizedCredit wanted = new NormalizedCredit(creditEnabled, creditLimit, netDays);
        validateWanted(type, wanted);
        return wanted;
    }

    private static void validateWanted(CustomerType type, NormalizedCredit wanted) {
        if (wanted.creditLimit().compareTo(BigDecimal.ZERO) < 0) {
            throw ApiException.badRequest("Hạn mức không được âm");
        }
        if (wanted.netDays() < 0) {
            throw ApiException.badRequest("Số ngày nợ không hợp lệ");
        }
        if (type == CustomerType.B2C
                && !wanted.creditEnabled()
                && (wanted.creditLimit().compareTo(BigDecimal.ZERO) != 0 || wanted.netDays() != 0)) {
            throw ApiException.badRequest("Khách B2C chưa bật mua nợ — không được có hạn mức / kỳ hạn");
        }
    }

    private void openApprovalTask(Customer customer, NormalizedCredit wanted, Long actorUserId) {
        if (actorUserId == null) {
            throw ApiException.badRequest("Cần đăng nhập để gửi đề xuất hạn mức");
        }
        approvalTaskRepository
                .findByRefTypeAndRefIdAndStatus(REF_TYPE, customer.getId(), ApprovalStatus.PENDING)
                .ifPresent(existing -> {
                    throw ApiException.conflict("Đã có việc duyệt hạn mức đang mở cho khách này");
                });

        AppUser requester = userRepository
                .findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        NumberFormat money = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
        String title = customer.getName()
                + ": "
                + (wanted.creditEnabled() ? "bật mua nợ / hạn mức " : "đổi hạn mức ")
                + money.format(wanted.creditLimit())
                + " ₫ · NET "
                + wanted.netDays();

        ApprovalTask task = new ApprovalTask();
        task.setApprovalType(ApprovalType.CREDIT_TERMS);
        task.setRefType(REF_TYPE);
        task.setRefId(customer.getId());
        task.setTitle(title);
        task.setStatus(ApprovalStatus.PENDING);
        task.setRequestedBy(requester);
        approvalTaskRepository.save(task);
    }

    record NormalizedCredit(boolean creditEnabled, BigDecimal creditLimit, short netDays) {}
}
