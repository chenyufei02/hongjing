package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.constants.TaggingConstants;
import com.whu.hongjing.enums.RiskLevelEnum;
import com.whu.hongjing.mapper.RiskAssessmentMapper;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.entity.RiskAssessment;
import com.whu.hongjing.pojo.vo.RiskAssessmentVO;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.CustomerTagRelationService;
import com.whu.hongjing.service.RiskAssessmentService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RiskAssessmentServiceImpl extends ServiceImpl<RiskAssessmentMapper, RiskAssessment> implements RiskAssessmentService {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerTagRelationService customerTagRelationService;

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

    /**
     * 【最终完整版】同时支持多维度复杂查询与动态排序
     */
    @Override
    @Transactional(readOnly = true)
    public Page<RiskAssessmentVO> getAssessmentPage(
            Page<RiskAssessmentVO> page, String customerName, String riskLevel,
            String actualRiskLevel, String riskDiagnosis,
            String sortField, String sortOrder)
    {
        // 步骤 1: 基于所有筛选条件（客户姓名、实盘风险、风险诊断），预先筛选出符合条件的客户ID集合。
        Set<Long> customerIdsToFilter = null;

        // a. 如果有按“实盘风险”或“风险诊断”这两个标签的筛选条件
        if (StringUtils.hasText(actualRiskLevel) || StringUtils.hasText(riskDiagnosis)) {
            customerIdsToFilter = customerTagRelationService.findCustomerIdsByTags(
                    Map.of(
                        TaggingConstants.CATEGORY_RISK_ACTUAL, actualRiskLevel,
                        TaggingConstants.CATEGORY_RISK_DIAGNOSIS, riskDiagnosis
                    )
            );
            if (customerIdsToFilter.isEmpty()) {
                page.setRecords(Collections.emptyList());
                page.setTotal(0);
                return page;
            }
        }

        // b. 如果有按客户姓名的筛选条件
        if (StringUtils.hasText(customerName)) {
            Set<Long> idsByName = customerService.lambdaQuery()
                    .like(Customer::getName, customerName)
                    .list().stream().map(Customer::getId).collect(Collectors.toSet());

            if (customerIdsToFilter == null) {
                customerIdsToFilter = idsByName;
            } else {
                customerIdsToFilter.retainAll(idsByName); // 取交集
            }

            if (customerIdsToFilter.isEmpty()) {
                page.setRecords(Collections.emptyList());
                page.setTotal(0);
                return page;
            }
        }

        // 步骤 2: 构建对 risk_assessment 表的主查询
        QueryWrapper<RiskAssessment> assessmentQueryWrapper = new QueryWrapper<>();

        // a. 应用上面预筛选出的客户ID集合作为查询条件
        if (customerIdsToFilter != null) {
            assessmentQueryWrapper.in("customer_id", customerIdsToFilter);
        }

        // b. 应用对“申报风险”的筛选（这是表内字段）
        if (StringUtils.hasText(riskLevel)) {
            assessmentQueryWrapper.eq("risk_level", riskLevel);
        }

        // c. 【【【 关键修复：恢复动态排序逻辑 】】】
        if (StringUtils.hasText(sortField)) {
            String dbColumn;
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
                    dbColumn = "assessment_date";
                    sortOrder = "desc";
            }
            if ("asc".equalsIgnoreCase(sortOrder)) {
                assessmentQueryWrapper.orderByAsc(dbColumn);
            } else {
                assessmentQueryWrapper.orderByDesc(dbColumn);
            }
        } else {
            assessmentQueryWrapper.orderByDesc("assessment_date"); // 默认按评估日期降序
        }

        // 步骤 3: 执行分页查询，此时查询结果是已经排序好的
        Page<RiskAssessment> assessmentPage = new Page<>(page.getCurrent(), page.getSize());
        this.page(assessmentPage, assessmentQueryWrapper);

        List<RiskAssessment> assessmentRecords = assessmentPage.getRecords();
        if (assessmentRecords.isEmpty()) {
            return page.setRecords(Collections.emptyList());
        }

        // 步骤 4: 批量获取关联数据（客户姓名和标签），用于填充VO
        List<Long> resultCustomerIds = assessmentRecords.stream()
                .map(RiskAssessment::getCustomerId).distinct().collect(Collectors.toList());

        Map<Long, String> customerIdToNameMap = customerService.listByIds(resultCustomerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));

        List<String> targetCategories = List.of(TaggingConstants.CATEGORY_RISK_ACTUAL, TaggingConstants.CATEGORY_RISK_DIAGNOSIS);
        List<CustomerTagRelation> relatedTags = customerTagRelationService.lambdaQuery()
                .in(CustomerTagRelation::getCustomerId, resultCustomerIds)
                .in(CustomerTagRelation::getTagCategory, targetCategories)
                .list();
        Map<Long, Map<String, String>> customerTagsMap = relatedTags.stream()
                .collect(Collectors.groupingBy(
                        CustomerTagRelation::getCustomerId,
                        Collectors.toMap(CustomerTagRelation::getTagCategory, CustomerTagRelation::getTagName, (t1, t2) -> t1)
                ));

        // 步骤 5: 组装最终的VO列表
        List<RiskAssessmentVO> voRecords = assessmentRecords.stream().map(assessment -> {
            RiskAssessmentVO vo = new RiskAssessmentVO();
            BeanUtils.copyProperties(assessment, vo);
            vo.setCustomerName(customerIdToNameMap.get(assessment.getCustomerId()));
            Map<String, String> tagsForCustomer = customerTagsMap.get(assessment.getCustomerId());
            if (tagsForCustomer != null) {
                vo.setActualRiskLevel(tagsForCustomer.get(TaggingConstants.CATEGORY_RISK_ACTUAL));
                vo.setRiskDiagnosis(tagsForCustomer.get(TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
            }
            return vo;
        }).collect(Collectors.toList());

        // 设置并返回最终的分页结果
        page.setRecords(voRecords);
        page.setTotal(assessmentPage.getTotal());
        return page;
    }
}