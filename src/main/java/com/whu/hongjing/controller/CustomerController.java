package com.whu.hongjing.controller;

//http://localhost:8080/v3/api-docs
import com.whu.hongjing.pojo.dto.CustomerDTO;
import com.whu.hongjing.pojo.dto.CustomerUpdateDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.vo.CustomerVO;
import com.whu.hongjing.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

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
    public boolean add(@RequestBody @Validated CustomerDTO dto) {
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
    public boolean updateCustomer(@RequestBody @Validated CustomerUpdateDTO dto) {
        Customer customer = new Customer();
        BeanUtils.copyProperties(dto, customer);
        return customerService.updateCustomer(customer);
    }

    @Operation(summary = "根据ID查询客户")
    @GetMapping("/{id}")
    public CustomerVO getCustomerById(@PathVariable Long id) {
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return null;
        }
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
        return vo;
    }

    @Operation(summary = "获取所有客户列表")
    @GetMapping("/list")
    public List<CustomerVO> getCustomerList() {
        List<Customer> customerList = customerService.getAllCustomers();
        return customerList.stream().map(customer -> {
            CustomerVO vo = new CustomerVO();
            BeanUtils.copyProperties(customer, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}