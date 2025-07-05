package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO;
import com.whu.hongjing.pojo.entity.RiskAssessment;

public interface RiskAssessmentService extends IService<RiskAssessment> {

    /**
     * 根据提交的DTO，创建一条新的风险评估记录。
     * 核心业务逻辑（根据分数计算等级）将在此方法的实现中完成。
     * @param dto 包含客户ID和评估分数的DTO
     * @return 创建好的、完整的风险评估实体
     */
    RiskAssessment createAssessment(RiskAssessmentSubmitDTO dto);

}