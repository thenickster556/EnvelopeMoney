package com.example.envelopemoney;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class MoneyMathTest {

    @Test
    public void roundToCents_halfUp() {
        assertEquals(33.33, MoneyMath.roundToCents(100.0 / 3), 0.001);
        assertEquals(50.00, MoneyMath.roundToCents(50.004), 0.001);
        assertEquals(50.01, MoneyMath.roundToCents(50.005), 0.001);
    }

    @Test
    public void splitIntegerPercentsFirstCeiling_examples() {
        assertArrayEquals(new int[]{100}, MoneyMath.splitIntegerPercentsFirstCeiling(1));
        assertArrayEquals(new int[]{50, 50}, MoneyMath.splitIntegerPercentsFirstCeiling(2));
        assertArrayEquals(new int[]{34, 33, 33}, MoneyMath.splitIntegerPercentsFirstCeiling(3));
        assertArrayEquals(new int[]{20, 20, 20, 20, 20}, MoneyMath.splitIntegerPercentsFirstCeiling(5));
    }

    @Test
    public void splitTotalByPercents_sumsToTotal() {
        assertAmountsSum(100.00, MoneyMath.splitTotalByPercents(100.00,
                MoneyMath.splitIntegerPercentsFirstCeiling(3)));
        assertEquals(34.00, MoneyMath.splitTotalByPercents(100.00, new int[]{34, 33, 33})[0], 0.001);
        assertEquals(33.00, MoneyMath.splitTotalByPercents(100.00, new int[]{34, 33, 33})[1], 0.001);
        assertEquals(33.00, MoneyMath.splitTotalByPercents(100.00, new int[]{34, 33, 33})[2], 0.001);

        double[] uneven = MoneyMath.splitTotalByPercents(27.50,
                MoneyMath.splitIntegerPercentsFirstCeiling(3));
        assertAmountsSum(27.50, uneven);
    }

    @Test
    public void splitTotalByPercents_tinyTotal() {
        double[] amounts = MoneyMath.splitTotalByPercents(0.01,
                MoneyMath.splitIntegerPercentsFirstCeiling(2));
        assertAmountsSum(0.01, amounts);
    }

    private static void assertAmountsSum(double expectedTotal, double[] amounts) {
        double sum = 0d;
        for (double amount : amounts) {
            sum += amount;
        }
        assertEquals(expectedTotal, MoneyMath.roundToCents(sum), 0.001);
    }
}
