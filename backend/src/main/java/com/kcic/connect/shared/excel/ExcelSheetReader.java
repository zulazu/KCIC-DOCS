package com.kcic.connect.shared.excel;

import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Reads the first sheet of an uploaded XLSX into trimmed text rows.
 * Formula cells are rejected so imported values are always literal data.
 */
public final class ExcelSheetReader {

    private static final int MAX_COLUMNS = 64;

    public record DataRow(int rowNumber, List<String> cells) {
    }

    public record SheetData(List<String> headers, List<DataRow> rows) {
    }

    public static SheetData readFirstSheet(byte[] bytes, int maxDataRows) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE, "File harus berformat .xlsx.");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (!(workbook instanceof XSSFWorkbook)) {
                throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "Hanya workbook .xlsx yang didukung; simpan ulang file sebagai .xlsx.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null || headerRow.getLastCellNum() <= 0) {
                throw ApiException.validation("file", "Sheet pertama tidak memiliki baris header.");
            }
            if (headerRow.getLastCellNum() > MAX_COLUMNS) {
                throw ApiException.validation("file", "Jumlah kolom melebihi batas " + MAX_COLUMNS + ".");
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                headers.add(textValue(headerRow.getCell(column), formatter, 1).trim());
            }
            List<DataRow> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>(headers.size());
                boolean blank = true;
                for (int column = 0; column < headers.size(); column++) {
                    String value = textValue(row.getCell(column), formatter, rowIndex + 1).trim();
                    cells.add(value);
                    if (!value.isEmpty()) {
                        blank = false;
                    }
                }
                if (blank) {
                    continue;
                }
                if (rows.size() >= maxDataRows) {
                    throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                            "Jumlah baris data melebihi batas " + maxDataRows + ".");
                }
                rows.add(new DataRow(rowIndex + 1, List.copyOf(cells)));
            }
            return new SheetData(List.copyOf(headers), List.copyOf(rows));
        } catch (ApiException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "File bukan workbook Excel yang valid atau tidak dapat dibaca.");
        }
    }

    private static String textValue(Cell cell, DataFormatter formatter, int rowNumber) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.FORMULA) {
            throw ApiException.validation("baris " + rowNumber,
                    "Sel formula tidak didukung; gunakan nilai teks biasa.");
        }
        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            // Plain notation keeps long identifiers (NIP) readable instead of scientific notation.
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        return formatter.formatCellValue(cell);
    }

    private ExcelSheetReader() {
    }
}