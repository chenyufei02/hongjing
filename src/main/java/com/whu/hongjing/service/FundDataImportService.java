package com.whu.hongjing.service;

/**
 * 基金数据导入服务
 * 负责从外部数据源获取所有基金的基本信息，并存入数据库
 */
public interface FundDataImportService {

    /**
     * 执行导入操作
     * @return 成功导入的基金数量
     */
    int importFundsFromDataSource();
}