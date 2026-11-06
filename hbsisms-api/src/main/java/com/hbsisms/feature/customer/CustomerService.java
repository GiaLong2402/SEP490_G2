package com.hbsisms.feature.customer;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
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
import com.hbsisms.feature.customer.dto.CustomerRequest;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer list/search + create/update (Duy). Address/price write APIs remain with Khôi. */
@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.ASC, "name");
    private static final Pattern VN_PHONE = Pattern.compile("^0\\d{9}$");
    private static final Pattern TAX_CODE = Pattern.compile("^\\d{10,13}$");

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
        Customer customer = requireCustomer(id);
        return toDetail(customer);
    }

    @Transactional
    public CustomerDetailResponse create(CustomerRequest request) {
        CustomerType type = requireType(request);
        NormalizedProfile profile = normalizeAndValidate(request, type);

        Customer customer = new Customer();
        customer.setCode(nextCode(type));
        customer.setCurrentDebt(BigDecimal.ZERO);
        customer.setAccountUser(null);
        applyProfile(customer, type, profile);
        Customer saved = customerRepository.save(customer);
        return toDetail(saved);
    }

    @Transactional
    public CustomerDetailResponse update(Long id, CustomerRequest request) {
        Customer customer = requireCustomer(id);
        CustomerType type = requireType(request);

        if (customer.getCustomerType() == CustomerType.B2B
                && type == CustomerType.B2C
                && customer.getCurrentDebt().compareTo(BigDecimal.ZERO) > 0) {
            throw ApiException.conflict("Khách đang có công nợ, không thể chuyển sang B2C");
        }

        NormalizedProfile profile = normalizeAndValidate(request, type);
        applyProfile(customer, type, profile);
        Customer saved = customerRepository.save(customer);
        return toDetail(saved);
    }

    /**
     * Khóa ({@code active=false}) hoặc mở khóa khách. Idempotent.
     * <p>Contract bán hàng: khách đã khóa không được tạo đơn mới / báo giá — sales phải kiểm tra
     * {@code isActive} trước khi tạo {@code SalesOrder} hoặc quote.
     */
    @Transactional
    public CustomerDetailResponse setActive(Long id, boolean active) {
        Customer customer = requireCustomer(id);
        customer.setActive(active);
        Customer saved = customerRepository.save(customer);
        return toDetail(saved);
    }

    private CustomerDetailResponse toDetail(Customer customer) {
        Long id = customer.getId();
        List<AddressResponse> addresses = addressRepository
                .findByCustomerIdOrderByDefaultAddressDescIdAsc(id)
                .stream()
                .map(AddressResponse::from)
                .toList();
        List<CustomerPrice> priceRows = customerPriceRepository.findByCustomerIdOrderByIdAsc(id);
        Map<Long, LatestDecision> decisions = latestDecisions(priceRows);
        List<CustomerPriceRowResponse> prices = priceRows.stream()
                .map(price -> {
                    LatestDecision decision = decisionFor(price, decisions);
                    return CustomerPriceRowResponse.from(
                            price,
                            decision == null ? null : decision.note(),
                            decision == null ? null : decision.status());
                })
                .toList();
        return CustomerDetailResponse.from(customer, addresses, prices);
    }

    private Customer requireCustomer(Long id) {
        return customerRepository
                .findWithAccountById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
    }

    private String nextCode(CustomerType type) {
        String prefix = CustomerCodeGenerator.prefixFor(type);
        List<String> codes = customerRepository.findCodesByPrefix(prefix);
        return CustomerCodeGenerator.nextCode(type, codes);
    }

    private static CustomerType requireType(CustomerRequest request) {
        if (request.customerType() == null) {
            throw ApiException.badRequest("Loại khách hàng là bắt buộc");
        }
        return request.customerType();
    }

    private static NormalizedProfile normalizeAndValidate(CustomerRequest request, CustomerType type) {
        String name = Strings.trimToNull(request.name());
        if (name == null) {
            throw ApiException.badRequest("Vui lòng nhập tên khách");
        }

        String email = Strings.trimToNull(request.email());
        String phone = normalizeOptionalPhone(request.phone(), "Số điện thoại");
        String contactPhone = normalizeOptionalPhone(request.contactPhone(), "SĐT liên hệ");
        String contactName = Strings.trimToNull(request.contactName());
        String note = Strings.trimToNull(request.note());
        String taxCode = Strings.trimToNull(request.taxCode());

        BigDecimal creditLimit = request.creditLimit() == null ? BigDecimal.ZERO : request.creditLimit();
        short netDays = request.netDays() == null ? 0 : request.netDays();

        if (type == CustomerType.B2C) {
            if (taxCode != null || creditLimit.compareTo(BigDecimal.ZERO) != 0 || netDays != 0) {
                throw ApiException.badRequest("Khách B2C không được có MST / hạn mức / kỳ hạn nợ");
            }
            taxCode = null;
            creditLimit = BigDecimal.ZERO;
            netDays = 0;
        } else {
            if (creditLimit.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest("Hạn mức không được âm");
            }
            if (netDays < 0) {
                throw ApiException.badRequest("Số ngày nợ không hợp lệ");
            }
            if (taxCode != null && !TAX_CODE.matcher(taxCode).matches()) {
                throw ApiException.badRequest("Mã số thuế không hợp lệ (10–13 chữ số)");
            }
        }

        boolean active = Boolean.TRUE.equals(request.isActive());
        return new NormalizedProfile(
                name, email, phone, taxCode, contactName, contactPhone, creditLimit, netDays, active, note);
    }

    private static String normalizeOptionalPhone(String raw, String label) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("[\\s.-]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        if (!VN_PHONE.matcher(digits).matches()) {
            throw ApiException.badRequest(label + " không hợp lệ");
        }
        return digits;
    }

    private static void applyProfile(Customer customer, CustomerType type, NormalizedProfile profile) {
        customer.setCustomerType(type);
        customer.setName(profile.name());
        customer.setEmail(profile.email());
        customer.setPhone(profile.phone());
        customer.setTaxCode(profile.taxCode());
        customer.setContactName(profile.contactName());
        customer.setContactPhone(profile.contactPhone());
        customer.setCreditLimit(profile.creditLimit());
        customer.setNetDays(profile.netDays());
        customer.setActive(profile.active());
        customer.setNote(profile.note());
    }

    /**
     * Chờ duyệt: ẩn quyết định cũ. Đã duyệt/từ chối: gắn lần quyết định gần nhất (status + note)
     * để FE phân biệt “duyệt giá đang dùng” vs “từ chối đề xuất mới trên dòng đã duyệt”.
     */
    private static LatestDecision decisionFor(CustomerPrice price, Map<Long, LatestDecision> decisions) {
        if (price.getStatus() == ApprovalStatus.PENDING) {
            return null;
        }
        return decisions.get(price.getId());
    }

    private Map<Long, LatestDecision> latestDecisions(List<CustomerPrice> priceRows) {
        if (priceRows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = priceRows.stream().map(CustomerPrice::getId).toList();
        Map<Long, LatestDecision> decisions = new HashMap<>();
        for (ApprovalTask task : approvalTaskRepository.findDecidedByRefTypeAndRefIdIn(
                CustomerPriceService.REF_TYPE_CUSTOMER_PRICE,
                ids,
                EnumSet.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED))) {
            if (!decisions.containsKey(task.getRefId())) {
                decisions.put(task.getRefId(), new LatestDecision(task.getStatus(), task.getNote()));
            }
        }
        return decisions;
    }

    private record LatestDecision(ApprovalStatus status, String note) {}

    private record NormalizedProfile(
            String name,
            String email,
            String phone,
            String taxCode,
            String contactName,
            String contactPhone,
            BigDecimal creditLimit,
            short netDays,
            boolean active,
            String note) {
    }
}
