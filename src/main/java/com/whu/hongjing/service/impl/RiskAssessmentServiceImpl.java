package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.RiskAssessmentMapper;
import com.whu.hongjing.pojo.entity.RiskAssessment;
import com.whu.hongjing.service.RiskAssessmentService;
import org.springframework.stereotype.Service;

@Service
public class RiskAssessmentServiceImpl extends ServiceImpl<RiskAssessmentMapper, RiskAssessment> implements RiskAssessmentService {
}