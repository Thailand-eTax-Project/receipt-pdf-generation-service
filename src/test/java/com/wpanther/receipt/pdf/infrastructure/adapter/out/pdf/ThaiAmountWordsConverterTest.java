package com.wpanther.receipt.pdf.infrastructure.adapter.out.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ThaiAmountWordsConverter Unit Tests")
class ThaiAmountWordsConverterTest {

    @Test
    @DisplayName("Whole baht with zero satang appends 'บาทถ้วน'")
    void toWords_wholeBaht_appendsThuean() {
        assertThat(ThaiAmountWordsConverter.toWords(new BigDecimal("100.00")))
                .isEqualTo("หนึ่งร้อยบาทถ้วน");
    }

    @Test
    @DisplayName("Amount with satang appends 'สตางค์'")
    void toWords_withSatang_appendsSatang() {
        assertThat(ThaiAmountWordsConverter.toWords(new BigDecimal("64788.50")))
                .isEqualTo("หกหมื่นสี่พันเจ็ดร้อยแปดสิบแปดบาทห้าสิบสตางค์");
    }

    @Test
    @DisplayName("Zero amount returns 'ศูนย์บาทถ้วน'")
    void toWords_zero_returnsZeroBaht() {
        assertThat(ThaiAmountWordsConverter.toWords(BigDecimal.ZERO))
                .isEqualTo("ศูนย์บาทถ้วน");
    }

    @Test
    @DisplayName("11 baht uses เอ็ด for ones digit")
    void toWords_elevenBaht_usesEd() {
        assertThat(ThaiAmountWordsConverter.toWords(new BigDecimal("11.00")))
                .isEqualTo("สิบเอ็ดบาทถ้วน");
    }

    @Test
    @DisplayName("21 baht uses ยี่สิบ for the tens")
    void toWords_twentyOne_usesYiSib() {
        assertThat(ThaiAmountWordsConverter.toWords(new BigDecimal("21.00")))
                .isEqualTo("ยี่สิบเอ็ดบาทถ้วน");
    }

    @Test
    @DisplayName("101 baht — ones digit 1 without tens uses หนึ่ง (not เอ็ด)")
    void toWords_oneHundredOne_onesIsNueng() {
        assertThat(ThaiAmountWordsConverter.toWords(new BigDecimal("101.00")))
                .isEqualTo("หนึ่งร้อยหนึ่งบาทถ้วน");
    }

    @Test
    @DisplayName("1,000,000 baht produces หนึ่งล้านบาทถ้วน")
    void toWords_oneMillion_correct() {
        assertThat(ThaiAmountWordsConverter.toWords(new BigDecimal("1000000.00")))
                .isEqualTo("หนึ่งล้านบาทถ้วน");
    }

    @Test
    @DisplayName("10 satang only produces digit word + สตางค์")
    void toWords_tenSatang_correct() {
        assertThat(ThaiAmountWordsConverter.toWords(new BigDecimal("1.10")))
                .isEqualTo("หนึ่งบาทสิบสตางค์");
    }

    @Test
    @DisplayName("Null amount throws IllegalArgumentException")
    void toWords_null_throwsIllegalArgument() {
        assertThatThrownBy(() -> ThaiAmountWordsConverter.toWords(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Negative amount throws IllegalArgumentException")
    void toWords_negative_throwsIllegalArgument() {
        assertThatThrownBy(() -> ThaiAmountWordsConverter.toWords(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
