package com.unisphere.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CsvWriterTest {

    @Test
    void writesAHeaderAndRowsWithCrlfAndABom() {
        String csv = CsvWriter.write(List.of("Name", "Amount"), List.of(List.of("Amina Otieno", new BigDecimal("85000.00"))));
        assertThat(csv).isEqualTo("﻿Name,Amount\r\nAmina Otieno,85000.00\r\n");
    }

    @Test
    void quotesCommasQuotesAndLineBreaks() {
        assertThat(CsvWriter.cell("Otieno, Amina")).isEqualTo("\"Otieno, Amina\"");
        assertThat(CsvWriter.cell("The \"Prof\"")).isEqualTo("\"The \"\"Prof\"\"\"");
        assertThat(CsvWriter.cell("two\nlines")).isEqualTo("\"two\nlines\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://evil\")", "+1+1", "-2+3", "@SUM(A1)", "\tcmd"})
    void neutralisesSpreadsheetFormulas(String text) {
        assertThat(CsvWriter.cell(text)).contains("'" + text.charAt(0));
    }

    @Test
    void numbersStayNumbersEvenWhenNegative() {
        assertThat(CsvWriter.cell(new BigDecimal("-50.00"))).isEqualTo("-50.00");
        assertThat(CsvWriter.cell(42)).isEqualTo("42");
    }

    @Test
    void writesInstantsAsIsoAndNullAsEmpty() {
        assertThat(CsvWriter.cell(Instant.parse("2026-10-04T16:10:00Z"))).isEqualTo("2026-10-04T16:10:00Z");
        assertThat(CsvWriter.cell(null)).isEmpty();
    }
}
