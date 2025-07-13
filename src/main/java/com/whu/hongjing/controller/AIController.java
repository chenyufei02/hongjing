// 文件位置: src/main/java/com/whu/hongjing/controller/AIController.java
package com.whu.hongjing.controller;

import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.vo.ApiResponseVO;
import com.whu.hongjing.service.AISuggestionService;
import com.whu.hongjing.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI助手", description = "提供基于大模型的智能分析与建议")
public class AIController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AISuggestionService aiSuggestionService;

    @PostMapping("/suggestion/{customerId}")
    @Operation(summary = "为指定客户生成营销建议")
    public ApiResponseVO<String> generateSuggestion(@PathVariable Long customerId) {
        Customer customer = customerService.getById(customerId);
        if (customer == null) {
            // 使用我们新的静态工厂方法，代码更简洁
            return ApiResponseVO.error("找不到该客户。");
        }

        try {
            String suggestion = aiSuggestionService.getMarketingSuggestion(customer);
            // 将AI生成的建议作为数据返回
            return ApiResponseVO.success("建议生成成功", suggestion);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponseVO.error("生成建议时发生后端错误：" + e.getMessage());
        }
    }
}