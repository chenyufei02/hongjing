package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerMapper;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.CustomerTagRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private CustomerTagRelationService customerTagRelationService;

    @Override
    public boolean removeCustomer(Long id) {
        return customerMapper.deleteById(id) > 0;
    }

    @Override
    public boolean updateCustomer(Customer customer) {
        return customerMapper.updateById(customer) > 0;
    }

    @Override
    public Customer getCustomerById(Long id) {
        return customerMapper.selectById(id);
    }

    @Override
    public Customer getCustomerByIdNumber(String idNumber) {
        QueryWrapper<Customer> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id_number", idNumber);
        return this.getOne(queryWrapper); // getOne确保只返回一条记录
    }

    @Override
    public List<Customer> getCustomersByName(String name) {
        QueryWrapper<Customer> queryWrapper = new QueryWrapper<>();
        queryWrapper.like("name", name);
        return this.list(queryWrapper); // list会返回所有匹配的记录
    }

//    @Override
//    public List<Customer> getAllCustomers() {
//        return customerMapper.selectList(null);
//    }


    /**
     * 【新增】分页查询客户列表的实现
     * @param page 分页对象，包含了当前的页码和每页显示数量
     * @return 包含分页信息和当前页数据的Page对象
     */
    @Override
    public Page<Customer> getCustomerPage(Page<Customer> page) {
        // 直接调用MyBatis-Plus提供的page方法，传入分页对象和查询条件（这里为null，即查询所有）
        // 分页插件会自动拦截这个查询，并追加LIMIT
        return this.page(page, null);
    }

    @Override
    public boolean save(Customer entity) {
        return super.save(entity);
    }

    @Override
    public List<Customer> getCustomersByTag(String tagName) {
        // 1. 先从标签关系表中，找到所有拥有该标签的 customer_id
        QueryWrapper<CustomerTagRelation> tagQuery = new QueryWrapper<>();
        tagQuery.eq("tag_name", tagName);
        tagQuery.select("customer_id"); // 我们只关心 customer_id 这一列

        List<Long> customerIds = customerTagRelationService.list(tagQuery)
                .stream()
                .map(CustomerTagRelation::getCustomerId)
                .collect(Collectors.toList());

        if (customerIds.isEmpty()) {
            // 如果没有任何客户拥有这个标签，直接返回空列表，避免无效查询
            return new ArrayList<>();
        }

        // 2. 根据找到的 customer_id 列表，一次性查询出所有客户的详细信息
        return this.listByIds(customerIds);
    }
}
