package com.hbsisms.feature.storefront;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.sales.RequestStatus;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesRequest;
import com.hbsisms.feature.sales.SalesRequestRepository;
import com.hbsisms.feature.sales.SalesRequestService;
import com.hbsisms.feature.sales.dto.SalesRequestInputRequest;
import com.hbsisms.feature.storefront.dto.MeSalesRequestResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inter 2 — B2C {@code /me/sales-requests} (yêu cầu mua vượt tồn). */
@Service
public class MeSalesRequestService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "receivedAt", "id");

    private final SalesRequestRepository salesRequestRepository;
    private final Clock clock;

    public MeSalesRequestService(SalesRequestRepository salesRequestRepository, Clock clock) {
        this.salesRequestRepository = salesRequestRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<MeSalesRequestResponse> listMine(Customer customer, int page, int pageSize) {
        return PageResponse.from(salesRequestRepository
                .findByCustomer_IdOrderByReceivedAtDescIdDesc(
                        customer.getId(), PageRequests.of(page, pageSize, LIST_SORT))
                .map(MeSalesRequestResponse::from));
    }

    @Transactional(readOnly = true)
    public MeSalesRequestResponse getMine(Customer customer, String code) {
        String key = Strings.trimToNull(code);
        if (key == null) {
            throw ApiException.badRequest("Thiếu mã yêu cầu");
        }
        SalesRequest row = salesRequestRepository
                .findByCodeAndCustomer_Id(key, customer.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu"));
        return MeSalesRequestResponse.from(row);
    }

    @Transactional
    public MeSalesRequestResponse create(Customer customer, SalesRequestInputRequest request) {
        if (!customer.isActive()) {
            throw ApiException.conflict("Khách hàng đã bị khóa, không thể gửi yêu cầu");
        }
        String summary = Strings.trimToNull(request.summary());
        if (summary == null) {
            throw ApiException.badRequest("Vui lòng nhập nội dung yêu cầu");
        }
        validateQty(request.requestedQty(), request.availableQty());

        SalesRequest row = new SalesRequest();
        row.setCode(nextCode());
        row.setCustomer(customer);
        row.setCustomerName(customer.getName());
        row.setChannel(SaleChannel.WEB);
        row.setSummary(summary);
        row.setStatus(RequestStatus.NEW);
        row.setReceivedAt(Instant.now(clock));
        row.setHandledBy(null);
        SalesRequestService.applyCustomerQtyFields(row, request);
        salesRequestRepository.save(row);
        return MeSalesRequestResponse.from(row);
    }

    private static void validateQty(Integer requestedQty, Integer availableQty) {
        if (requestedQty == null && availableQty == null) {
            return;
        }
        if (requestedQty == null || availableQty == null) {
            throw ApiException.badRequest("Cần gửi đủ số lượng yêu cầu và số lượng còn hàng");
        }
        if (requestedQty <= availableQty) {
            throw ApiException.badRequest("Số lượng yêu cầu phải lớn hơn số còn hàng (vượt tồn)");
        }
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
}
