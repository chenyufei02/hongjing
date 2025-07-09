package com.whu.hongjing.controller;

//http://localhost:8080/v3/api-docs
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.constants.TaggingConstants;
import com.whu.hongjing.pojo.dto.CustomerDTO;
import com.whu.hongjing.pojo.dto.CustomerUpdateDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.vo.CustomerVO;
import com.whu.hongjing.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.whu.hongjing.pojo.vo.CustomerTagVO;
import com.whu.hongjing.service.CustomerTagService;
import com.whu.hongjing.service.CustomerTagRelationService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;


/**
 * 客户管理接口
 */
@Controller
@RequestMapping("/customer")
@Tag(name = "客户管理", description = "客户相关增删改查接口")
public class CustomerController {

    @Autowired
    private CustomerTagService customerTagService; // 注入标签服务
    @Autowired
    private CustomerService customerService;
    @Autowired
    private CustomerTagRelationService customerTagRelationService;

    /**
     * 显示客户详情页面
     */
    @Operation(summary = "查询客户详情及其标签")
    @GetMapping("/detail/{id}")
    public String showCustomerDetails(@PathVariable Long id, Model model) {
        // 1. 获取客户基础信息
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return "redirect:/customer/list";
        }

        // 2. 获取该客户的所有标签
        QueryWrapper<CustomerTagRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("customer_id", id);
        List<CustomerTagRelation> tags = customerTagRelationService.list(queryWrapper);

        // 3. 【核心】定义标签类别的排序规则
        // 我们使用 TaggingConstants 中的常量来避免硬编码
        final Map<String, Integer> categoryOrder = Map.of(
            TaggingConstants.CATEGORY_AGE, 1,
            TaggingConstants.CATEGORY_GENDER, 2,
            TaggingConstants.CATEGORY_OCCUPATION, 3,
            TaggingConstants.CATEGORY_STYLE, 4,
            TaggingConstants.CATEGORY_RISK_DECLARED, 5,
            TaggingConstants.CATEGORY_RISK_ACTUAL, 6,
            TaggingConstants.CATEGORY_RISK_DIAGNOSIS, 7,
            TaggingConstants.CATEGORY_ASSET, 8,
            TaggingConstants.CATEGORY_RECENCY, 9,
            TaggingConstants.CATEGORY_FREQUENCY, 10
        );

        // 4. 【核心】根据定义的顺序对标签列表进行排序
        tags.sort(Comparator.comparing(tag -> categoryOrder.getOrDefault(tag.getTagCategory(), 99)));

        // 5. 将数据放入Model
        model.addAttribute("customer", customer);
        model.addAttribute("tags", tags); // 此时传递给前端的tags列表已经是排好序的
        model.addAttribute("activeUri", "/customer/list");

        return "customer/detail";
    }





    /**
     *   处理新增客户的表单提交 定向跳转到新增用户的表单页面 保存成功后重新定向到客户列表页面
     */
    @Operation(summary = "新增客户")
    @PostMapping("/add")
    public String add(@ModelAttribute @Validated CustomerDTO dto, BindingResult result, Model model) {
        // 【新增】检查是否存在校验错误
        if (result.hasErrors()) {
            // 如果有错误，则重新返回到新增页面
            // Spring会自动将dto和result对象（包含错误信息）放回到Model中
            model.addAttribute("activeUri", "/customer/list"); // 确保侧边栏高亮正确
            return "customer/add"; // 直接返回视图，而不是重定向
        }

        // 如果没有错误，则执行保存操作
        Customer customer = new Customer();
        BeanUtils.copyProperties(dto, customer);
        customerService.save(customer);

        // 保存成功后，重定向到客户列表页面
        return "redirect:/customer/list";
    }

    /**
     * 显示新增客户后自动跳转到的表单页面
     */
    @Operation(summary = "显示新增客户页面")
    @GetMapping("/add")
    public String showAddForm(Model model) {
        // 向模型中放入一个新的、空的CustomerDTO对象，用于表单数据绑定
        model.addAttribute("customerDTO", new CustomerDTO());
        // 设置侧边栏高亮
        model.addAttribute("activeUri", "/customer/list");
        // 返回新增页面的模板路径
        return "customer/add";
    }



    @Operation(summary = "根据ID删除客户")
    // 这里用 PostMapping 或者 GetMapping，因为要返回的HTML的普通链接不支持DELETE方法
    @PostMapping("/delete/{id}")
    public String deleteCustomer(@PathVariable Long id) {
        customerService.removeCustomer(id);
        // 删除后重定向回列表页
        return "redirect:/customer/list";
    }



    @Operation(summary = "更新客户信息")
    @PostMapping("/update")
    public String updateCustomer(@ModelAttribute @Validated CustomerUpdateDTO dto, BindingResult result, Model model) {
        // 检查校验结果
        if (result.hasErrors()) {
            // 如果有错误，则重新返回到编辑页面
            // 注意：这里要用 dto 的名字 "customerUpdateDTO" 传回去，才能和 edit.html 里的 th:object 对应上
            model.addAttribute("customerUpdateDTO", dto);
            model.addAttribute("activeUri", "/customer/list");
            return "customer/edit";
        }

        Customer customer = new Customer();
        BeanUtils.copyProperties(dto, customer);
        customerService.updateCustomer(customer);

        // 更新成功后，重定向到客户列表页面
        return "redirect:/customer/list";
    }

    /**
     * 显示编辑客户的表单页面
     */
    @Operation(summary = "显示编辑客户页面")
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        // 1. 根据ID从数据库查询客户数据
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            // 如果客户不存在，可以重定向到列表页或显示一个错误页
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





    @Operation(summary = "根据ID查询客户")
    @GetMapping("/{id}")
    @ResponseBody
    public CustomerVO getCustomerById(@PathVariable Long id) {
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return null;
        }
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
        return vo;
    }

    @Operation(summary = "根据身份证号码查询客户")
    @GetMapping("/search") // 我们使用 /search 路径
    @ResponseBody
    public CustomerVO getCustomerByIdNumber(@RequestParam String idNumber) {
        Customer customer = customerService.getCustomerByIdNumber(idNumber);
        if (customer == null) {
            return null;
        }
        CustomerVO vo = new CustomerVO();
        BeanUtils.copyProperties(customer, vo);
        return vo;
    }

    @Operation(summary = "根据姓名查询客户列表（支持重名）")
    @GetMapping("/search-by-name") // 使用新路径 /search-by-name
    @ResponseBody
    public List<CustomerVO> getCustomersByName(@RequestParam String name) {
        List<Customer> customers = customerService.getCustomersByName(name);
        // 使用Stream API将List<Customer>转换为List<CustomerVO>
        return customers.stream().map(customer -> {
            CustomerVO vo = new CustomerVO();
            BeanUtils.copyProperties(customer, vo);
            return vo;
        }).collect(Collectors.toList());
    }




    @Operation(summary = "分页获取所有客户列表页面")
    @GetMapping("/list")
    public String getCustomerList(
            Model model,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "size", defaultValue = "10") int pageSize
    ) {
        // 1. 创建并查询分页数据（这部分保持不变）
        Page<Customer> customerPage = new Page<>(pageNum, pageSize);
        customerService.getCustomerPage(customerPage);

        // 2. 【新增】计算分页导航条的起始页和结束页
        int startPage;
        int endPage;
        int current = (int) customerPage.getCurrent();
        int total = (int) customerPage.getPages();

        if (total <= 5) {
            // 如果总页数小于等于5，则全部显示
            startPage = 1;
            endPage = total;
        } else {
            // 如果总页数大于5，则根据当前页计算
            startPage = Math.max(1, current - 2);
            endPage = Math.min(total, current + 2);
            // 处理边界情况
            if (endPage - startPage < 4) {
                if (startPage == 1) {
                    endPage = 5;
                } else if (endPage == total) {
                    startPage = total - 4;
                }
            }
        }

        // 3. 将所有需要的数据放入Model
        model.addAttribute("customerPage", customerPage);
        model.addAttribute("startPage", startPage); // 新增起始页
        model.addAttribute("endPage", endPage);     // 新增结束页
        model.addAttribute("activeUri", "/customer/list");

        // 4. 返回视图名称
        return "customer/list";
    }


    @Operation(summary = "根据客户ID获取其画像标签")
    @GetMapping("/{id}/tags")
    @ResponseBody
    public List<CustomerTagVO> getCustomerTags(@PathVariable Long id) {
        return customerTagService.getTagsByCustomerId(id);
    }

    @Operation(summary = "根据标签筛选客户列表")
    @GetMapping("/by-tag")
    @ResponseBody
    public List<CustomerVO> listCustomersByTag(@RequestParam String tagName) {
        List<Customer> customers = customerService.getCustomersByTag(tagName);
        // 将 List<Customer> 转换为 List<CustomerVO>
        return customers.stream().map(customer -> {
            CustomerVO vo = new CustomerVO();
            BeanUtils.copyProperties(customer, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}