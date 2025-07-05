package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.enums.RiskLevelEnum; // 1. 导入我们创建的枚举类
import com.whu.hongjing.mapper.RiskAssessmentMapper;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO;
import com.whu.hongjing.pojo.entity.RiskAssessment;
import com.whu.hongjing.service.RiskAssessmentService;
import org.springframework.stereotype.Service;

@Service
public class RiskAssessmentServiceImpl extends ServiceImpl<RiskAssessmentMapper, RiskAssessment> implements RiskAssessmentService {

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
}