package com.kcic.connect.shared.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelSheetReaderTest {

    @Test
    void readsTrimmedTextRowsAndSkipsBlankRows() throws Exception {
        byte[] bytes = workbook(sheet -> {
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("nip");
            header.createCell(1).setCellValue("full_name");
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue(" 00123 ");
            first.createCell(1).setCellValue("Budi");
            sheet.createRow(2);
            Row second = sheet.createRow(3);
            second.createCell(0).setCellValue("00456");
            second.createCell(1).setCellValue("Sari");
        });
        ExcelSheetReader.SheetData data = ExcelSheetReader.readFirstSheet(bytes, 10);
        assertThat(data.headers()).containsExactly("nip", "full_name");
        assertThat(data.rows()).hasSize(2);
        assertThat(data.rows().get(0).rowNumber()).isEqualTo(2);
        assertThat(data.rows().get(0).cells()).containsExactly("00123", "Budi");
        assertThat(data.rows().get(1).rowNumber()).isEqualTo(4);
    }

    @Test
    void numericCellsKeepPlainNotationAndLeadingZerosAreTextSafe() throws Exception {
        byte[] bytes = workbook(sheet -> {
            sheet.createRow(0).createCell(0).setCellValue("nip");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(123456789012345d);
        });
        ExcelSheetReader.SheetData data = ExcelSheetReader.readFirstSheet(bytes, 10);
        assertThat(data.rows().get(0).cells().get(0)).isEqualTo("123456789012345");
    }

    @Test
    void formulaCellsAreRejected() throws Exception {
        byte[] bytes = workbook(sheet -> {
            sheet.createRow(0).createCell(0).setCellValue("nip");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellFormula("1+1");
        });
        assertThatThrownBy(() -> ExcelSheetReader.readFirstSheet(bytes, 10))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).fieldErrors().get(0).message())
                        .contains("formula"));
    }

    @Test
    void rowLimitIsEnforced() throws Exception {
        byte[] bytes = workbook(sheet -> {
            sheet.createRow(0).createCell(0).setCellValue("nip");
            for (int index = 1; index <= 3; index++) {
                sheet.createRow(index).createCell(0).setCellValue("N" + index);
            }
        });
        assertThatThrownBy(() -> ExcelSheetReader.readFirstSheet(bytes, 2))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void legacyXlsAndNonExcelBytesAreRejected() throws Exception {
        byte[] xls;
        try (HSSFWorkbook legacy = new HSSFWorkbook()) {
            legacy.createSheet("Employee").createRow(0).createCell(0).setCellValue("nip");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            legacy.write(out);
            xls = out.toByteArray();
        }
        assertThatThrownBy(() -> ExcelSheetReader.readFirstSheet(xls, 10))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo(ErrorCode.UNSUPPORTED_FILE_TYPE));
        assertThatThrownBy(() -> ExcelSheetReader.readFirstSheet("bukan excel".getBytes(), 10))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void writerProducesTextCellsReadableBack() throws Exception {
        byte[] bytes = ExcelWriter.write(List.of(new ExcelWriter.SheetData("Data",
                List.of("nip", "name"), List.of(List.of("00123", "=SUM(A1)")))));
        ExcelSheetReader.SheetData data = ExcelSheetReader.readFirstSheet(bytes, 10);
        assertThat(data.rows().get(0).cells()).containsExactly("00123", "=SUM(A1)");
    }

    private interface SheetFiller {
        void fill(Sheet sheet);
    }

    private static byte[] workbook(SheetFiller filler) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            filler.fill(workbook.createSheet("Employee"));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}