package com.hbsisms.feature.storefront;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.sales.Quote;
import com.hbsisms.feature.sales.QuoteRepository;
import com.hbsisms.feature.sales.QuoteStatus;
import com.hbsisms.feature.sales.RequestStatus;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesOrderService;
import com.hbsisms.feature.sales.SalesRequest;
import com.hbsisms.feature.sales.SalesRequestRepository;
import com.hbsisms.feature.sales.SalesRequestService;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.SalesRequestInputRequest;
import com.hbsisms.feature.sales.dto.SalesRequestResponse;
import com.hbsisms.feature.storefront.dto.MeSalesRequestRejectRequest;
import com.hbsisms.feature.storefront.dto.MeSalesRequestResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inter 2 — B2C {@code /me/sales-requests} (yêu cầu mua vượt tồn + đồng ý/từ chối báo giá). */
@Service
public class MeSalesRequestService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "receivedAt", "id");

    private final SalesRequestRepository salesRequestRepository;
    private final SalesRequestService salesRequestService;
    private final QuoteRepository quoteRepository;
    private final SalesOrderService salesOrderService;
    private final Clock clock;

    public MeSalesRequestService(
            SalesRequestRepository salesRequestRepository,
            SalesRequestService salesRequestService,
            QuoteRepository quoteRepository,
            SalesOrderService salesOrderService,
            Clock clock) {
        this.salesRequestRepository = salesRequestRepository;
        this.salesRequestService = salesRequestService;
        this.quoteRepository = quoteRepository;
        this.salesOrderService = salesOrderService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<MeSalesRequestResponse> listMine(Customer customer, int page, int pageSize) {
        return PageResponse.from(salesRequestRepository
                .findByCustomer_IdOrderByReceivedAtDescIdDesc(
                        customer.getId(), PageRequests.of(page, pageSize, LIST_SORT))
                .map(this::toListResponse));
    }

    @Transactional(readOnly = true)
    public MeSalesRequestResponse getMine(Customer customer, String code) {
        return toResponse(requireMine(customer, code));
    }

    @Transactional
    public MeSalesRequestResponse create(Customer customer, SalesRequestInputRequest request) {
        if (!customer.isActive()) {
            throw ApiException.conflict("Khách hàng đã bị khóa, không thể gửi yêu cầu");
        }
        validateQty(request.requestedQty(), request.availableQty());

        SalesRequestResponse created = salesRequestService.create(
                new SalesRequestInputRequest(
                        customer.getId(),
                        null,
                        request.contactPhone(),
                        null,
                        SaleChannel.WEB,
                        request.summary(),
                        request.neededDate(),
                        request.requestedQty(),
                        request.availableQty(),
                        request.lines()),
                null);
        return toResponse(requireMine(customer, created.code()));
    }

    /**
     * Khách đồng ý báo giá đã gửi → chốt báo giá + tạo đơn; yêu cầu → ORDERED.
     */
    @Transactional
    public MeSalesRequestResponse accept(Customer customer, String code, Long actorUserId) {
        SalesRequest row = requireMine(customer, code);
        if (row.getStatus() != RequestStatus.QUOTING) {
            throw ApiException.conflict("Chỉ đồng ý được khi cửa hàng đang báo giá");
        }
        Quote quote = requireActiveSentQuote(row);
        quote.setStatus(QuoteStatus.APPROVED);

        OrderDetailResponse order = salesOrderService.createFromQuote(quote.getId(), actorUserId);
        row.setStatus(RequestStatus.ORDERED);
        row.setOrderCode(order.code());
        return toResponse(row);
    }

    /**
     * Khách từ chối báo giá — bắt buộc lý do; cửa hàng xem trên màn yêu cầu nội bộ.
     */
    @Transactional
    public MeSalesRequestResponse reject(
            Customer customer, String code, MeSalesRequestRejectRequest request) {
        SalesRequest row = requireMine(customer, code);
        if (row.getStatus() != RequestStatus.QUOTING && row.getStatus() != RequestStatus.NEW) {
            throw ApiException.conflict("Yêu cầu đã kết thúc, không thể từ chối");
        }
        String reason = Strings.trimToNull(request == null ? null : request.rejectReason());
        if (reason == null) {
            throw ApiException.badRequest("Vui lòng nhập lý do từ chối");
        }

        Quote quote = latestQuote(row.getId());
        if (quote != null
                && (quote.getStatus() == QuoteStatus.SENT || quote.getStatus() == QuoteStatus.DRAFT)) {
            quote.setStatus(QuoteStatus.REJECTED);
        }

        row.setStatus(RequestStatus.REJECTED);
        row.setRejectReason(reason);
        return toResponse(row);
    }

    private MeSalesRequestResponse toListResponse(SalesRequest row) {
        Quote quote = latestQuote(row.getId());
        if (quote != null) {
            quote = quoteRepository.findWithDetailsById(quote.getId()).orElse(quote);
            expireIfNeeded(quote);
        }
        return MeSalesRequestResponse.from(row, quote, List.of());
    }

    private MeSalesRequestResponse toResponse(SalesRequest row) {
        Quote quote = latestQuote(row.getId());
        if (quote != null) {
            quote = quoteRepository.findWithDetailsById(quote.getId()).orElse(quote);
            expireIfNeeded(quote);
        }
        return MeSalesRequestResponse.from(row, quote);
    }

    private SalesRequest requireMine(Customer customer, String code) {
        String key = Strings.trimToNull(code);
        if (key == null) {
            throw ApiException.badRequest("Thiếu mã yêu cầu");
        }
        return salesRequestRepository
                .findByCodeAndCustomer_Id(key, customer.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu"));
    }

    private Quote requireActiveSentQuote(SalesRequest row) {
        Quote quote = latestQuote(row.getId());
        if (quote == null) {
            throw ApiException.conflict("Cửa hàng chưa gửi báo giá cho yêu cầu này");
        }
        quote = quoteRepository
                .findWithDetailsById(quote.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy báo giá"));
        expireIfNeeded(quote);
        if (quote.getStatus() != QuoteStatus.SENT) {
            throw ApiException.conflict("Báo giá chưa sẵn sàng để đồng ý (cần trạng thái đã gửi)");
        }
        return quote;
    }

    private Quote latestQuote(Long requestId) {
        return quoteRepository.findFirstByRequest_IdOrderByIdDesc(requestId).orElse(null);
    }

    private void expireIfNeeded(Quote quote) {
        if (quote.getStatus() != QuoteStatus.SENT) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(VN));
        if (quote.getValidUntil() != null && quote.getValidUntil().isBefore(today)) {
            quote.setStatus(QuoteStatus.EXPIRED);
        }
    }

    private static void validateQty(Integer requestedQty, Integer availableQty) {
        if (requestedQty == null && availableQty == null) {
            return;
        }
        if (requestedQty == null) {
            throw ApiException.badRequest("Vui lòng nhập số lượng yêu cầu");
        }
        if (availableQty != null && requestedQty <= availableQty) {
            throw ApiException.badRequest("Số lượng yêu cầu phải lớn hơn số còn hàng (vượt tồn)");
        }
    }
}
