package com.happydev.prestockbackend.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class FinancialStatementDto {

    private List<Section> sections = new ArrayList<>();
    private String periodLabel;

    // Income Statement totals
    private BigDecimal totalRevenue;
    private BigDecimal totalExpenses;
    private BigDecimal netIncome;

    // Balance Sheet totals
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;

    public FinancialStatementDto() {}

    // -------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------

    public static class Section {
        private String name;
        private String type;
        private List<LineItem> items = new ArrayList<>();
        private BigDecimal subtotal = BigDecimal.ZERO;

        public Section() {}

        public Section(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public List<LineItem> getItems() { return items; }
        public void setItems(List<LineItem> items) { this.items = items; }

        public BigDecimal getSubtotal() { return subtotal; }
        public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    }

    public static class LineItem {
        private String code;
        private String name;
        private BigDecimal amount;

        public LineItem() {}

        public LineItem(String code, String name, BigDecimal amount) {
            this.code = code;
            this.name = name;
            this.amount = amount;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    // -------------------------------------------------------
    // Getters / Setters
    // -------------------------------------------------------

    public List<Section> getSections() { return sections; }
    public void setSections(List<Section> sections) { this.sections = sections; }

    public String getPeriodLabel() { return periodLabel; }
    public void setPeriodLabel(String periodLabel) { this.periodLabel = periodLabel; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public void setTotalExpenses(BigDecimal totalExpenses) { this.totalExpenses = totalExpenses; }

    public BigDecimal getNetIncome() { return netIncome; }
    public void setNetIncome(BigDecimal netIncome) { this.netIncome = netIncome; }

    public BigDecimal getTotalAssets() { return totalAssets; }
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }

    public BigDecimal getTotalLiabilities() { return totalLiabilities; }
    public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }

    public BigDecimal getTotalEquity() { return totalEquity; }
    public void setTotalEquity(BigDecimal totalEquity) { this.totalEquity = totalEquity; }
}
