package com.whu.hongjing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.hongjing.pojo.dto.RiskAssessmentDTO;
import com.whu.hongjing.pojo.entity.RiskAssessment;
import com.whu.hongjing.service.RiskAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/risk-assessment")
@Tag(name = "客户风险评估管理", description = "提供客户风险评估的增删改查接口")
public class RiskAssessmentController {

    @Autowired
    private RiskAssessmentService riskAssessmentService;

    @Operation(summary = "新增一条客户风险评估记录")
    @PostMapping("/add")
    public boolean addAssessment(@RequestBody @Validated RiskAssessmentDTO dto) {
        RiskAssessment assessment = new RiskAssessment();
        BeanUtils.copyProperties(dto, assessment);
        return riskAssessmentService.save(assessment);
    }

    @Operation(summary = "根据客户ID查询其所有风险评估记录")
    @GetMapping("/customer/{customerId}")
    public List<RiskAssessment> listByCustomerId(@PathVariable Long customerId) {
        // 使用QueryWrapper来构建查询条件
        QueryWrapper<RiskAssessment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("customer_id", customerId);
        queryWrapper.orderByDesc("assessment_date"); // 按评估日期降序排序，最新的在前
        return riskAssessmentService.list(queryWrapper);
    }

    @Operation(summary = "根据主键ID删除评估记录(通常不使用)")
    @DeleteMapping("/delete/{id}")
    public boolean deleteAssessment(@PathVariable Long id) {
        return riskAssessmentService.removeById(id);
    }
}