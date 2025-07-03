package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerMapper;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public List<Customer> getAllCustomers() {
        return customerMapper.selectList(null);
    }

    @Override
    public boolean save(Customer entity) {
        return super.save(entity);
    }
}
