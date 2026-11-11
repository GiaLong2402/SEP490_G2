package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.pricing.PricingService;
import com.hbsisms.feature.customer.pricing.dto.PricedLineResponse;
import com.hbsisms.feature.customer.pricing.dto.PricingRequest;
import com.hbsisms.feature.customer.pricing.dto.PricingResultResponse;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.QuoteConvertResponse;
import com.hbsisms.feature.sales.dto.QuoteDetailResponse;
import com.hbsisms.feature.sales.dto.QuoteInputRequest;
import com.hbsisms.feature.sales.dto.QuoteListItemResponse;
import com.hbsisms.feature.sales.dto.QuoteStatusRequest;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Báo giá: list / detail / create / status / convert → đơn.
 */
@Service
public class QuoteService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final Map<QuoteStatus, List<QuoteStatus>> ALLOWED = new EnumMap<>(QuoteStatus.class);

    static {
        ALLOWED.put(QuoteStatus.DRAFT, List.of(QuoteStatus.SENT));
        ALLOWED.put(QuoteStatus.SENT, List.of(QuoteStatus.APPROVED, QuoteStatus.REJECTED));
    }

    private final QuoteRepository quoteRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesRequestRepository salesRequestRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final SalesOrderService salesOrderService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public QuoteService(
            QuoteRepository quoteRepository,
            SalesOrderRepository salesOrderRepository,
            SalesRequestRepository salesRequestRepository,
            CustomerRepository customerRepository,
            ProductVariantRepository productVariantRepository,
            UserRepository userRepository,
            PricingService pricingService,
            SalesOrderService salesOrderService,
            AuditLogService auditLogService,
            Clock clock) {
        this.quoteRepository = quoteRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesRequestRepository = salesRequestRepository;
        this.customerRepository = customerRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
        this.salesOrderService = salesOrderService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Writable: SENT quá hạn được ghi EXPIRED khi đọc (cùng hành vi mock FE). */
    @Transactional
    public PageResponse<QuoteListItemResponse> search(String q, String status, int page, int pageSize) {
        return PageResponse.from(quoteRepository
                .findAll(QuoteSpecifications.search(q, parseStatus(status)), PageRequests.of(page, pageSize, LIST_SORT))
                .map(quote -> {
                    expireIfNeeded(quote);
                    return QuoteListItemResponse.from(quote, convertedOrder(quote.getId()));
                }));
    }

    /** Writable: SENT quá hạn được ghi EXPIRED khi đọc (cùng hành vi mock FE). */
    @Transactional
    public QuoteDetailResponse get(Long id) {
        Quote quote = quoteRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy báo giá"));
        expireIfNeeded(quote);
        return QuoteDetailResponse.from(quote, convertedOrder(quote.getId()));
    }

    @Transactional
    public QuoteDetailResponse create(QuoteInputRequest request, Long actorId) {
        Customer customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> ApiException.badRequest("Vui lòng chọn khách hàng"));
        if (!customer.isActive()) {
            throw ApiException.conflict("Khách hàng đã bị khóa, không thể lập báo giá");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw ApiException.badRequest("Báo giá phải có ít nhất một dòng hàng");
        }

        SalesRequest linkedRequest = null;
        if (request.requestId() != null) {
            linkedRequest = salesRequestRepository
                    .findById(request.requestId())
                    .orElseThrow(() -> ApiException.badRequest("Không tìm thấy yêu cầu"));
            if (linkedRequest.getCustomer() != null
                    && !linkedRequest.getCustomer().getId().equals(customer.getId())) {
                throw ApiException.badRequest("Khách hàng không khớp với yêu cầu");
            }
        }

        AppUser creator = actorId == null
                ? null
                : userRepository
                        .findById(actorId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        PricingResultResponse priced = pricingService.resolve(new PricingRequest(
                customer.getId(),
                null,
                ZERO,
                ZERO,
                request.lines()));

        Quote quote = new Quote();
        quote.setCode(nextQuoteCode());
        quote.setCustomer(customer);
        quote.setRequest(linkedRequest);
        quote.setStatus(QuoteStatus.DRAFT);
        quote.setSubtotal(priced.subtotal());
        quote.setTotal(priced.total());
        quote.setValidUntil(request.validUntil());
        quote.setCreatedBy(creator);
        quote.setNote(Strings.trimToNull(request.note()));

        for (PricedLineResponse line : priced.lines()) {
            ProductVariant variant = productVariantRepository
                    .findById(line.variantId())
                    .orElseThrow(() -> ApiException.badRequest("Quy cách không tồn tại"));
            QuoteLine row = new QuoteLine();
            row.setVariant(variant);
            row.setUnit(line.unit());
            row.setQty(line.qty());
            row.setQtyKhay(line.qtyKhay());
            row.setUnitPrice(line.unitPrice());
            row.setLineTotal(line.lineTotal());
            row.setPriceBasis(line.priceBasis());
            quote.addLine(row);
        }

        quoteRepository.save(quote);

        if (linkedRequest != null && linkedRequest.getStatus() == RequestStatus.NEW) {
            linkedRequest.setStatus(RequestStatus.QUOTING);
        }

        Map<String, Object> audit = new HashMap<>();
        audit.put("total", quote.getTotal());
        audit.put("requestId", request.requestId());
        auditLogService.record(actorId, "create", "quote", quote.getId(), audit);

        return get(quote.getId());
    }

    @Transactional
    public void setStatus(Long id, QuoteStatusRequest request, Long actorId) {
        Quote quote = quoteRepository
                .findWithHeaderById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy báo giá"));
        expireIfNeeded(quote);

        QuoteStatus next = request.status();
        List<QuoteStatus> allowed = ALLOWED.getOrDefault(quote.getStatus(), List.of());
        if (!allowed.contains(next)) {
            throw ApiException.conflict("Không thể chuyển sang trạng thái này");
        }

        quote.setStatus(next);
        if (next == QuoteStatus.APPROVED && actorId != null) {
            userRepository.findById(actorId).ifPresent(quote::setApprovedBy);
        }

        Map<String, Object> audit = new HashMap<>();
        audit.put("status", next.code());
        auditLogService.record(actorId, "update", "quote", quote.getId(), audit);
    }

    @Transactional
    public QuoteConvertResponse convert(Long id, Long actorId) {
        OrderDetailResponse order = salesOrderService.createFromQuote(id, actorId);
        Map<String, Object> audit = new HashMap<>();
        audit.put("orderId", order.id());
        audit.put("orderCode", order.code());
        auditLogService.record(actorId, "convert", "quote", id, audit);
        return new QuoteConvertResponse(order.id(), order.code());
    }

    private SalesOrder convertedOrder(Long quoteId) {
        return salesOrderRepository.findFirstBySourceQuote_Id(quoteId).orElse(null);
    }

    private void expireIfNeeded(Quote quote) {
        if (quote.getStatus() != QuoteStatus.SENT) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        if (quote.getValidUntil() != null && quote.getValidUntil().isBefore(today)) {
            quote.setStatus(QuoteStatus.EXPIRED);
        }
    }

    private String nextQuoteCode() {
        String year = LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyy"));
        String prefix = "BG-" + year + "-";
        int next = quoteRepository.findCodesByPrefix(prefix).stream()
                .map(code -> code.substring(prefix.length()))
                .map(digits -> {
                    try {
                        return Integer.parseInt(digits);
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .max(Integer::compareTo)
                .orElse(0)
                + 1;
        return prefix + String.format(Locale.ROOT, "%04d", next);
    }

    private static QuoteStatus parseStatus(String raw) {
        try {
            return QuoteStatus.fromCode(Strings.trimToNull(raw));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }
}
