package com.hbsisms.feature.costing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostRunningBalanceTest {

    @Test
    void inbound_updatesWacLikeChatGptExample() {
        CostRunningBalance row = CostRunningBalance.empty(CostItemType.FINISHED, 1L);

        row.applyInbound(bd("10"), bd("100000"));
        assertThat(row.getQtyOnHand()).isEqualByComparingTo("10");
        assertThat(row.getInventoryValue()).isEqualByComparingTo("1000000");
        assertThat(row.getAvgUnitCost()).isEqualByComparingTo("100000");

        row.applyInbound(bd("3"), bd("150000"));
        assertThat(row.getQtyOnHand()).isEqualByComparingTo("13");
        assertThat(row.getInventoryValue()).isEqualByComparingTo("1450000");
        assertThat(row.getAvgUnitCost()).isEqualByComparingTo("111538.46");
    }

    @Test
    void outbound_reducesValueAtCurrentWac_avgUnchangedUntilNextInbound() {
        CostRunningBalance row = CostRunningBalance.of(CostItemType.FINISHED, 1L, bd("13"), bd("1450000"));
        assertThat(row.getAvgUnitCost()).isEqualByComparingTo("111538.46");

        BigDecimal unit = row.applyOutbound(bd("2"));
        assertThat(unit).isEqualByComparingTo("111538.46");
        assertThat(row.getQtyOnHand()).isEqualByComparingTo("11");
        assertThat(row.getInventoryValue())
                .isEqualByComparingTo(bd("1450000").subtract(bd("2").multiply(bd("111538.46"))));
        assertThat(row.getAvgUnitCost()).isEqualByComparingTo("111538.46");
    }

    @Test
    void outbound_toZero_clearsAvg() {
        CostRunningBalance row = CostRunningBalance.of(CostItemType.RAW_MATERIAL, 2L, bd("5"), bd("500000"));
        row.applyOutbound(bd("5"));
        assertThat(row.getQtyOnHand()).isEqualByComparingTo("0");
        assertThat(row.getInventoryValue()).isEqualByComparingTo("0");
        assertThat(row.getAvgUnitCost()).isNull();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
