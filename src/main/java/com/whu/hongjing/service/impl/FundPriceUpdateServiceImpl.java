package com.whu.hongjing.service.impl;

import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.FundInfoService;
import com.whu.hongjing.service.FundPriceUpdateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 基金净值更新服务实现类 (纯本地模拟模式)
 * 职责：为数据库中所有基金生成一个在小范围内随机波动的新净值。
 * 此模式不依赖任何外部网络请求，确保了速度和100%的成功率。
 */
@Service
public class FundPriceUpdateServiceImpl implements FundPriceUpdateService {

    @Autowired
    private FundInfoService fundInfoService;

    // 创建一个可复用的Random实例
    private final Random random = new Random();

    @Override
    public int updateAllFundPrices() {
        System.out.println("【定时任务】启动纯本地高速模拟模式...");

        // 1. 从数据库获取所有需要更新的基金列表
        List<FundInfo> allFundsInDb = fundInfoService.list();

        if (allFundsInDb == null || allFundsInDb.isEmpty()) {
            System.out.println("【定时任务】数据库中没有基金信息，无需模拟。");
            return 0;
        }

        List<FundInfo> fundsToUpdate = new ArrayList<>();

        // 2. 遍历每一只基金，为其生成新的随机净值
        for (FundInfo fund : allFundsInDb) {
            BigDecimal currentNetValue = fund.getNetValue();

            // 如果基金没有初始净值，则为其设定一个在0.8到1.5之间的随机初始值
            if (currentNetValue == null || currentNetValue.compareTo(BigDecimal.ZERO) == 0) {
                currentNetValue = new BigDecimal(0.8 + random.nextDouble() * 0.7);
            }

            // 3. 生成一个 -2.5% 到 +2.5% 之间的随机日波动率
            // (random.nextDouble() * 0.05) 会生成 [0.0, 0.05) 的随机数
            // 减去 0.025 后，范围变为 [-0.025, 0.025)
            double percentageChange = (random.nextDouble() * 0.05) - 0.025;
            BigDecimal changeMultiplier = BigDecimal.ONE.add(new BigDecimal(percentageChange));

            // 4. 计算新的净值，并保留4位小数，采用四舍五入
            BigDecimal newNetValue = currentNetValue.multiply(changeMultiplier).setScale(4, RoundingMode.HALF_UP);

            // 5. 创建一个仅包含主键和新净值的对象用于更新
            FundInfo fundForUpdate = new FundInfo();
            fundForUpdate.setFundCode(fund.getFundCode());
            fundForUpdate.setNetValue(newNetValue);
            fundsToUpdate.add(fundForUpdate);
        }

        // 6. 将所有待更新的数据一次性批量写入数据库
        if (!fundsToUpdate.isEmpty()) {
            fundInfoService.updateBatchById(fundsToUpdate);
            System.out.println("【定时任务】本地高速模拟完成！共更新 " + fundsToUpdate.size() + " 只基金的净值。");
            return fundsToUpdate.size();
        }

        return 0;
    }
}