package com.settlr;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.settlr.SplitCalculator.Mode;

class SplitCalculatorTest {

    private static Map<Integer, Double> inputs(double... values) {
        Map<Integer, Double> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put(i + 1, values[i]);
        }
        return map;
    }

    private static long sum(Map<Integer, Long> shares) {
        return shares.values().stream().mapToLong(Long::longValue).sum();
    }

    @Test
    void equalSplitNeverLosesAPaisa() {
        Map<Integer, Long> shares = SplitCalculator.compute(Mode.EQUAL, 10000, inputs(1, 1, 1));
        assertEquals(10000, sum(shares));
        assertEquals(3334, shares.get(1));
        assertEquals(3333, shares.get(2));
        assertEquals(3333, shares.get(3));
    }

    @Test
    void percentSplitFollowsPercentages() {
        Map<Integer, Long> shares = SplitCalculator.compute(Mode.PERCENT, 100000, inputs(50, 30, 20));
        assertEquals(50000, shares.get(1));
        assertEquals(30000, shares.get(2));
        assertEquals(20000, shares.get(3));
    }

    @Test
    void percentsMustTotalHundred() {
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.compute(Mode.PERCENT, 100000, inputs(50, 30, 10)));
    }

    @Test
    void sharesSplitProportionally() {
        Map<Integer, Long> shares = SplitCalculator.compute(Mode.SHARES, 90000, inputs(2, 1));
        assertEquals(60000, shares.get(1));
        assertEquals(30000, shares.get(2));
    }

    @Test
    void sharesAlwaysAddUpToTheTotal() {
        Map<Integer, Long> shares = SplitCalculator.compute(Mode.SHARES, 10001, inputs(1, 1, 1, 2));
        assertEquals(10001, sum(shares));
    }

    @Test
    void exactAmountsMustMatchTheTotal() {
        Map<Integer, Long> ok = SplitCalculator.compute(Mode.EXACT, 50000, inputs(200, 300));
        assertEquals(20000, ok.get(1));
        assertEquals(30000, ok.get(2));
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.compute(Mode.EXACT, 50000, inputs(200, 250)));
    }

    @Test
    void rejectsEmptyParticipantsAndBadTotals() {
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.compute(Mode.EQUAL, 1000, new LinkedHashMap<>()));
        assertThrows(IllegalArgumentException.class,
                () -> SplitCalculator.compute(Mode.EQUAL, 0, inputs(1)));
    }
}