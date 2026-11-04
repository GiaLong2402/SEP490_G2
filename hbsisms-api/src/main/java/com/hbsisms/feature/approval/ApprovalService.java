package com.hbsisms.feature.approval;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.approval.dto.ApprovalDecisionRequest;
import com.hbsisms.feature.approval.dto.ApprovalListItemResponse;
import com.hbsisms.feature.catalog.CustomerPrice;
import com.hbsisms.feature.catalog.CustomerPriceRepository;
import com.hbsisms.feature.customer.CustomerPriceService;
import com.hbsisms.feature.inventory.DamageReportService;
import com.hbsisms.feature.inventory.StocktakeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** List việc duyệt + quyết định (giá B2B, kiểm kê, báo hỏng, …). */
@Service
@Transactional(readOnly = true)
public class ApprovalService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ApprovalTaskRepository approvalTaskRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final StocktakeService stocktakeService;
    private final UserRepository userRepository;
    private final DamageReportService damageReportService;

    public ApprovalService(
            ApprovalTaskRepository approvalTaskRepository,
            CustomerPriceRepository customerPriceRepository,
            StocktakeService stocktakeService,
            UserRepository userRepository,
            DamageReportService damageReportService) {
        this.approvalTaskRepository = approvalTaskRepository;
        this.customerPriceRepository = customerPriceRepository;
        this.stocktakeService = stocktakeService;
        this.userRepository = userRepository;
        this.damageReportService = damageReportService;
    }

    public PageResponse<ApprovalListItemResponse> search(
            String q, ApprovalStatus status, ApprovalType approvalType, int page, int pageSize) {
        return PageResponse.from(approvalTaskRepository
                .findAll(ApprovalSpecifications.search(q, status, approvalType), PageRequests.of(page, pageSize, DEFAULT_SORT))
                .map(task -> ApprovalListItemResponse.from(task, resolveRefLink(task))));
    }

    @Transactional
    public void decide(Long taskId, ApprovalDecisionRequest request, Long actorUserId) {
        if (request.decision() != ApprovalStatus.APPROVED && request.decision() != ApprovalStatus.REJECTED) {
            throw ApiException.badRequest("decision phải là approved hoặc rejected");
        }

        ApprovalTask task = approvalTaskRepository
                .findWithActorsById(taskId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy việc cần duyệt"));
        if (task.getStatus() != ApprovalStatus.PENDING) {
            throw ApiException.conflict("Việc này đã được xử lý");
        }

        AppUser actor = userRepository
                .findById(actorUserId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        boolean approved = request.decision() == ApprovalStatus.APPROVED;
        if (CustomerPriceService.REF_TYPE_CUSTOMER_PRICE.equals(task.getRefType())) {
            applyCustomerPrice(task.getRefId(), approved, actor);
        } else if (StocktakeService.REF_TYPE.equals(task.getRefType())) {
            stocktakeService.applyApprovalDecision(task.getRefId(), approved, actor.getId());
        } else if (DamageReportService.REF_TYPE.equals(task.getRefType())) {
            damageReportService.applyDecision(task.getRefId(), approved, actor, request.note());
        } else {
            throw ApiException.badRequest(
                    "Loại duyệt \"" + task.getRefType() + "\" chưa được hỗ trợ trên API");
        }

        task.setStatus(request.decision());
        task.setDecidedBy(actor);
        task.setDecidedAt(Instant.now());
        task.setNote(Strings.trimToNull(request.note()));
        approvalTaskRepository.save(task);
    }

    private void applyCustomerPrice(Long priceId, boolean approved, AppUser actor) {
        CustomerPrice price = customerPriceRepository
                .findById(priceId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đề xuất giá"));
        if (price.getStatus() != ApprovalStatus.PENDING) {
            throw ApiException.conflict("Đề xuất giá không còn ở trạng thái chờ duyệt");
        }

        if (approved) {
            if (price.getPendingPrice() != null) {
                price.setUnitPrice(price.getPendingPrice());
            }
            price.setApprovedBy(actor);
            price.setApprovedAt(Instant.now());
            price.setStatus(ApprovalStatus.APPROVED);
        } else if (price.getApprovedAt() != null) {
            // Đã từng có giá duyệt — từ chối đề xuất mới, giữ giá đang áp dụng.
            price.setStatus(ApprovalStatus.APPROVED);
        } else {
            price.setStatus(ApprovalStatus.REJECTED);
        }
        price.setPendingPrice(null);
        customerPriceRepository.save(price);
    }

    private String resolveRefLink(ApprovalTask task) {
        if (CustomerPriceService.REF_TYPE_CUSTOMER_PRICE.equals(task.getRefType())) {
            return customerPriceRepository
                    .findWithCustomerById(task.getRefId())
                    .map(price -> "/customers/" + price.getCustomer().getId())
                    .orElse(null);
        }
        if (StocktakeService.REF_TYPE.equals(task.getRefType())) {
            return "/stocktakes/" + task.getRefId();
        }
        if (DamageReportService.REF_TYPE.equals(task.getRefType())) {
            return "/damage";
        }
        return null;
    }
}
