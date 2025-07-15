package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundTransaction;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.vo.CustomerHoldingVO;


public interface CustomerHoldingService extends IService<CustomerHolding> {

    // 用于根据客户ID查询其所有持仓【优化为了4 综合根据多参数分页查询】
    List<CustomerHolding> listByCustomerId(Long customerId);

    // 为单个客户重新计算并更新所有持仓的方法【前端未采用 优化为了一键更新所有】
    boolean recalculateAndSaveHoldings(Long customerId);

    // 处理一笔新交易并自动更新持仓【交易发生后自动实现 但目前没有在系统里提供进行交易的前端接口 目前只用于批量导入交易数据时自动更新持仓数据】
    void updateHoldingAfterNewTransaction(FundTransaction transaction);

    /**
     *  根据条件分页查询持仓信息 用于pagecontroller里持仓管理界面的数据显示
     * @param page 分页对象
     * @param customerName 客户姓名 (模糊查询)
     * @param fundCode 基金代码 (模糊查询)
     * @return 包含持仓视图对象(VO)的分页结果
     */
    Page<CustomerHoldingVO> getHoldingPage(
            Page<CustomerHoldingVO> page, String customerName,
            String fundCode, String sortField, String sortOrder);


    /**
     * 获取单个客户市值排名前N的持仓详情
     * @param customerId 客户ID
     * @param limit 要获取的记录数
     * @return 包含持仓视图对象(VO)的列表
     */
    List<CustomerHoldingVO> getTopNHoldings(Long customerId, int limit);






}