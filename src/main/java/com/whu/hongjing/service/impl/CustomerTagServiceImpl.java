package com.whu.hongjing.service.impl;

import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.vo.CustomerTagVO;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.CustomerTagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
public class CustomerTagServiceImpl implements CustomerTagService {

    @Autowired
    private CustomerService customerService;

    @Override
    public List<CustomerTagVO> getTagsByCustomerId(Long customerId) {
        // 1. 获取客户的基础信息
        Customer customer = customerService.getCustomerById(customerId);
        if (customer == null) {
            return new ArrayList<>(); // 如果客户不存在，返回空列表
        }

        List<CustomerTagVO> tags = new ArrayList<>();

        // === 2. 开始计算各种标签 ===

        // a. 计算年龄标签
        addAgeTag(tags, customer.getBirthDate());

        // b. 计算性别标签
        tags.add(new CustomerTagVO(customer.getGender()));

        // c. (可以在这里添加更多标签的计算，比如风险等级、资产规模等)

        return tags;
    }

    /**
     * 私有辅助方法：根据出生日期计算年龄标签
     */
    private void addAgeTag(List<CustomerTagVO> tags, LocalDate birthDate) {
        if (birthDate == null) {
            return;
        }
        int age = Period.between(birthDate, LocalDate.now()).getYears();
        String ageTag;
        if (age >= 60) {
            ageTag = "60后及以上";
        } else if (age >= 50) {
            ageTag = "70后";
        } else if (age >= 40) {
            ageTag = "80后";
        } else if (age >= 30) {
            ageTag = "90后";
        } else if (age >= 20) {
            ageTag = "00后";
        } else {
            ageTag = "10后";
        }
        tags.add(new CustomerTagVO(ageTag));
        tags.add(new CustomerTagVO(age + "岁")); // 也可以添加一个具体的年龄标签
    }
}