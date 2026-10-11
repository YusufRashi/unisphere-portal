package com.unisphere.common.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Minimal RFC 4180 CSV writer for report exports. Text cells that a spreadsheet would treat as a formula
 * (starting with {@code = + - @}, tab or carriage return) are prefixed with an apostrophe, so a name like
 * {@code =HYPERLINK(...)} entered in the system is shown as text instead of being executed when the file is
 * opened (CSV/formula injection). Numbers are written as numbers and never prefixed.
 */
public final class CsvWriter {

    /** Lets Excel detect UTF-8, so accented names survive. */
    public static final String UTF8_BOM = "﻿";

    private CsvWriter() {
    }

    public static String write(List<String> header, List<List<Object>> rows) {
        StringBuilder out = new StringBuilder(UTF8_BOM);
        appendRow(out, List.copyOf(header));
        rows.forEach(row -> appendRow(out, row));
        return out.toString();
    }

    private static void appendRow(StringBuilder out, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return number instanceof BigDecimal d ? d.toPlainString() : number.toString();
        }
        String text = value instanceof Instant instant ? DateTimeFormatter.ISO_INSTANT.format(instant) : value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
