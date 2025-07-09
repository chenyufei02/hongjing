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
import org.springframework.util.StringUtils;


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


    /**
     * 分页查询客户列表的实现，增加了动态条件查询和稳定的排序
     */
    @Override
    public Page<Customer> getCustomerPage(
            Page<Customer> page, Long customerId,
            String name, String idNumber, String tagName)
    {
        QueryWrapper<Customer> queryWrapper = new QueryWrapper<>();

        // 处理按客户ID的精确查询
        // 如果传入了 customerId，则其他所有条件都忽略，只按ID查询
        if (customerId != null) {
            queryWrapper.eq("id", customerId);
            return this.page(page, queryWrapper);
        }

        // --- 如果没有传入ID，则处理其他模糊查询和标签查询 ---

        if (StringUtils.hasText(tagName)) {
            QueryWrapper<CustomerTagRelation> tagQuery = new QueryWrapper<>();
            tagQuery.eq("tag_name", tagName).select("customer_id");
            List<Long> customerIds = customerTagRelationService.list(tagQuery)
                    .stream()
                    .map(CustomerTagRelation::getCustomerId)
                    .collect(Collectors.toList());
            if (customerIds.isEmpty()) {
                return new Page<>(page.getCurrent(), page.getSize(), 0);
            }
            queryWrapper.in("id", customerIds);
        }

        if (StringUtils.hasText(name)) {
            queryWrapper.like("name", name);
        }
        if (StringUtils.hasText(idNumber)) {
            queryWrapper.like("id_number", idNumber);
        }

        queryWrapper.orderByAsc("id");

        return this.page(page, queryWrapper);
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
