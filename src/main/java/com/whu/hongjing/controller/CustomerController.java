package com.whu.hongjing.controller;

import com.whu.hongjing.pojo.dto.CustomerAddDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户管理接口
 */
@RestController
@RequestMapping("/customer")
@Tag(name = "客户管理", description = "客户相关增删改查接口")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Operation(summary = "新增客户")
    @PostMapping("/add")
    public boolean add(@RequestBody CustomerAddDTO dto) {
        Customer customer = new Customer();
        BeanUtils.copyProperties(dto, customer);
        return customerService.save(customer);
    }


    @Operation(summary = "根据ID删除客户")
    @DeleteMapping("/delete/{id}")
    public boolean deleteCustomer(@PathVariable Long id) {
        return customerService.removeCustomer(id);
    }

    @Operation(summary = "更新客户信息")
    @PutMapping("/update")
    public boolean updateCustomer(@RequestBody Customer customer) {
        return customerService.updateCustomer(customer);
    }

    @Operation(summary = "根据ID查询客户")
    @GetMapping("/{id}")
    public Customer getCustomerById(@PathVariable Long id) {
        return customerService.getCustomerById(id);
    }

    @Operation(summary = "获取所有客户列表")
    @GetMapping("/list")
    public List<Customer> getCustomerList() {
        return customerService.getAllCustomers();
    }
}
