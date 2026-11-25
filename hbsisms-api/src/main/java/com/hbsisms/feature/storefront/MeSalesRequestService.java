package com.hbsisms.feature.storefront;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesRequest;
import com.hbsisms.feature.sales.SalesRequestRepository;
import com.hbsisms.feature.sales.SalesRequestService;
import com.hbsisms.feature.sales.dto.SalesRequestInputRequest;
import com.hbsisms.feature.sales.dto.SalesRequestResponse;
import com.hbsisms.feature.storefront.dto.MeSalesRequestResponse;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inter 2 — B2C {@code /me/sales-requests} (yêu cầu mua vượt tồn). */
@Service
public class MeSalesRequestService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "receivedAt", "id");

    private final SalesRequestRepository salesRequestRepository;
    private final SalesRequestService salesRequestService;

    public MeSalesRequestService(
            SalesRequestRepository salesRequestRepository, SalesRequestService salesRequestService) {
        this.salesRequestRepository = salesRequestRepository;
        this.salesRequestService = salesRequestService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MeSalesRequestResponse> listMine(Customer customer, int page, int pageSize) {
        return PageResponse.from(salesRequestRepository
                .findByCustomer_IdOrderByReceivedAtDescIdDesc(
                        customer.getId(), PageRequests.of(page, pageSize, LIST_SORT))
                .map(row -> MeSalesRequestResponse.from(row, List.of())));
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
        return MeSalesRequestResponse.from(created);
    }

    /**
     * Portal: cho phép chỉ {@code requestedQty} (đặt số lượng lớn / tư vấn).
     * Khi gửi cả hai (checkout thiếu hàng) vẫn yêu cầu requested &gt; available.
     */
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
