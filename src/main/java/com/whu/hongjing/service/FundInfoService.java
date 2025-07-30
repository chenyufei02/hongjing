package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.FundInfo;

public interface FundInfoService extends IService<FundInfo> {

    /**
     * 根据条件分页查询基金信息
     * @param page 分页对象
     * @param fundCode 基金代码（模糊查询）
     * @param fundName 基金名称（模糊查询）
     * @param fundType 基金类型（精确查询）
     * @param riskScore 风险分数（精确查询）
     * @return 包含查询结果的分页对象
     */
     Page<FundInfo> getFundInfoPage(
             Page<FundInfo> page,
             String fundCode,
             String fundName,
             String fundType,
             Integer riskScore);
}