package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.enums.RiskLevelEnum; // 1. 导入我们创建的枚举类
import com.whu.hongjing.mapper.RiskAssessmentMapper;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.RiskAssessment;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.RiskAssessmentService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.whu.hongjing.pojo.vo.RiskAssessmentVO;
import org.springframework.util.StringUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RiskAssessmentServiceImpl extends ServiceImpl<RiskAssessmentMapper, RiskAssessment> implements RiskAssessmentService {

    @Autowired
    private CustomerService customerService;

    @Override
    public RiskAssessment createAssessment(RiskAssessmentSubmitDTO dto) {
        // 2. 使用我们的枚举类，根据分数计算出风险等级
        RiskLevelEnum riskLevelEnum = RiskLevelEnum.getByScore(dto.getScore());

        // 3. 创建一个完整的、即将存入数据库的实体对象
        RiskAssessment assessment = new RiskAssessment();
        assessment.setCustomerId(dto.getCustomerId());
        assessment.setAssessmentDate(dto.getAssessmentDate());
        assessment.setRiskScore(dto.getScore());
        // 4. 从枚举实例中获取规范的等级名称并设置
        assessment.setRiskLevel(riskLevelEnum.getLevelName());

        // 5. 将填充完毕的实体对象保存到数据库
        this.save(assessment);

        // 6. 返回保存好的实体（它现在已经包含了数据库生成的ID）
        return assessment;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RiskAssessmentVO> getAssessmentPage(
            Page<RiskAssessmentVO> page, String customerName,
            String riskLevel, String sortField, String sortOrder)
    {
        // 步骤 1: 根据客户姓名查询匹配的客户ID
        List<Long> customerIds = null;
        if (StringUtils.hasText(customerName)) {
            customerIds = customerService.lambdaQuery()
                    .like(Customer::getName, customerName)
                    .list().stream()
                    .map(Customer::getId)
                    .collect(Collectors.toList());
            if (customerIds.isEmpty()) {
                return page.setRecords(Collections.emptyList());
            }
        }

        // 步骤 2: 构建对风险评估表的分页查询
        QueryWrapper<RiskAssessment> assessmentQueryWrapper = new QueryWrapper<>();
        if (customerIds != null) {
            assessmentQueryWrapper.in("customer_id", customerIds);
        }
        if (StringUtils.hasText(riskLevel)) {
            assessmentQueryWrapper.eq("risk_level", riskLevel);
        }

        //  排序逻辑
        if (StringUtils.hasText(sortField) && StringUtils.hasText(sortOrder)) {
            // 将前端传来的驼峰式字段名转换为数据库的下划线式列名
            // Mybatis-Plus的QueryWrapper可以直接使用实体类的属性名，它会自动映射
            String dbColumn;
            // 【关键】手动将前端传来的驼峰字段名，转换为数据库的下划线列名
            switch (sortField) {
                case "customerId":
                    dbColumn = "customer_id";
                    break;
                case "riskScore":
                    dbColumn = "risk_score";
                    break;
                case "assessmentDate":
                    dbColumn = "assessment_date";
                    break;
                default:
                    // 如果传入了未知的排序字段，则使用默认排序，防止SQL注入
                    dbColumn = "customer_id";
                    sortOrder = "asc";
            }

            if ("asc".equalsIgnoreCase(sortOrder)) {
                assessmentQueryWrapper.orderByAsc(dbColumn);
            } else {
                assessmentQueryWrapper.orderByDesc(dbColumn);
            }
        } else {
            // 默认排序
            assessmentQueryWrapper.orderByAsc("customer_id");
        }

        Page<RiskAssessment> assessmentPage = new Page<>(page.getCurrent(), page.getSize());
        this.page(assessmentPage, assessmentQueryWrapper);

        List<RiskAssessment> assessmentRecords = assessmentPage.getRecords();
        if (assessmentRecords.isEmpty()) {
            return page.setRecords(Collections.emptyList());
        }

        // 步骤 3: 批量获取关联的客户信息
        List<Long> resultCustomerIds = assessmentRecords.stream().map(RiskAssessment::getCustomerId).distinct().collect(Collectors.toList());
        Map<Long, String> customerIdToNameMap = customerService.listByIds(resultCustomerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));

        // 步骤 4: 组装最终的 RiskAssessmentVO 列表
        List<RiskAssessmentVO> voRecords = assessmentRecords.stream().map(assessment -> {
            RiskAssessmentVO vo = new RiskAssessmentVO();
            BeanUtils.copyProperties(assessment, vo);
            vo.setCustomerName(customerIdToNameMap.get(assessment.getCustomerId()));
            return vo;
        }).collect(Collectors.toList());

        // 步骤 5: 设置分页结果并返回
        page.setRecords(voRecords);
        page.setTotal(assessmentPage.getTotal());
        return page;
    }


}