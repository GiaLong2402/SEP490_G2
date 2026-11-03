package com.hbsisms.feature.customer;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.catalog.CustomerPrice;
import com.hbsisms.feature.catalog.CustomerPriceRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.dto.PriceProposeRequest;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Task 20 — đề xuất giá riêng B2B (status pending + mở approval_task). */
@Service
@Transactional(readOnly = true)
public class CustomerPriceService {

    static final String REF_TYPE_CUSTOMER_PRICE = "customer_price";

    private final CustomerRepository customerRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final UserRepository userRepository;

    public CustomerPriceService(
            CustomerRepository customerRepository,
            CustomerPriceRepository customerPriceRepository,
            ProductVariantRepository productVariantRepository,
            ApprovalTaskRepository approvalTaskRepository,
            UserRepository userRepository) {
        this.customerRepository = customerRepository;
        this.customerPriceRepository = customerPriceRepository;
        this.productVariantRepository = productVariantRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void propose(Long customerId, PriceProposeRequest request, Long actorUserId) {
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        if (customer.getCustomerType() != CustomerType.B2B) {
            throw ApiException.badRequest("Chỉ khách B2B có giá riêng");
        }

        ProductVariant variant = productVariantRepository
                .findWithProductById(request.variantId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách sản phẩm"));

        if (request.effectiveFrom() != null
                && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw ApiException.badRequest("Ngày hiệu lực đến phải sau hoặc bằng ngày bắt đầu");
        }

        CustomerPrice price = customerPriceRepository
                .findByCustomerIdAndVariantId(customerId, request.variantId())
                .orElse(null);

        if (price != null && price.getStatus() == ApprovalStatus.PENDING) {
            throw ApiException.conflict("Quy cách này đang có đề xuất chờ duyệt");
        }

        BigDecimal proposed = request.unitPrice();
        if (price == null) {
            price = new CustomerPrice();
            price.setCustomer(customer);
            price.setVariant(variant);
            // Lần đầu: unitPrice = đề xuất (chưa áp dụng bán) cho đến khi duyệt (task 21).
            price.setUnitPrice(proposed);
            price.setPendingPrice(proposed);
        } else {
            // Đã có giá duyệt: giữ unitPrice, chỉ ghi đề xuất vào pendingPrice.
            price.setPendingPrice(proposed);
            price.setVariant(variant);
        }
        price.setStatus(ApprovalStatus.PENDING);
        price.setEffectiveFrom(request.effectiveFrom());
        price.setEffectiveTo(request.effectiveTo());
        price.setApprovedBy(null);
        price.setApprovedAt(null);
        CustomerPrice saved = customerPriceRepository.save(price);

        openApprovalTask(saved, customer, variant, proposed, actorUserId);
    }

    private void openApprovalTask(
            CustomerPrice price,
            Customer customer,
            ProductVariant variant,
            BigDecimal proposed,
            Long actorUserId) {
        approvalTaskRepository
                .findByRefTypeAndRefIdAndStatus(REF_TYPE_CUSTOMER_PRICE, price.getId(), ApprovalStatus.PENDING)
                .ifPresent(existing -> {
                    throw ApiException.conflict("Đã có việc duyệt giá đang mở cho đề xuất này");
                });

        AppUser requester = userRepository
                .findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        String money = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN")).format(proposed);
        ApprovalTask task = new ApprovalTask();
        task.setApprovalType(ApprovalType.PRICE_LIST);
        task.setRefType(REF_TYPE_CUSTOMER_PRICE);
        task.setRefId(price.getId());
        task.setTitle(customer.getName() + ": đề xuất giá riêng " + variant.getSku() + " " + money + " ₫");
        task.setStatus(ApprovalStatus.PENDING);
        task.setRequestedBy(requester);
        approvalTaskRepository.save(task);
    }
}
