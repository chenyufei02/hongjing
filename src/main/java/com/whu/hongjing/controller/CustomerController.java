package com.whu.hongjing.controller;

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
import com.whu.hongjing.pojo.vo.CustomerTagVO;
import com.whu.hongjing.service.CustomerTagService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 【最终版】客户管理API控制器
 * 它的职责是提供所有与客户相关的、返回JSON数据格式的接口。
 * 注意：所有返回页面的逻辑都已移至 PageController.java。
 */
@RestController // 【关键修改】使用 @RestController，表明此类所有方法都返回数据，而非视图。
@RequestMapping("/api/customer") // 【关键修改】为所有API路径统一添加 /api 前缀，方便管理和部署。
@Tag(name = "客户管理API", description = "客户相关增删改查的数据接口")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerTagService customerTagService;

    /**
     * 【保留】处理新增客户的API。
     * 前端将通过JS调用此接口。
     * @param dto 包含新客户信息的DTO对象
     * @return 操作是否成功
     */
    @Operation(summary = "新增客户")
    @PostMapping("/add")
    public boolean add(@RequestBody @Validated CustomerDTO dto) {
        Customer customer = new Customer();
        BeanUtils.copyProperties(dto, customer);
        return customerService.save(customer);
    }

    /**
     * 【保留】处理删除客户的API。
     * @param id 要删除的客户ID
     * @return 操作是否成功
     */
    @Operation(summary = "根据ID删除客户")
    @DeleteMapping("/delete/{id}")
    public boolean deleteCustomer(@PathVariable Long id) {
        return customerService.removeCustomer(id);
    }

    /**
     * 【保留】处理更新客户信息的API。
     * @param dto 包含客户更新信息的DTO对象
     * @return 操作是否成功
     */
    @Operation(summary = "更新客户信息")
    @PutMapping("/update")
    public boolean updateCustomer(@RequestBody @Validated CustomerUpdateDTO dto) {
        Customer customer = new Customer();
        BeanUtils.copyProperties(dto, customer);
        return customerService.updateCustomer(customer);
    }

    /**
     * 【保留】根据ID查询单个客户信息的API。
     * @param id 客户ID
     * @return 客户信息视图对象
     */
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

    /**
     * 【保留】根据身份证号码查询客户的API。
     * @param idNumber 身份证号码
     * @return 客户信息视图对象
     */
    @Operation(summary = "根据身份证号码查询客户")
    @GetMapping("/search")
    public CustomerVO getCustomerByIdNumber(@RequestParam String idNumber) {
        Customer customer = customerService.getCustomerByIdNumber(idNumber);
        if (customer == null) {
            return null;
        }
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
        return vo;
    }

    /**
     * 【保留】根据姓名查询客户列表的API。
     * @param name 客户姓名
     * @return 客户信息视图对象列表
     */
    @Operation(summary = "根据姓名查询客户列表（支持重名）")
    @GetMapping("/search-by-name")
    public List<CustomerVO> getCustomersByName(@RequestParam String name) {
        List<Customer> customers = customerService.getCustomersByName(name);
        return customers.stream().map(customer -> {
            CustomerVO vo = new CustomerVO();
            BeanUtils.copyProperties(customer, vo);
            return vo;
        }).collect(Collectors.toList());
    }


    /**
     * 【保留】根据客户ID获取其画像标签的API。
     * @param id 客户ID
     * @return 标签列表
     */
    @Operation(summary = "根据客户ID获取其画像标签")
    @GetMapping("/{id}/tags")
    public List<CustomerTagVO> getCustomerTags(@PathVariable Long id) {
        return customerTagService.getTagsByCustomerId(id);
    }

    /**
     * 【保留】根据标签筛选客户列表的API。
     * @param tagName 标签名称
     * @return 客户信息视图对象列表
     */
    @Operation(summary = "根据标签筛选客户列表")
    @GetMapping("/by-tag")
    public List<CustomerVO> listCustomersByTag(@RequestParam String tagName) {
        List<Customer> customers = customerService.getCustomersByTag(tagName);
        return customers.stream().map(customer -> {
            CustomerVO vo = new CustomerVO();
            BeanUtils.copyProperties(customer, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}