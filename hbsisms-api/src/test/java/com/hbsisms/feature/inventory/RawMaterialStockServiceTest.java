package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.user.AppUser;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RawMaterialStockServiceTest {

    /** 23/11/2026 10:00 giờ VN. */
    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

    @Mock
    private RawMaterialRepository rawMaterialRepository;
    @Mock
    private RawMaterialLotRepository rawMaterialLotRepository;
    @Mock
    private RawMaterialLedgerRepository rawMaterialLedgerRepository;

    private RawMaterialStockService service;
    private RawMaterial ice;

    @BeforeEach
    void setUp() {
        service = new RawMaterialStockService(
                rawMaterialRepository, rawMaterialLotRepository, rawMaterialLedgerRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        ice = new RawMaterial();
        setId(ice, 3L);
        ice.setCode("NVL-003");
        ice.setName("Đá lạnh");
        ice.setUnit("kg");
        ice.setQtyOnHand(new BigDecimal("5"));
    }

    @Test
    void receive_createsLot_addsToTotal_andWritesInLedger() {
        GoodsReceipt receipt = new GoodsReceipt();
        setId(receipt, 20L);
        AppUser actor = new AppUser();
        setId(actor, 1L);
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "DA-01")).thenReturn(false);
        when(rawMaterialLotRepository.saveAndFlush(any(RawMaterialLot.class))).thenAnswer(inv -> inv.getArgument(0));

        RawMaterialLot lot = service.receive(ice, " DA-01 ", LocalDate.of(2026, 11, 22), LocalDate.of(2026, 12, 5),
                new BigDecimal("4000"), new BigDecimal("9.5"), receipt, actor, "Nhập nguyên liệu PN-20");

        assertThat(lot.getLotCode()).isEqualTo("DA-01");
        assertThat(lot.getQtyReceived()).isEqualByComparingTo("9.5");
        assertThat(lot.getQtyRemaining()).isEqualByComparingTo("9.5");
        assertThat(lot.getUnitPrice()).isEqualByComparingTo("4000");
        assertThat(lot.getExpDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(lot.getReceipt()).isSameAs(receipt);
        assertThat(lot.getReceivedAt()).isEqualTo(NOW);
        assertThat(ice.getQtyOnHand()).isEqualByComparingTo("14.5");
        verify(rawMaterialRepository).save(ice);

        ArgumentCaptor<RawMaterialLedger> ledger = ArgumentCaptor.forClass(RawMaterialLedger.class);
        verify(rawMaterialLedgerRepository).save(ledger.capture());
        assertThat(ledger.getValue().getEntryType()).isEqualTo(RawLedgerEntryType.IN);
        assertThat(ledger.getValue().getQtyDelta()).isEqualByComparingTo("9.5");
        assertThat(ledger.getValue().getBalanceAfter()).isEqualByComparingTo("14.5");
        assertThat(ledger.getValue().getRefType()).isEqualTo("goods_receipt");
        assertThat(ledger.getValue().getRefId()).isEqualTo(20L);
        assertThat(ledger.getValue().getCreatedBy()).isSameAs(actor);
    }

    @Test
    void receive_blankLotCode_autoGeneratesNextFreeCodeForThatMaterial() {
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261123-01")).thenReturn(true);
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261123-02")).thenReturn(false);
        when(rawMaterialLotRepository.saveAndFlush(any(RawMaterialLot.class))).thenAnswer(inv -> inv.getArgument(0));

        RawMaterialLot lot = service.receive(ice, "  ", null, null, BigDecimal.TEN, BigDecimal.ONE, null, null, null);

        assertThat(lot.getLotCode()).isEqualTo("NVL-261123-02");
    }

    @Test
    void receive_duplicateLotCodeForSameMaterial_returns409_noStockChange() {
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "DA-01")).thenReturn(true);

        assertThatThrownBy(() -> service.receive(
                        ice, "DA-01", null, null, BigDecimal.TEN, BigDecimal.ONE, null, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(ice.getQtyOnHand()).isEqualByComparingTo("5");
        verify(rawMaterialLotRepository, never()).saveAndFlush(any());
    }

    @Test
    void receive_nonPositiveQty_returns400() {
        assertThatThrownBy(() -> service.receive(
                        ice, null, null, null, BigDecimal.TEN, BigDecimal.ZERO, null, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void ensureOpeningLots_createsLotForStockWithoutLots_withoutChangingTotal() {
        when(rawMaterialRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(ice));
        when(rawMaterialRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(ice));
        when(rawMaterialLotRepository.sumRemaining(3L)).thenReturn(BigDecimal.ZERO);
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "TON-DAU")).thenReturn(false);
        when(rawMaterialLotRepository.saveAndFlush(any(RawMaterialLot.class))).thenAnswer(inv -> inv.getArgument(0));

        int created = service.ensureOpeningLots();

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<RawMaterialLot> lot = ArgumentCaptor.forClass(RawMaterialLot.class);
        verify(rawMaterialLotRepository).saveAndFlush(lot.capture());
        assertThat(lot.getValue().getLotCode()).isEqualTo("TON-DAU");
        assertThat(lot.getValue().getQtyRemaining()).isEqualByComparingTo("5");
        assertThat(lot.getValue().getUnitPrice()).isNull();
        assertThat(ice.getQtyOnHand()).isEqualByComparingTo("5");
        verify(rawMaterialRepository, never()).save(any());
        ArgumentCaptor<RawMaterialLedger> ledger = ArgumentCaptor.forClass(RawMaterialLedger.class);
        verify(rawMaterialLedgerRepository).save(ledger.capture());
        assertThat(ledger.getValue().getEntryType()).isEqualTo(RawLedgerEntryType.ADJUST);
        assertThat(ledger.getValue().getRefType()).isEqualTo(RawMaterialStockService.REF_OPENING);
    }

    @Test
    void ensureOpeningLots_lotsAlreadyMatchTotal_isNoop() {
        when(rawMaterialRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(ice));
        when(rawMaterialRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(ice));
        when(rawMaterialLotRepository.sumRemaining(3L)).thenReturn(new BigDecimal("5.000"));

        assertThat(service.ensureOpeningLots()).isZero();
        verify(rawMaterialLotRepository, never()).saveAndFlush(any());
        verify(rawMaterialLedgerRepository, never()).save(any());
    }

    @Test
    void lots_unknownMaterial_returns404() {
        when(rawMaterialRepository.existsById(77L)).thenReturn(false);

        assertThatThrownBy(() -> service.lots(77L, true))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void issueFefo_outsAcrossLotsInOrder_andWritesOutLedger() {
        ice.setQtyOnHand(new BigDecimal("10"));
        LocalDate today = LocalDate.of(2026, 11, 23);
        RawMaterialLot first = lot(11L, ice, "L1", new BigDecimal("4"), new BigDecimal("1000"));
        RawMaterialLot second = lot(12L, ice, "L2", new BigDecimal("6"), new BigDecimal("2000"));
        when(rawMaterialLotRepository.sumUsableRemaining(3L, today)).thenReturn(new BigDecimal("10"));
        when(rawMaterialLotRepository.findFefoUsable(3L, today)).thenReturn(List.of(first, second));
        when(rawMaterialLotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(first));
        when(rawMaterialLotRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(second));

        var slices = service.issueFefo(
                ice, new BigDecimal("5"), RawMaterialStockService.REF_MATERIAL_ISSUE, 99L, null, "xuất");

        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).qty()).isEqualByComparingTo("4");
        assertThat(slices.get(1).qty()).isEqualByComparingTo("1");
        assertThat(first.getQtyRemaining()).isEqualByComparingTo("0");
        assertThat(second.getQtyRemaining()).isEqualByComparingTo("5");
        assertThat(ice.getQtyOnHand()).isEqualByComparingTo("5");
        verify(rawMaterialLedgerRepository, org.mockito.Mockito.times(2)).save(any(RawMaterialLedger.class));
    }

    @Test
    void issueFefo_skipsExpiredLots_usesOnlyUsable() {
        ice.setQtyOnHand(new BigDecimal("10"));
        LocalDate today = LocalDate.of(2026, 11, 23);
        RawMaterialLot usable = lot(12L, ice, "L2", new BigDecimal("6"), new BigDecimal("2000"));
        when(rawMaterialLotRepository.sumUsableRemaining(3L, today)).thenReturn(new BigDecimal("6"));
        when(rawMaterialLotRepository.findFefoUsable(3L, today)).thenReturn(List.of(usable));
        when(rawMaterialLotRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(usable));

        var slices = service.issueFefo(
                ice, new BigDecimal("5"), RawMaterialStockService.REF_MATERIAL_ISSUE, 99L, null, "xuất");

        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).qty()).isEqualByComparingTo("5");
        assertThat(usable.getQtyRemaining()).isEqualByComparingTo("1");
        verify(rawMaterialLotRepository).findFefoUsable(3L, today);
    }

    @Test
    void issueFefo_whenUsableShort_returns409_evenIfOnHandIncludesExpired() {
        ice.setQtyOnHand(new BigDecimal("10"));
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(rawMaterialLotRepository.sumUsableRemaining(3L, today)).thenReturn(new BigDecimal("2"));

        assertThatThrownBy(() -> service.issueFefo(
                        ice, new BigDecimal("3"), RawMaterialStockService.REF_MATERIAL_ISSUE, 1L, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---- Mã lô nguyên liệu dư trả lại (-TL) ----

    @Test
    void nextLeftoverLotCode_firstReturn_isSourcePlusTl() {
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261124-01-TL"))
                .thenReturn(false);

        assertThat(service.nextLeftoverLotCode(ice, "NVL-261124-01")).isEqualTo("NVL-261124-01-TL");
    }

    @Test
    void nextLeftoverLotCode_tlTaken_countsUpToTl2Tl3() {
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261124-01-TL"))
                .thenReturn(true);
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261124-01-TL2"))
                .thenReturn(true);
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261124-01-TL3"))
                .thenReturn(false);

        assertThat(service.nextLeftoverLotCode(ice, "NVL-261124-01")).isEqualTo("NVL-261124-01-TL3");
    }

    @Test
    void nextLeftoverLotCode_sourceIsLeftoverLot_usesOriginalCode_notTlTl() {
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261124-01-TL"))
                .thenReturn(true);
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261124-01-TL2"))
                .thenReturn(false);

        assertThat(service.nextLeftoverLotCode(ice, "NVL-261124-01-TL")).isEqualTo("NVL-261124-01-TL2");
        assertThat(service.nextLeftoverLotCode(ice, "NVL-261124-01-TL-TL")).isEqualTo("NVL-261124-01-TL2");
    }

    @Test
    void nextLeftoverLotCode_checksOnlyThisMaterial() {
        RawMaterial film = new RawMaterial();
        setId(film, 4L);
        film.setName("Màng bọc");
        when(rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(4L, "NVL-261124-01-TL"))
                .thenReturn(false);

        assertThat(service.nextLeftoverLotCode(film, "NVL-261124-01")).isEqualTo("NVL-261124-01-TL");
        verify(rawMaterialLotRepository, never()).existsByRawMaterial_IdAndLotCodeIgnoreCase(3L, "NVL-261124-01-TL");
    }

    private static RawMaterialLot lot(Long id, RawMaterial material, String code, BigDecimal remaining, BigDecimal price) {
        RawMaterialLot lot = new RawMaterialLot();
        setId(lot, id);
        lot.setRawMaterial(material);
        lot.setLotCode(code);
        lot.setQtyReceived(remaining);
        lot.setQtyRemaining(remaining);
        lot.setUnitPrice(price);
        return lot;
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
