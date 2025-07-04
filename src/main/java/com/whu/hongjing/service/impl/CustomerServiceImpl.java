package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerMapper;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import java.util.List;

@Service
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    @Autowired
    private CustomerMapper customerMapper;


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

    @Override
    public List<Customer> getAllCustomers() {
        return customerMapper.selectList(null);
    }

    @Override
    public boolean save(Customer entity) {
        return super.save(entity);
    }
}
