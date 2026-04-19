package com.example.envelopemoney.receipt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raw OCR output as lines (order top-to-bottom when available).
 */
public final class OcrResult {
    private final List<OcrLine> lines;

    public OcrResult(List<OcrLine> lines) {
        this.lines = lines != null ? new ArrayList<>(lines) : new ArrayList<>();
    }

    public List<OcrLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public String getFullText() {
        StringBuilder sb = new StringBuilder();
        for (OcrLine line : lines) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(line.text);
        }
        return sb.toString();
    }
}
