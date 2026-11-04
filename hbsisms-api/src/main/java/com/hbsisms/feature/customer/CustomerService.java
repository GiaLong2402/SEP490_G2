package com.hbsisms.feature.customer;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.catalog.CustomerPrice;
import com.hbsisms.feature.catalog.CustomerPriceRepository;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.feature.customer.dto.CustomerDetailResponse;
import com.hbsisms.feature.customer.dto.CustomerListItemResponse;
import com.hbsisms.feature.customer.dto.CustomerPriceRowResponse;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer list/search + detail read (Duy). Address/price write APIs remain with Khôi. */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final ApprovalTaskRepository approvalTaskRepository;

    public CustomerService(
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            CustomerPriceRepository customerPriceRepository,
            ApprovalTaskRepository approvalTaskRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.customerPriceRepository = customerPriceRepository;
        this.approvalTaskRepository = approvalTaskRepository;
    }

    public PageResponse<CustomerListItemResponse> search(
            String q, CustomerType type, Boolean isActive, int page, int pageSize) {
        return PageResponse.from(customerRepository
                .findAll(
                        CustomerSpecifications.search(q, type, isActive),
                        PageRequests.of(page, pageSize, LIST_SORT))
                .map(CustomerListItemResponse::from));
    }

    public CustomerDetailResponse getDetail(Long id) {
        Customer customer = customerRepository
                .findWithAccountById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        List<AddressResponse> addresses = addressRepository
                .findByCustomerIdOrderByDefaultAddressDescIdAsc(id)
                .stream()
                .map(AddressResponse::from)
                .toList();
        List<CustomerPrice> priceRows = customerPriceRepository.findByCustomerIdOrderByIdAsc(id);
        Map<Long, String> decisionNotes = latestDecisionNotes(priceRows);
        List<CustomerPriceRowResponse> prices = priceRows.stream()
                .map(price -> CustomerPriceRowResponse.from(price, decisionNoteFor(price, decisionNotes)))
                .toList();
        return CustomerDetailResponse.from(customer, addresses, prices);
    }

    /**
     * Chờ duyệt: ẩn note cũ. Đã duyệt/từ chối: hiện ghi chú lần quyết định gần nhất (nếu có).
     * Duyệt có thể không có note; từ chối thường có lý do.
     */
    private static String decisionNoteFor(CustomerPrice price, Map<Long, String> decisionNotes) {
        if (price.getStatus() == ApprovalStatus.PENDING) {
            return null;
        }
        return decisionNotes.get(price.getId());
    }

    /** Ghi chú quyết định gần nhất theo từng customer_price (đã sort decidedAt desc từ repo). */
    private Map<Long, String> latestDecisionNotes(List<CustomerPrice> priceRows) {
        if (priceRows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = priceRows.stream().map(CustomerPrice::getId).toList();
        Map<Long, String> notes = new HashMap<>();
        for (ApprovalTask task : approvalTaskRepository.findDecidedByRefTypeAndRefIdIn(
                CustomerPriceService.REF_TYPE_CUSTOMER_PRICE,
                ids,
                EnumSet.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED))) {
            // Không dùng putIfAbsent: note=null vẫn bị coi là "chưa có" và bị note lần trước ghi đè.
            if (!notes.containsKey(task.getRefId())) {
                notes.put(task.getRefId(), task.getNote());
            }
        }
        return notes;
    }
}
