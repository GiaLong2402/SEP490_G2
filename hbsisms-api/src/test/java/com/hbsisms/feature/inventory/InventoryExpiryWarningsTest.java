package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.inventory.dto.ExpiryWarningsResponse;
import com.hbsisms.feature.system.AppConfigService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryExpiryWarningsTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockLedgerRepository stockLedgerRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private AppConfigService appConfigService;

    private InventoryQueryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryQueryService(
                stockRepository,
                stockLedgerRepository,
                locationRepository,
                warehouseRepository,
                appConfigService);
    }

    @Test
    void expiryWarnings_splitsNearAvailableAndExpiredQty() {
        when(appConfigService.expiryWarningDays()).thenReturn(5);
        when(warehouseRepository.count()).thenReturn(1L);

        LocalDate today = LocalDate.now();
        Stock near = stock(1L, "LOT-NEAR", 4, 0, today.plusDays(2));
        Stock far = stock(2L, "LOT-FAR", 8, 0, today.plusDays(20));
        Stock expired = stock(3L, "LOT-EXP", 0, 5, today.minusDays(3));
        when(stockRepository.findAllWithDetails()).thenReturn(List.of(near, far, expired));

        ExpiryWarningsResponse result = service.expiryWarnings();

        assertThat(result.expiryWarningDays()).isEqualTo(5);
        assertThat(result.nearExpiry()).extracting(r -> r.lotCode()).containsExactly("LOT-NEAR");
        assertThat(result.nearExpiry().get(0).locationCode()).isEqualTo("A1");
        assertThat(result.expired()).extracting(r -> r.lotCode()).containsExactly("LOT-EXP");
        assertThat(result.expired().get(0).expired()).isEqualTo(5);
    }

    private static Stock stock(long lotId, String code, int available, int expired, LocalDate exp) {
        Product product = new Product();
        product.setName("Cá");
        ProductVariant variant = new ProductVariant();
        setId(variant, lotId + 100);
        variant.setSku("SKU-" + lotId);
        variant.setPackLabel("1kg");
        variant.setProduct(product);

        Lot lot = new Lot();
        setId(lot, lotId);
        lot.setLotCode(code);
        lot.setExpDate(exp);

        Warehouse wh = new Warehouse();
        setId(wh, 1L);
        wh.setCode("KHO");
        Location loc = new Location();
        setId(loc, 10L);
        loc.setCode("A1");
        loc.setWarehouse(wh);

        Stock s = new Stock();
        s.setVariant(variant);
        s.setLot(lot);
        s.setLocation(loc);
        s.setQtyAvailable(available);
        s.setQtyHeld(0);
        s.setQtyQuarantine(0);
        s.setQtyExpired(expired);
        return s;
    }

    private static void setId(Object entity, long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
