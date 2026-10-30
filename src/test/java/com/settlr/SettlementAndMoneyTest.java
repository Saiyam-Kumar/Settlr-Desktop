package com.settlr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.settlr.SettlementCalculator.Transfer;

class SettlementAndMoneyTest {

    /** The worked example from the project document: A +1000, B +200, C -200, D -1000. */
    @Test
    void transfersSettleEveryone() {
        List<Balance> balances = List.of(
                new Balance(1, "A", 2000, 1000),
                new Balance(2, "B", 1200, 1000),
                new Balance(3, "C", 800, 1000),
                new Balance(4, "D", 0, 1000));

        List<Transfer> transfers = SettlementCalculator.suggest(balances);

        Map<Integer, Double> net = new HashMap<>();
        balances.forEach(b -> net.put(b.getMemberId(), b.getBalance()));
        for (Transfer t : transfers) {
            net.merge(t.fromId(), t.amount(), Double::sum);
            net.merge(t.toId(), -t.amount(), Double::sum);
        }
        net.values().forEach(v -> assertEquals(0.0, v, 0.001));
        assertTrue(transfers.size() <= 3, "n people never need more than n-1 transfers");
    }

    @Test
    void nobodyOwesWhenEverythingIsEven() {
        List<Balance> balances = List.of(new Balance(1, "A", 500, 500), new Balance(2, "B", 500, 500));
        assertTrue(SettlementCalculator.suggest(balances).isEmpty());
    }

    @Test
    void settlementsReduceBalances() {
        // B owed 400, then paid 400 to A: balance becomes 0
        Balance b = new Balance(2, "B", 0, 400, 400, 0);
        assertEquals(0.0, b.getBalance(), 0.001);
    }

    @Test
    void formatsIndianGrouping() {
        assertEquals("₹0.00", Money.format(0));
        assertEquals("₹999.50", Money.format(999.5));
        assertEquals("₹1,000.00", Money.format(1000));
        assertEquals("₹12,34,567.50", Money.format(1234567.5));
        assertEquals("-₹2,500.00", Money.format(-2500));
    }

    @Test
    void parsesOnlyValidAmounts() {
        assertEquals(250.5, Money.parse("250.50"));
        assertEquals(1200.0, Money.parse("1,200"));
        assertNull(Money.parse("12.345"));
        assertNull(Money.parse("-5"));
        assertNull(Money.parse("abc"));
        assertNull(Money.parse(""));
    }
}