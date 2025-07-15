package com.whu.hongjing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO; // 1. 导入新的DTO
import com.whu.hongjing.pojo.entity.RiskAssessment;
import com.whu.hongjing.service.RiskAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/risk-assessment")
@Tag(name = "客户风险评估管理", description = "提供客户风险评估的记录和查询接口")
public class RiskAssessmentController {

    @Autowired
    private RiskAssessmentService riskAssessmentService;

// ======== 【因为提供了模拟数据一键自动化生成的方法，此处未直接提供前端实现】 =========
    @Operation(summary = "新增一条客户风险评估记录")
    @PostMapping("/add")
    public RiskAssessment addAssessment(@RequestBody @Validated RiskAssessmentSubmitDTO dto) {
        // 调用 Service 层中的 createAssessment 方法根据分数dto计算风险等级并封装进RiskAssessment对象进行创建
        return riskAssessmentService.createAssessment(dto);
    }


    @Operation(summary = "根据客户ID查询其所有风险评估记录")
    @GetMapping("/customer/{customerId}")
    public List<RiskAssessment> listByCustomerId(@PathVariable Long customerId) {
        QueryWrapper<RiskAssessment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("customer_id", customerId);
        queryWrapper.orderByDesc("assessment_date");
        return riskAssessmentService.list(queryWrapper);
    }

    @Operation(summary = "根据主键ID删除评估记录")
    @DeleteMapping("/delete/{id}")
    public boolean deleteAssessment(@PathVariable Long id) {
        return riskAssessmentService.removeById(id);
    }
}