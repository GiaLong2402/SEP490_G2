package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.dto.ReceiveStockRequest;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Thin goods-in until full GoodsReceipt feature lands. */
@Service
public class InventoryReceiveService {

    private final ProductVariantRepository productVariantRepository;
    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;
    private final StockService stockService;

    public InventoryReceiveService(
            ProductVariantRepository productVariantRepository,
            LocationRepository locationRepository,
            LotRepository lotRepository,
            StockService stockService) {
        this.productVariantRepository = productVariantRepository;
        this.locationRepository = locationRepository;
        this.lotRepository = lotRepository;
        this.stockService = stockService;
    }

    @Transactional
    public Stock receive(ReceiveStockRequest request, Long actorId) {
        ProductVariant variant = productVariantRepository
                .findById(request.variantId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách sản phẩm"));
        locationRepository
                .findById(request.locationId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));

        String lotCode = Strings.trimToNull(request.lotCode());
        Lot lot = lotRepository
                .findByLotCodeIgnoreCase(lotCode)
                .orElseGet(() -> createLot(variant, lotCode, request));

        if (!lot.getVariant().getId().equals(variant.getId())) {
            throw ApiException.conflict("Mã lô " + lotCode + " đã thuộc quy cách khác");
        }

        return stockService.receiveIn(StockMutation.of(
                variant.getId(),
                lot.getId(),
                request.locationId(),
                request.qty(),
                "goods_receipt",
                null,
                request.note(),
                actorId));
    }

    private Lot createLot(ProductVariant variant, String lotCode, ReceiveStockRequest request) {
        Lot lot = new Lot();
        lot.setLotCode(lotCode);
        lot.setVariant(variant);
        lot.setMfgDate(request.mfgDate());
        lot.setExpDate(request.expDate());
        lot.setReceivedAt(Instant.now());
        lot.setNote(Strings.trimToNull(request.note()));
        return lotRepository.saveAndFlush(lot);
    }
}
