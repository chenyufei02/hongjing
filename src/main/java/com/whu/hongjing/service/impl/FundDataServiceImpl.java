package com.whu.hongjing.service.impl;

import com.whu.hongjing.service.FundDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

@Service
public class FundDataServiceImpl implements FundDataService {

    /**
     * 这里我们用随机数来模拟获取到了一个真实的净值
     * 真实的实现会是通过HTTP请求一个财经数据API
     */
    @Override
    public BigDecimal getLatestNetValue(String fundCode) {
        // 为了演示，我们返回一个在 0.8 到 2.5 之间的随机净值
        double randomValue = 0.8 + (new Random().nextDouble() * 1.7);
        return new BigDecimal(randomValue).setScale(4, RoundingMode.HALF_UP);
    }
}