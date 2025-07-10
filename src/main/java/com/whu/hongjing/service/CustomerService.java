package com.whu.hongjing.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.vo.ProfitLossVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.Customer;
import java.util.List;

public interface CustomerService extends IService<Customer>{
    boolean removeCustomer(Long id);

    boolean updateCustomer(Customer customer);

    Customer getCustomerById(Long id);

    // 分页显示和查询客户的方法
    Page<Customer> getCustomerPage(Page<Customer> page, Long customerId, String name, String idNumber, String tagName);

    boolean save(Customer customer);

    // 根据身份证号查询客户的接口方法
    Customer getCustomerByIdNumber(String idNumber);

    // 根据姓名查询客户列表的接口方法
    List<Customer> getCustomersByName(String name);

    List<Customer> getCustomersByTag(String tagName);

    Page<ProfitLossVO> getProfitLossPage(
            Page<ProfitLossVO> page, String customerName, String sortField, String sortOrder);

    /**
     * 【新增】根据多个标签名，找出同时拥有这些标签的所有客户ID
     * @param tagNames 标签名称列表
     * @return 符合条件的客户ID列表
     */
    List<Long> findCustomerIdsByTags(List<String> tagNames);


    // --- 【【【 新增方法声明 】】】 ---
    /**
     * 根据客户ID，获取该客户的盈亏统计信息
     * @param customerId 客户ID
     * @return 客户的盈亏视图对象
     */
    ProfitLossVO getProfitLossVO(Long customerId);


}
