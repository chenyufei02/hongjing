package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.FundInfoMapper;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.FundInfoService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FundInfoServiceImpl extends ServiceImpl<FundInfoMapper, FundInfo> implements FundInfoService {

    @Override
    public Page<FundInfo> getFundInfoPage(Page<FundInfo> page, String fundCode, String fundName, String fundType, Integer riskScore) {
        QueryWrapper<FundInfo> queryWrapper = new QueryWrapper<>();

        // 构建动态查询条件
        if (StringUtils.hasText(fundCode)) {
            queryWrapper.like("fund_code", fundCode);
        }
        if (StringUtils.hasText(fundName)) {
            queryWrapper.like("fund_name", fundName);
        }
        if (StringUtils.hasText(fundType)) {
            queryWrapper.eq("fund_type", fundType);
        }
        if (riskScore != null) {
            queryWrapper.eq("risk_score", riskScore);
        }

        // 默认按基金代码升序排序
        queryWrapper.orderByAsc("fund_code");

        return this.page(page, queryWrapper);
    }

}