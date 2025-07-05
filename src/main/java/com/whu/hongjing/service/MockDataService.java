package com.whu.hongjing.service;

public interface MockDataService {
    /**
     * 【工具I：创世】
     */
    String createMockCustomers(int customerCount);

    /**
     * 【工具II：演绎】
     */
    String simulateTradingDays(int days);
}