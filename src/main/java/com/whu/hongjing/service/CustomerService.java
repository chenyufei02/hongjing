package com.whu.hongjing.service;

import com.whu.hongjing.pojo.entity.Customer;
import java.util.List;

public interface CustomerService {
    boolean removeCustomer(Long id);
    boolean updateCustomer(Customer customer);
    Customer getCustomerById(Long id);
    List<Customer> getAllCustomers();

    boolean save(Customer customer);
}
