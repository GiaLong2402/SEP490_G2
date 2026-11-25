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
import com.hbsisms.feature.sales.dto.FeasibilityResultResponse;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.SalesRequestCloseRequest;
import com.hbsisms.feature.sales.dto.SalesRequestFeasibilityRequest;
import com.hbsisms.feature.sales.dto.SalesRequestInputRequest;
import com.hbsisms.feature.sales.dto.SalesRequestLineInput;
import com.hbsisms.feature.sales.dto.SalesRequestRejectRequest;
import com.hbsisms.feature.sales.dto.SalesRequestResponse;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesRequestService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "receivedAt", "id");

    private final SalesRequestRepository salesRequestRepository;
    private final CustomerRepository customerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final SalesOrderService salesOrderService;
    private final BusinessCodeService businessCodeService;
    private final Clock clock;

    public SalesRequestService(
            SalesRequestRepository salesRequestRepository,
            CustomerRepository customerRepository,
            ProductVariantRepository productVariantRepository,
            UserRepository userRepository,
            SalesOrderService salesOrderService,
            BusinessCodeService businessCodeService,
            Clock clock) {
        this.salesRequestRepository = salesRequestRepository;
        this.customerRepository = customerRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
        this.salesOrderService = salesOrderService;
        this.businessCodeService = businessCodeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesRequestResponse> search(
            String q,
            String status,
            String channel,
            String neededFrom,
            String neededTo,
            Long customerId,
            int page,
            int pageSize) {
        return PageResponse.from(salesRequestRepository
                .findAll(
                        RequestSpecifications.search(
                                q,
                                parseStatus(status),
                                parseChannel(channel),
                                parseDate(neededFrom),
                                parseDate(neededTo),
                                customerId),
                        PageRequests.of(page, pageSize, LIST_SORT))
                .map(row -> SalesRequestResponse.from(row, List.of())));
    }

    @Transactional(readOnly = true)
    public SalesRequestResponse get(Long id) {
        return SalesRequestResponse.from(require(id));
    }

    @Transactional
    public SalesRequestResponse create(SalesRequestInputRequest request, Long actorId) {
        Customer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository
                    .findById(request.customerId())
                    .orElseThrow(() -> ApiException.badRequest("Không tìm thấy khách hàng"));
        }
        String walkInName = Strings.trimToNull(request.customerName());
        if (customer == null && walkInName == null) {
            walkInName = "Khách vãng lai";
        }

        List<SalesRequestLine> lines = buildLines(request.lines());
        String summary = Strings.trimToNull(request.summary());
        if (summary == null && lines.isEmpty()) {
            throw ApiException.badRequest("Vui lòng nhập nội dung yêu cầu hoặc thêm dòng hàng");
        }
        if (summary == null) {
            summary = autoSummary(lines);
        }

        AppUser handler = actorId == null
                ? null
                : userRepository.findById(actorId).orElse(null);

        SalesRequest row = new SalesRequest();
        row.setCode(nextCode());
        row.setCustomer(customer);
        row.setCustomerName(customer != null ? customer.getName() : walkInName);
        // Portal có thể gửi SĐT khác hồ sơ; staff chỉ gửi khi khách vãng lai.
        row.setContactPhone(Strings.trimToNull(request.contactPhone()));
        row.setContactAddress(Strings.trimToNull(request.contactAddress()));
        row.setChannel(request.channel());
        row.setSummary(summary);
        row.setStatus(RequestStatus.NEW);
        row.setReceivedAt(Instant.now(clock));
        row.setHandledBy(handler);
        applyCustomerQtyFields(row, request);
        if (!lines.isEmpty()) {
            int totalKhay = lines.stream().mapToInt(SalesRequestLine::getQtyKhay).sum();
            if (row.getRequestedQty() == null) {
                row.setRequestedQty(totalKhay);
            }
            row.replaceLines(lines);
        }
        salesRequestRepository.save(row);
        return SalesRequestResponse.from(require(row.getId()));
    }

    @Transactional
    public FeasibilityResultResponse recordFeasibility(
            Long id, SalesRequestFeasibilityRequest request, Long actorId) {
        SalesRequest row = require(id);
        assertOpen(row);

        if (Boolean.FALSE.equals(request.feasible())) {
            String reason = Strings.trimToNull(request.note());
            if (reason == null) {
                throw ApiException.badRequest("Vui lòng nhập lý do từ chối");
            }
            row.setStatus(RequestStatus.REJECTED);
            row.setRejectReason(reason);
            row.setFeasible(false);
            assignHandler(row, actorId);
            return new FeasibilityResultResponse(SalesRequestResponse.from(row), null);
        }

        row.setFeasible(true);
        row.setExpectedDate(request.expectedDate());
        if (Strings.trimToNull(request.note()) != null) {
            row.setNote(Strings.trimToNull(request.note()));
        }
        // Staff chọn SP + SL đáp ứng → ghi đè lines (prefill báo giá).
        if (request.lines() != null && !request.lines().isEmpty()) {
            List<SalesRequestLine> offer = buildOfferLines(request.lines());
            row.replaceLines(offer);
            int fulfillKhay = offer.stream().mapToInt(SalesRequestLine::getQtyKhay).sum();
            row.setFulfillableQty(fulfillKhay);
            row.setShortageQty(0);
        } else {
            row.setFulfillableQty(request.fulfillableQty());
            row.setShortageQty(request.shortageQty());
        }
        assignHandler(row, actorId);

        if (row.getStatus() == RequestStatus.NEW) {
            row.setStatus(RequestStatus.QUOTING);
        }

        OrderDetailResponse order = null;
        if (request.customerAccepted()) {
            if (request.order() == null) {
                throw ApiException.badRequest("Khách đồng ý — vui lòng gửi thông tin đơn hàng");
            }
            order = salesOrderService.createFromRequest(request.order(), actorId);
            row.setStatus(RequestStatus.ORDERED);
            row.setOrderCode(order.code());
        }

        return new FeasibilityResultResponse(SalesRequestResponse.from(row), order);
    }

    /** Shared by staff create and portal {@code /me/sales-requests}. */
    public static void applyCustomerQtyFields(SalesRequest row, SalesRequestInputRequest request) {
        row.setNeededDate(request.neededDate());
        row.setRequestedQty(request.requestedQty());
        row.setAvailableQty(request.availableQty());
    }

    @Transactional
    public SalesRequestResponse reject(Long id, SalesRequestRejectRequest request, Long actorId) {
        SalesRequest row = require(id);
        assertOpen(row);
        String reason = Strings.trimToNull(request.rejectReason());
        if (reason == null) {
            throw ApiException.badRequest("Vui lòng nhập lý do từ chối");
        }
        row.setStatus(RequestStatus.REJECTED);
        row.setRejectReason(reason);
        row.setFeasible(false);
        assignHandler(row, actorId);
        return SalesRequestResponse.from(row);
    }

    @Transactional
    public SalesRequestResponse patch(Long id, SalesRequestCloseRequest request, Long actorId) {
        SalesRequest row = require(id);
        RequestStatus next = parseStatus(request.status());
        if (next == RequestStatus.CLOSED) {
            if (row.getStatus() == RequestStatus.REJECTED || row.getStatus() == RequestStatus.ORDERED) {
                throw ApiException.conflict("Yêu cầu đã kết thúc, không thể đóng thêm");
            }
            row.setStatus(RequestStatus.CLOSED);
            if (Strings.trimToNull(request.note()) != null) {
                row.setNote(Strings.trimToNull(request.note()));
            }
            assignHandler(row, actorId);
            return SalesRequestResponse.from(row);
        }
        throw ApiException.badRequest("PATCH chỉ hỗ trợ đóng yêu cầu (status=closed)");
    }

    /** Dòng đáp ứng Sales chọn: qty = fulfillableQty, shortage = 0. */
    private List<SalesRequestLine> buildOfferLines(List<SalesRequestLineInput> inputs) {
        List<SalesRequestLine> lines = buildLines(inputs);
        for (SalesRequestLine line : lines) {
            line.setFulfillableQty(line.getQty());
            line.setShortageQty(0);
        }
        return lines;
    }

    private List<SalesRequestLine> buildLines(List<SalesRequestLineInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        List<SalesRequestLine> lines = new ArrayList<>();
        for (SalesRequestLineInput input : inputs) {
            ProductVariant variant = productVariantRepository
                    .findWithProductById(input.variantId())
                    .orElseThrow(() -> ApiException.badRequest("Không tìm thấy quy cách sản phẩm"));
            if (!variant.isActive()) {
                throw ApiException.conflict("Quy cách đã ngừng bán: " + variant.getSku());
            }
            SellUnit unit = input.unit() == null ? SellUnit.PACK : input.unit();
            int qtyKhay = toKhay(unit, input.qty(), variant.getPackPerCase());
            SalesRequestLine line = new SalesRequestLine();
            line.setVariant(variant);
            line.setUnit(unit);
            line.setQty(input.qty());
            line.setQtyKhay(qtyKhay);
            lines.add(line);
        }
        return lines;
    }

    private static String autoSummary(List<SalesRequestLine> lines) {
        return lines.stream()
                .map(line -> {
                    var v = line.getVariant();
                    String name = v.getProduct() == null ? v.getSku() : v.getProduct().getName();
                    String pack = v.getPackLabel() == null ? "" : " · " + v.getPackLabel();
                    String unit = line.getUnit() == SellUnit.CASE ? "thùng" : "khay";
                    return name + pack + " × " + line.getQty() + " " + unit;
                })
                .collect(Collectors.joining("; "));
    }

    private static int toKhay(SellUnit unit, int qty, short packPerCase) {
        return PricingService.toQtyKhay(unit, qty, packPerCase);
    }

    private void assertOpen(SalesRequest row) {
        if (row.getStatus() == RequestStatus.REJECTED
                || row.getStatus() == RequestStatus.CLOSED
                || row.getStatus() == RequestStatus.ORDERED) {
            throw ApiException.conflict("Yêu cầu đã kết thúc, không thể cập nhật");
        }
    }

    private void assignHandler(SalesRequest row, Long actorId) {
        if (actorId == null || row.getHandledBy() != null) {
            return;
        }
        userRepository.findById(actorId).ifPresent(row::setHandledBy);
    }

    private SalesRequest require(Long id) {
        return salesRequestRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu"));
    }

    private String nextCode() {
        return businessCodeService.nextSalesRequestCode();
    }

    private static RequestStatus parseStatus(String raw) {
        try {
            return RequestStatus.fromCode(Strings.trimToNull(raw));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static SaleChannel parseChannel(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return SaleChannel.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static LocalDate parseDate(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw ApiException.badRequest("Ngày không hợp lệ: " + raw);
        }
    }
}
