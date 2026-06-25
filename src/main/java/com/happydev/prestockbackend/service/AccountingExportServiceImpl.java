package com.happydev.prestockbackend.service;

import com.happydev.prestockbackend.dto.FinancialStatementDto;
import com.happydev.prestockbackend.dto.LedgerEntryDto;
import com.happydev.prestockbackend.dto.TrialBalanceRowDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class AccountingExportServiceImpl implements AccountingExportService {

    private final AccountingReportService reportService;

    public AccountingExportServiceImpl(AccountingReportService reportService) {
        this.reportService = reportService;
    }

    // ── Trial Balance ───────────────────────────────────────────────────────

    @Override
    public byte[] exportTrialBalance(LocalDate from, LocalDate to) {
        List<TrialBalanceRowDto> rows = reportService.getTrialBalance(from, to);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Balanza de Comprobación");
            CellStyle headerStyle = boldStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            CellStyle titleStyle = titleStyle(wb);

            int r = 0;
            titleRow(sheet, r++, "Balanza de Comprobación", 5, titleStyle);
            titleRow(sheet, r++, from + " — " + to, 5, headerStyle);
            r++;

            Row header = sheet.createRow(r++);
            String[] cols = {"Código", "Cuenta", "Tipo", "Débito", "Crédito", "Saldo"};
            for (int i = 0; i < cols.length; i++) cell(header, i, cols[i], headerStyle);

            BigDecimal totDr = BigDecimal.ZERO, totCr = BigDecimal.ZERO, totBal = BigDecimal.ZERO;
            for (TrialBalanceRowDto row : rows) {
                Row dr = sheet.createRow(r++);
                cell(dr, 0, row.code());
                cell(dr, 1, row.name());
                cell(dr, 2, row.type());
                moneyCell(dr, 3, row.totalDebit(), moneyStyle);
                moneyCell(dr, 4, row.totalCredit(), moneyStyle);
                moneyCell(dr, 5, row.balance(), moneyStyle);
                totDr  = totDr.add(row.totalDebit());
                totCr  = totCr.add(row.totalCredit());
                totBal = totBal.add(row.balance());
            }

            Row totRow = sheet.createRow(r);
            cell(totRow, 1, "TOTAL", headerStyle);
            moneyCell(totRow, 3, totDr, boldMoneyStyle(wb));
            moneyCell(totRow, 4, totCr, boldMoneyStyle(wb));
            moneyCell(totRow, 5, totBal, boldMoneyStyle(wb));

            for (int i = 0; i <= 5; i++) sheet.autoSizeColumn(i);
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de balanza", e);
        }
    }

    // ── Income Statement ────────────────────────────────────────────────────

    @Override
    public byte[] exportIncomeStatement(LocalDate from, LocalDate to) {
        FinancialStatementDto dto = reportService.getIncomeStatement(from, to);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Estado de Resultados");
            CellStyle headerStyle = boldStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            CellStyle titleStyle = titleStyle(wb);

            int r = 0;
            titleRow(sheet, r++, "Estado de Resultados", 3, titleStyle);
            titleRow(sheet, r++, dto.getPeriodLabel(), 3, headerStyle);
            r++;

            for (FinancialStatementDto.Section section : dto.getSections()) {
                Row secRow = sheet.createRow(r++);
                cell(secRow, 0, section.getName().toUpperCase(), headerStyle);

                for (FinancialStatementDto.LineItem item : section.getItems()) {
                    Row itemRow = sheet.createRow(r++);
                    cell(itemRow, 0, item.getCode());
                    cell(itemRow, 1, item.getName());
                    moneyCell(itemRow, 2, item.getAmount(), moneyStyle);
                }

                Row subtotalRow = sheet.createRow(r++);
                cell(subtotalRow, 1, "Subtotal " + section.getName(), boldStyle(wb));
                moneyCell(subtotalRow, 2, section.getSubtotal(), boldMoneyStyle(wb));
                r++;
            }

            Row netRow = sheet.createRow(r);
            cell(netRow, 1, "UTILIDAD NETA", titleStyle);
            moneyCell(netRow, 2, dto.getNetIncome(), boldMoneyStyle(wb));

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de estado de resultados", e);
        }
    }

    // ── Balance Sheet ───────────────────────────────────────────────────────

    @Override
    public byte[] exportBalanceSheet(LocalDate asOfDate) {
        FinancialStatementDto dto = reportService.getBalanceSheet(asOfDate);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Balance General");
            CellStyle headerStyle = boldStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            CellStyle titleStyle = titleStyle(wb);

            int r = 0;
            titleRow(sheet, r++, "Balance General", 3, titleStyle);
            titleRow(sheet, r++, dto.getPeriodLabel(), 3, headerStyle);
            r++;

            for (FinancialStatementDto.Section section : dto.getSections()) {
                Row secRow = sheet.createRow(r++);
                cell(secRow, 0, section.getName().toUpperCase(), headerStyle);

                for (FinancialStatementDto.LineItem item : section.getItems()) {
                    Row itemRow = sheet.createRow(r++);
                    cell(itemRow, 0, item.getCode());
                    cell(itemRow, 1, item.getName());
                    moneyCell(itemRow, 2, item.getAmount(), moneyStyle);
                }

                Row subtotalRow = sheet.createRow(r++);
                cell(subtotalRow, 1, "Total " + section.getName(), boldStyle(wb));
                moneyCell(subtotalRow, 2, section.getSubtotal(), boldMoneyStyle(wb));
                r++;
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de balance general", e);
        }
    }

    // ── Ledger ──────────────────────────────────────────────────────────────

    @Override
    public byte[] exportLedger(Long accountId, LocalDate from, LocalDate to) {
        List<LedgerEntryDto> rows = reportService.getLedger(accountId, from, to);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Libro Mayor");
            CellStyle headerStyle = boldStyle(wb);
            CellStyle moneyStyle = moneyStyle(wb);
            CellStyle titleStyle = titleStyle(wb);

            int r = 0;
            titleRow(sheet, r++, "Libro Mayor — Cuenta #" + accountId, 6, titleStyle);
            titleRow(sheet, r++, from + " — " + to, 6, headerStyle);
            r++;

            Row header = sheet.createRow(r++);
            String[] cols = {"Fecha", "Referencia", "Descripción asiento", "Descripción línea", "Débito", "Crédito", "Saldo"};
            for (int i = 0; i < cols.length; i++) cell(header, i, cols[i], headerStyle);

            for (LedgerEntryDto row : rows) {
                Row dr = sheet.createRow(r++);
                cell(dr, 0, row.entryDate() != null ? row.entryDate().toString() : "");
                cell(dr, 1, nvl(row.reference()));
                cell(dr, 2, nvl(row.entryDescription()));
                cell(dr, 3, nvl(row.lineDescription()));
                moneyCell(dr, 4, row.debit(), moneyStyle);
                moneyCell(dr, 5, row.credit(), moneyStyle);
                moneyCell(dr, 6, row.runningBalance(), moneyStyle);
            }

            for (int i = 0; i <= 6; i++) sheet.autoSizeColumn(i);
            return toBytes(wb);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de libro mayor", e);
        }
    }

    // ── POI helpers ─────────────────────────────────────────────────────────

    private void titleRow(Sheet sheet, int rowIndex, String text, int lastCol, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        cell(row, 0, text, style);
        if (lastCol > 0) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, lastCol));
        }
    }

    private void cell(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    private void cell(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value : "");
        c.setCellStyle(style);
    }

    private void moneyCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value != null ? value.doubleValue() : 0.0);
        c.setCellStyle(style);
    }

    private CellStyle boldStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle titleStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 13);
        style.setFont(font);
        return style;
    }

    private CellStyle moneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }

    private CellStyle boldMoneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        DataFormat fmt = wb.createDataFormat();
        style.setDataFormat(fmt.getFormat("#,##0.00"));
        return style;
    }

    private byte[] toBytes(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
