package com.whu.hongjing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.dto.CustomerUpdateDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.FundInfoService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.whu.hongjing.pojo.dto.CustomerDTO;
import java.util.List;

/**
 * 【新建】专门负责返回页面视图的控制器
 */
@Controller
public class PageController {

    // 注入所有为页面准备数据所需要的Service
    @Autowired
    private CustomerService customerService;
    @Autowired
    private FundInfoService fundInfoService;

    /**
     * 显示主页（工作台）
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("activeUri", "/");
        return "index";
    }

    /**
     * 显示客户列表页面 (将原来CustomerController里的页面逻辑搬到这里)
     */
    @GetMapping("/customer/list")
    public String customerList(Model model,
                               @RequestParam(value = "page", defaultValue = "1") int pageNum,
                               @RequestParam(value = "size", defaultValue = "10") int pageSize,
                               @RequestParam(value = "id", required = false) Long customerId,
                               @RequestParam(value = "name", required = false) String name,
                               @RequestParam(value = "idNumber", required = false) String idNumber,
                               @RequestParam(value = "tagName", required = false) String tagName) {

        Page<Customer> customerPage = new Page<>(pageNum, pageSize);
        customerService.getCustomerPage(customerPage, customerId, name, idNumber, tagName);

        int startPage = 1, endPage = (int) customerPage.getPages();
        if (customerPage.getPages() > 5) {
            startPage = Math.max(1, (int)customerPage.getCurrent() - 2);
            endPage = Math.min((int)customerPage.getPages(), startPage + 4);
            if (endPage == customerPage.getPages()) {
                startPage = Math.max(1, endPage - 4);
            }
        }

        model.addAttribute("customerPage", customerPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/customer/list");
        model.addAttribute("id", customerId);
        model.addAttribute("name", name);
        model.addAttribute("idNumber", idNumber);
        model.addAttribute("tagName", tagName);

        return "customer/list";
    }


    /**
     * 显示新增客户的表单页面
     */
    @GetMapping("/customer/add")
    public String showAddForm(Model model) {
        model.addAttribute("customerDTO", new CustomerDTO());
        model.addAttribute("activeUri", "/customer/list"); // 用于侧边栏高亮
        return "customer/add";
    }

    /**
     * 【新增】显示编辑客户的表单页面
     */
    @GetMapping("/customer/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        // 1. 根据ID从数据库查询客户数据
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            // 如果客户不存在，可以重定向到列表页
            return "redirect:/customer/list";
        }

        // 2. 将实体类数据复制到DTO中
        CustomerUpdateDTO customerUpdateDTO = new CustomerUpdateDTO();
        BeanUtils.copyProperties(customer, customerUpdateDTO);

        // 3. 将DTO放入Model中，用于前端表单回显
        model.addAttribute("customerUpdateDTO", customerUpdateDTO);
        model.addAttribute("activeUri", "/customer/list");

        // 4. 返回编辑页面的模板路径
        return "customer/edit";
    }







    /**
     * 显示基金信息列表页面
     */
    @GetMapping("/fund/list")
    public String fundList(Model model) {
        List<FundInfo> fundList = fundInfoService.list();
        model.addAttribute("funds", fundList);
        model.addAttribute("activeUri", "/fund/list");
        return "fund/list";
    }

    // 后续所有其他模块的列表、新增、编辑页面，我们都在这里继续添加方法...

}