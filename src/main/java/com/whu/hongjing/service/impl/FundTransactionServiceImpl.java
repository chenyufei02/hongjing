package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.exception.InsufficientFundsException;
import com.whu.hongjing.mapper.FundTransactionMapper;
import com.whu.hongjing.pojo.dto.FundPurchaseDTO;
import com.whu.hongjing.pojo.dto.FundRedeemDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.pojo.vo.FundTransactionVO;
import com.whu.hongjing.service.*;
import com.whu.hongjing.pojo.entity.FundInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * 基金交易服务实现类
 * 负责处理申购和赎回的核心业务逻辑
 */
@Service
public class FundTransactionServiceImpl extends ServiceImpl<FundTransactionMapper, FundTransaction> implements FundTransactionService {

    @Autowired
    @Lazy
    private TagRefreshService tagRefreshService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private FundInfoService fundInfoService;

    /**
     * 使用@Lazy注解懒加载客户持仓服务，以解决循环依赖问题
     */
    @Autowired
    @Lazy
    private CustomerHoldingService customerHoldingService;

    /**
     * 处理基金申购业务
     * @param dto 包含申购信息的DTO对象
     * @return 创建并保存好的交易记录实体
     */
    @Override
    @Transactional
    public FundTransaction createPurchaseTransaction(FundPurchaseDTO dto) {
        // 1. 从我们自己的数据库中，获取该基金最新的、可靠的净值
        // TODO 这里是昨日的收盘净值 后续可优化为消息队列在下午收盘更新完持仓以后再发生真实交易
        FundInfo fundInfo = fundInfoService.getById(dto.getFundCode());
        Assert.notNull(fundInfo, "找不到对应的基金信息：" + dto.getFundCode());
        Assert.notNull(fundInfo.getNetValue(), "该基金暂无有效的净值信息，无法交易。");

        // 取出前一日的净值
        BigDecimal sharePrice = fundInfo.getNetValue();

        // 2. 创建交易实体，并从DTO复制基础属性
        FundTransaction transaction = new FundTransaction();
        BeanUtils.copyProperties(dto, transaction);

        // 3. 填充申购业务特有的字段
        transaction.setTransactionType("申购");
        transaction.setSharePrice(sharePrice);

        transaction.setStatus("成功"); // 设置默认状态

        // 4. 根据申购金额和净值，计算出客户能购买到的份额
        BigDecimal shares = dto.getTransactionAmount().divide(sharePrice, 2, RoundingMode.DOWN);
        transaction.setTransactionShares(shares);
        // ------ 到此交易表保存完毕 ------

        // 5. 保存交易并触发持仓更新
        return saveTransactionAndUpdateHolding(transaction);
        // ------ 到此持仓表保存完毕 ------
    }

    /**
     * 处理基金赎回业务，包含核心的持仓校验逻辑
     * @param dto 包含赎回信息的DTO对象
     * @return 创建并保存好的交易记录实体
     */
    @Override
    @Transactional
    public FundTransaction createRedeemTransaction(FundRedeemDTO dto) {

        // a. 查询客户对该基金的当前持仓
        QueryWrapper<CustomerHolding> holdingQuery = new QueryWrapper<>();
        holdingQuery.eq("customer_id", dto.getCustomerId())
                    .eq("fund_code", dto.getFundCode());
        CustomerHolding currentHolding = customerHoldingService.getOne(holdingQuery);

        // b. 进行校验
        if (currentHolding == null || dto.getTransactionShares().compareTo(currentHolding.getTotalShares()) > 0) {
            // 如果持仓记录不存在，或者要赎回的份额 > 当前持有的总份额
            String availableShares = (currentHolding != null) ? currentHolding.getTotalShares().toPlainString() : "0";
            throw new InsufficientFundsException(
                "赎回失败：份额不足。当前持有 " + availableShares + " 份，尝试赎回 " + dto.getTransactionShares().toPlainString() + " 份。"
            );
        }

        // 2. 从数据库中，获取该基金最新的、可靠的净值
        FundInfo fundInfo = fundInfoService.getById(dto.getFundCode());
        Assert.notNull(fundInfo, "找不到对应的基金信息：" + dto.getFundCode());
        Assert.notNull(fundInfo.getNetValue(), "该基金暂无有效的净值信息，无法交易。");
        BigDecimal sharePrice = fundInfo.getNetValue();   // 采用模拟的按前一天收盘的净值赎回

        // 3. 创建交易实体
        FundTransaction transaction = new FundTransaction();
        BeanUtils.copyProperties(dto, transaction);
        transaction.setTransactionType("赎回");
        transaction.setSharePrice(sharePrice);
        transaction.setStatus("成功");

        // 4. 计算赎回金额
        BigDecimal amount = dto.getTransactionShares().multiply(sharePrice).setScale(2, RoundingMode.HALF_UP);
        transaction.setTransactionAmount(amount);

        // 5. 保存交易并触发持仓更新
        return saveTransactionAndUpdateHolding(transaction);
    }


    /**
     * 私有辅助方法，用于统一处理保存交易和更新持仓的逻辑
     * @param transaction 已经构建好的交易实体
     * @return 保存后的交易实体（包含数据库生成的ID）
     */
    private FundTransaction saveTransactionAndUpdateHolding(FundTransaction transaction) {
        // 步骤1：将交易记录保存到数据库
        this.save(transaction);
        // 步骤2：调用客户持仓服务，根据这笔新交易实时更新持仓信息
        customerHoldingService.updateHoldingAfterNewTransaction(transaction);

        // 立即为该客户刷新标签
        System.out.println("【实时刷新】交易完成，触发客户 " + transaction.getCustomerId() + " 的标签刷新...");
        tagRefreshService.refreshTagsForCustomer(transaction.getCustomerId());
        System.out.println("【实时刷新】客户 " + transaction.getCustomerId() + " 的标签已刷新完毕！");

        // 步骤3：返回包含ID的完整交易实体
        return transaction;
    }


    /**
     * 分页查询
     * @param page customerName, fundCode, transactionType]
     * @return com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.whu.hongjing.pojo.vo.FundTransactionVO>
     * @author yufei
     * @since 2025/7/9
     */
    @Override
    @Transactional(readOnly = true)
    public Page<FundTransactionVO> getTransactionPage(
            Page<FundTransactionVO> page, String customerName,
            String fundCode, String transactionType, String sortField,
            String sortOrder)
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

        // 步骤 2: 构建对交易表的分页查询
        QueryWrapper<FundTransaction> transactionQueryWrapper = new QueryWrapper<>();
        if (customerIds != null) {
            transactionQueryWrapper.in("customer_id", customerIds);
        }
        if (StringUtils.hasText(fundCode)) {
            transactionQueryWrapper.like("fund_code", fundCode);
        }
        if (StringUtils.hasText(transactionType)) {
            transactionQueryWrapper.eq("transaction_type", transactionType);
        }

        // 【 排序逻辑 】
        if (StringUtils.hasText(sortField) && StringUtils.hasText(sortOrder)) {
            String dbColumn;
            // 手动将前端传来的驼峰字段名，转换为数据库的下划线列名
            switch (sortField) {
                case "customerId":
                    dbColumn = "customer_id";
                    break;
                case "transactionAmount":
                    dbColumn = "transaction_amount";
                    break;
                default:
                    dbColumn = "transaction_time";
                    sortOrder = "desc";
            }

            if ("asc".equalsIgnoreCase(sortOrder)) {
                transactionQueryWrapper.orderByAsc(dbColumn);
            } else {
                transactionQueryWrapper.orderByDesc(dbColumn);
            }
        } else {
            // 默认排序
            transactionQueryWrapper.orderByDesc("transaction_time");
        }

        Page<FundTransaction> transactionPage = new Page<>(page.getCurrent(), page.getSize());
        this.page(transactionPage, transactionQueryWrapper);  // 根据查询规则 返回指定页面 以及总记录数和总页数

        List<FundTransaction> transactionRecords = transactionPage.getRecords();  // 页面里的信息放到列表供进一步操作

        if (transactionRecords.isEmpty()) { return page.setRecords(Collections.emptyList()); }

        // 步骤 3: 批量获取关联的客户和基金信息
            // 根据模糊匹配名字得到的数据找不重复的客户和基金ID
        List<Long> resultCustomerIds = transactionRecords.stream().map(FundTransaction::getCustomerId).distinct().collect(Collectors.toList());
        List<String> resultFundCodes = transactionRecords.stream().map(FundTransaction::getFundCode).distinct().collect(Collectors.toList());
            // 将不重复的客户和基金ID映射到姓名 供后续返回VO对象的时候好根据ID找到姓名
        Map<Long, String> customerIdToNameMap = customerService.listByIds(resultCustomerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Customer::getName));
        Map<String, String> fundCodeToNameMap = fundInfoService.listByIds(resultFundCodes).stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, FundInfo::getFundName));

        // 步骤 4: 组装最终的 FundTransactionVO 列表
        List<FundTransactionVO> voRecords = transactionRecords.stream().map(transactionrecords -> {

            FundTransactionVO vo = new FundTransactionVO();

            // 复制所有交易信息
            BeanUtils.copyProperties(transactionrecords, vo);   // 将已经找到的交易页的信息（转换存进了transactionRecords列表对象） 全部拷贝到VO对象

            // 然后再向VO对象里填充关联的名称信息
            vo.setCustomerName(customerIdToNameMap.get(transactionrecords.getCustomerId()));
            vo.setFundName(fundCodeToNameMap.get(transactionrecords.getFundCode()));
            return vo;
        }).collect(Collectors.toList());  //

        // 步骤 5: 设置分页结果并返回
        page.setRecords(voRecords);
        page.setTotal(transactionPage.getTotal());
        return page;
    }





}