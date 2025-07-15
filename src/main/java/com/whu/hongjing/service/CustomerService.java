package com.whu.hongjing.service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.vo.ProfitLossVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.Customer;
//import java.util.List;

public interface CustomerService extends IService<Customer>{

    boolean save(Customer customer);

    boolean removeCustomer(Long id);

    boolean updateCustomer(Customer customer);

    // 作为唯一保留下来的根据id查询客户信息的方法，提供给其他方法必须要根据customer_id获取客户基础数据时的中转（如客户详情页 编辑客户的表单页）
    Customer getCustomerById(Long id);


    // 根据一堆参数分页显示和查询客户的方法
    Page<Customer> getCustomerPage(Page<Customer> page, Long customerId, String name, String idNumber, String tagName);


    Page<ProfitLossVO> getProfitLossPage(Page<ProfitLossVO> page, Long customerId,
                                         String customerName, String sortField, String sortOrder);



    // --- 【【【 新增方法声明 】】】 ---
    /**
     * 根据客户ID，获取该客户的盈亏统计信息
     * @param customerId 客户ID
     * @return 客户的盈亏视图对象
     */
    ProfitLossVO getProfitLossVO(Long customerId);


}
