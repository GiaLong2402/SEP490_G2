package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.sales.dto.FeasibilityResultResponse;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.SalesRequestCloseRequest;
import com.hbsisms.feature.sales.dto.SalesRequestFeasibilityRequest;
import com.hbsisms.feature.sales.dto.SalesRequestInputRequest;
import com.hbsisms.feature.sales.dto.SalesRequestRejectRequest;
import com.hbsisms.feature.sales.dto.SalesRequestResponse;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesRequestService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "receivedAt", "id");

    private final SalesRequestRepository salesRequestRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final SalesOrderService salesOrderService;
    private final Clock clock;

    public SalesRequestService(
            SalesRequestRepository salesRequestRepository,
            CustomerRepository customerRepository,
            UserRepository userRepository,
            SalesOrderService salesOrderService,
            Clock clock) {
        this.salesRequestRepository = salesRequestRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.salesOrderService = salesOrderService;
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
                .map(SalesRequestResponse::from));
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

        AppUser handler = actorId == null
                ? null
                : userRepository.findById(actorId).orElse(null);

        SalesRequest row = new SalesRequest();
        row.setCode(nextCode());
        row.setCustomer(customer);
        row.setCustomerName(customer != null ? customer.getName() : walkInName);
        row.setChannel(request.channel());
        row.setSummary(Strings.trimToNull(request.summary()));
        row.setStatus(RequestStatus.NEW);
        row.setReceivedAt(Instant.now(clock));
        row.setHandledBy(handler);
        applyCustomerQtyFields(row, request);
        salesRequestRepository.save(row);
        return SalesRequestResponse.from(row);
    }

    @Transactional
    public FeasibilityResultResponse recordFeasibility(
            Long id, SalesRequestFeasibilityRequest request, Long actorId) {
        SalesRequest row = require(id);
        assertOpen(row);

        row.setFeasible(request.feasible());
        row.setFulfillableQty(request.fulfillableQty());
        row.setShortageQty(request.shortageQty());
        row.setExpectedDate(request.expectedDate());
        if (Strings.trimToNull(request.note()) != null) {
            row.setNote(Strings.trimToNull(request.note()));
        }
        assignHandler(row, actorId);

        if (Boolean.FALSE.equals(request.feasible())) {
            // Không khả thi nhưng chưa từ chối chính thức — chuyển quoting để Sales theo dõi.
            if (row.getStatus() == RequestStatus.NEW) {
                row.setStatus(RequestStatus.QUOTING);
            }
            return new FeasibilityResultResponse(SalesRequestResponse.from(row), null);
        }

        // Khả thi
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
        String year = LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyy"));
        String prefix = "YC-" + year + "-";
        int next = salesRequestRepository.findCodesByPrefix(prefix).stream()
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
