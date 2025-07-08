package com.whu.hongjing.service;
import java.util.List;
import com.whu.hongjing.pojo.entity.Customer;

public interface MockDataService {
    /**
     * 【工具I：创世】
     */
    String createMockCustomers(int customerCount);

    /**
     * 【工具II：演绎】
     */
    String simulateTradingDays(int days);

    /**
     * 这是一个独立的、带事务的方法，专门负责将模拟好的客户和风险评估数据写入数据库。
     * 必须在接口中声明，这样Spring的代理对象(self)才能找到并调用它。
     * @param customersToSave 在内存中生成好的客户对象列表
     */
    void saveCustomersAndAssessmentsInTransaction(List<Customer> customersToSave);


}