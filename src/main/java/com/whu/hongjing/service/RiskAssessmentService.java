package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO;
import com.whu.hongjing.pojo.entity.RiskAssessment;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.vo.RiskAssessmentVO;

public interface RiskAssessmentService extends IService<RiskAssessment> {

    /**
     * 根据提交的DTO，创建一条新的风险评估记录。
     * 核心业务逻辑（根据分数计算等级）将在此方法的实现中完成。
     * @param dto 包含客户ID和评估分数的DTO
     * @return 创建好的、完整的风险评估实体
     */
    RiskAssessment createAssessment(RiskAssessmentSubmitDTO dto);

   /**
     * 根据条件分页查询风险评估记录, 增加排序功能
     * @param page 分页对象
     * @param customerName 客户姓名 (模糊查询)
     * @param riskLevel 风险等级 (精确查询)
     * @param sortField 排序字段 (customerId, riskScore, assessmentDate)
     * @param sortOrder 排序顺序 (asc, desc)
     * @return 包含风险评估视图对象(VO)的分页结果
     */
    Page<RiskAssessmentVO> getAssessmentPage(
            Page<RiskAssessmentVO> page, String customerName, String riskLevel,
            String actualRiskLevel, String riskDiagnosis, // <-- 新增参数
            String sortField, String sortOrder);
}