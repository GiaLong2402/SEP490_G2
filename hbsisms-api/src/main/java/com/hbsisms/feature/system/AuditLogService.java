package com.hbsisms.feature.system;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.GoodsReceiptRepository;
import com.hbsisms.feature.inventory.Stocktake;
import com.hbsisms.feature.inventory.StocktakeRepository;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.production.ProductionOrder;
import com.hbsisms.feature.production.ProductionOrderRepository;
import com.hbsisms.feature.sales.Delivery;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.Quote;
import com.hbsisms.feature.sales.QuoteRepository;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesReturn;
import com.hbsisms.feature.sales.SalesReturnRepository;
import com.hbsisms.feature.system.dto.AuditLogResponse;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Append-only writer + list for màn Nhật ký admin. */
@Service
public class AuditLogService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final QuoteRepository quoteRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final StocktakeRepository stocktakeRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final SalesReturnRepository salesReturnRepository;

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            UserRepository userRepository,
            GoodsReceiptRepository goodsReceiptRepository,
            SalesOrderRepository salesOrderRepository,
            QuoteRepository quoteRepository,
            ProductionOrderRepository productionOrderRepository,
            StocktakeRepository stocktakeRepository,
            PaymentRepository paymentRepository,
            DeliveryRepository deliveryRepository,
            SalesReturnRepository salesReturnRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.quoteRepository = quoteRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.stocktakeRepository = stocktakeRepository;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.salesReturnRepository = salesReturnRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(
            String q, String action, String entityType, String from, String to, int page, int pageSize) {
        Page<AuditLog> result = auditLogRepository.findAll(
                AuditLogSpecifications.search(q, action, entityType, parseDate(from), parseDate(to)),
                PageRequests.of(page, pageSize, DEFAULT_SORT));
        Map<String, Map<Long, String>> codes = resolveEntityCodes(result.getContent());
        return PageResponse.from(result.map(row -> {
            String code = null;
            if (row.getEntityType() != null && row.getEntityId() != null) {
                Map<Long, String> byId = codes.get(row.getEntityType());
                code = byId == null ? null : byId.get(row.getEntityId());
            }
            if (code == null && row.getDetail() != null) {
                Object detailCode = row.getDetail().get("code");
                if (detailCode == null) {
                    detailCode = row.getDetail().get("orderCode");
                }
                if (detailCode != null && !String.valueOf(detailCode).isBlank()) {
                    code = String.valueOf(detailCode).trim();
                }
            }
            return AuditLogResponse.from(row, code);
        }));
    }

    /** Gom mã nghiệp vụ theo loại đối tượng trên trang hiện tại (tránh N+1). */
    private Map<String, Map<Long, String>> resolveEntityCodes(List<AuditLog> rows) {
        Map<String, Set<Long>> idsByType = new HashMap<>();
        for (AuditLog row : rows) {
            if (row.getEntityType() == null || row.getEntityId() == null) {
                continue;
            }
            idsByType.computeIfAbsent(row.getEntityType(), ignored -> new HashSet<>()).add(row.getEntityId());
        }

        Map<String, Map<Long, String>> out = new HashMap<>();
        putCodes(out, "goods_receipt", idsByType, goodsReceiptRepository::findAllById, GoodsReceipt::getId, GoodsReceipt::getCode);
        putCodes(out, "sales_order", idsByType, salesOrderRepository::findAllById, SalesOrder::getId, SalesOrder::getCode);
        putCodes(out, "quote", idsByType, quoteRepository::findAllById, Quote::getId, Quote::getCode);
        putCodes(out, "production_order", idsByType, productionOrderRepository::findAllById, ProductionOrder::getId, ProductionOrder::getCode);
        putCodes(out, "stocktake", idsByType, stocktakeRepository::findAllById, Stocktake::getId, Stocktake::getCode);
        putCodes(out, "payment", idsByType, paymentRepository::findAllById, Payment::getId, Payment::getCode);
        putCodes(out, "delivery", idsByType, deliveryRepository::findAllById, Delivery::getId, Delivery::getCode);
        putCodes(out, "sales_return", idsByType, salesReturnRepository::findAllById, SalesReturn::getId, SalesReturn::getCode);
        return out;
    }

    private static <T> void putCodes(
            Map<String, Map<Long, String>> out,
            String entityType,
            Map<String, Set<Long>> idsByType,
            Function<Iterable<Long>, List<T>> loader,
            Function<T, Long> idFn,
            Function<T, String> codeFn) {
        Set<Long> ids = idsByType.get(entityType);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Map<Long, String> map = loader.apply(ids).stream()
                .filter(row -> codeFn.apply(row) != null && !codeFn.apply(row).isBlank())
                .collect(Collectors.toMap(idFn, codeFn, (a, b) -> a));
        if (!map.isEmpty()) {
            out.put(entityType, map);
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

    @Transactional
    public void record(Long userId, String action, String entityType, Long entityId, Map<String, Object> detail) {
        AuditLog log = new AuditLog();
        if (userId != null) {
            AppUser user = userRepository.findById(userId).orElse(null);
            log.setUser(user);
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetail(detail == null ? null : new HashMap<>(detail));
        auditLogRepository.save(log);
    }
}
