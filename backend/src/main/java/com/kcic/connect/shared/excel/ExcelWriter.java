package com.kcic.connect.shared.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

/**
 * Builds XLSX workbooks with text-only cells so identifiers such as NIP keep
 * leading zeros and exported values can never be interpreted as formulas.
 */
public final class ExcelWriter {

    public record SheetData(String name, List<String> headers, List<List<String>> rows) {
    }

    public static byte[] write(List<SheetData> sheets) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            for (SheetData sheetData : sheets) {
                Sheet sheet = workbook.createSheet(sheetData.name());
                writeRow(sheet, 0, sheetData.headers(), headerStyle);
                int rowIndex = 1;
                for (List<String> values : sheetData.rows()) {
                    writeRow(sheet, rowIndex++, values, null);
                }
                for (int column = 0; column < sheetData.headers().size(); column++) {
                    int width = sheetData.headers().get(column).length();
                    for (List<String> values : sheetData.rows()) {
                        if (column < values.size()) {
                            width = Math.max(width, values.get(column).length());
                        }
                    }
                    sheet.setColumnWidth(column, Math.min(Math.max(width + 2, 12), 60) * 256);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal membuat file Excel.", ex);
        }
    }

    private static void writeRow(Sheet sheet, int rowIndex, List<String> values, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        for (int column = 0; column < values.size(); column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(values.get(column) == null ? "" : values.get(column));
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private ExcelWriter() {
    }
}