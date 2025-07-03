package com.whu.hongjing.controller;

import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.pojo.entity.Customer;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户信息管理控制器
 */
@RestController
@RequestMapping("/customer")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Operation(summary = "获取所有客户列表")
    @GetMapping("/list")
    public List<Customer> listAll() {
        return customerService.list();
    }

    @Operation(summary = "新增客户")
    @PostMapping("/add")
    public boolean add(@RequestBody Customer customer) {
        return customerService.save(customer);
    }

    @Operation(summary = "删除客户")
    @DeleteMapping("/delete/{id}")
    public boolean delete(@PathVariable Long id) {
        return customerService.removeById(id);
    }

    @Operation(summary = "根据ID更新客户")
    @PutMapping("/update")
    public boolean update(@RequestBody Customer customer) {
        return customerService.updateById(customer);
    }
}
