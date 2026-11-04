package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.inventory.dto.LotExpireJobResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LotExpireServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockService stockService;

    private LotExpireService service;

    @BeforeEach
    void setUp() {
        service = new LotExpireService(stockRepository, stockService, "Asia/Ho_Chi_Minh");
    }

    @Test
    void run_expiresEachAvailablePastHsdRow() {
        Stock row = stock(1L, 5, LocalDate.now().minusDays(1));
        when(stockRepository.findAvailablePastExpiry(any(LocalDate.class))).thenReturn(List.of(row));
        when(stockService.expire(any())).thenReturn(row);

        LotExpireJobResponse result = service.run();

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);

        ArgumentCaptor<StockMutation> captor = ArgumentCaptor.forClass(StockMutation.class);
        verify(stockService).expire(captor.capture());
        assertThat(captor.getValue().qty()).isEqualTo(5);
        assertThat(captor.getValue().refType()).isEqualTo(LotExpireService.REF_TYPE);
        assertThat(captor.getValue().refId()).isEqualTo(1L);
    }

    @Test
    void run_countsFailureWithoutStoppingOthers() {
        Stock ok = stock(1L, 2, LocalDate.now().minusDays(2));
        Stock bad = stock(2L, 3, LocalDate.now().minusDays(1));
        when(stockRepository.findAvailablePastExpiry(any(LocalDate.class))).thenReturn(List.of(ok, bad));
        when(stockService.expire(any()))
                .thenReturn(ok)
                .thenThrow(new RuntimeException("Không đủ tồn kho"));

        LotExpireJobResponse result = service.run();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(stockService, times(2)).expire(any());
    }

    @Test
    void run_noopWhenNoRows() {
        when(stockRepository.findAvailablePastExpiry(any(LocalDate.class))).thenReturn(List.of());

        LotExpireJobResponse result = service.run();

        assertThat(result.scanned()).isEqualTo(0);
        assertThat(result.expired()).isEqualTo(0);
        verify(stockService, never()).expire(any());
    }

    private static Stock stock(long id, int available, LocalDate expDate) {
        ProductVariant variant = new ProductVariant();
        setId(variant, 10L);
        Lot lot = new Lot();
        setId(lot, 20L + id);
        lot.setLotCode("LOT-" + id);
        lot.setExpDate(expDate);
        Location location = new Location();
        setId(location, 30L);
        Stock stock = new Stock();
        setId(stock, id);
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(location);
        stock.setQtyAvailable(available);
        stock.setQtyExpired(0);
        return stock;
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
