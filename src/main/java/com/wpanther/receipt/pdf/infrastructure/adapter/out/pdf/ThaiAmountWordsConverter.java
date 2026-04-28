package com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ThaiAmountWordsConverter {

    private static final String[] DIGITS =
        {"ศูนย์", "หนึ่ง", "สอง", "สาม", "สี่", "ห้า", "หก", "เจ็ด", "แปด", "เก้า"};

    private ThaiAmountWordsConverter() {}

    public static String toWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount must be non-negative");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        long totalSatang = amount.movePointRight(2).longValue();
        long baht   = totalSatang / 100;
        long satang = totalSatang % 100;

        StringBuilder result = new StringBuilder(numberToThai(baht));
        if (satang == 0) {
            result.append("บาทถ้วน");
        } else {
            result.append("บาท");
            result.append(numberToThai(satang));
            result.append("สตางค์");
        }
        return result.toString();
    }

    private static String numberToThai(long n) {
        if (n == 0) return "ศูนย์";

        StringBuilder sb = new StringBuilder();

        if (n >= 1_000_000) {
            sb.append(numberToThai(n / 1_000_000));
            sb.append("ล้าน");
            n %= 1_000_000;
            if (n == 0) return sb.toString();
        }

        // hasTens: true when the sub-million portion has a non-zero tens digit.
        // Controls whether the ones digit 1 is rendered as เอ็ด instead of หนึ่ง.
        boolean hasTens = (n % 100) >= 10;

        int[] place   = {100_000, 10_000, 1_000, 100, 10, 1};
        String[] unit = {"แสน",   "หมื่น", "พัน", "ร้อย", "สิบ", ""};

        for (int i = 0; i < place.length; i++) {
            int digit = (int) (n / place[i]);
            n %= place[i];
            if (digit == 0) continue;

            if (i == 4) {                     // tens position
                if (digit == 1)      sb.append("สิบ");
                else if (digit == 2) sb.append("ยี่สิบ");
                else                 sb.append(DIGITS[digit]).append("สิบ");
            } else if (i == 5) {              // ones position
                if (digit == 1 && hasTens) sb.append("เอ็ด");
                else                        sb.append(DIGITS[digit]);
            } else {
                sb.append(DIGITS[digit]).append(unit[i]);
            }
        }
        return sb.toString();
    }
}
