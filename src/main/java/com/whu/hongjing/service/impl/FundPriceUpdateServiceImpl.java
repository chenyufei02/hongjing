package com.whu.hongjing.service.impl;

import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.FundInfoService;
import com.whu.hongjing.service.FundPriceUpdateService;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FundPriceUpdateServiceImpl implements FundPriceUpdateService {

    @Autowired
    private FundInfoService fundInfoService;


    // 我们将从一个新的、只包含净值数据的接口获取信息，这个接口更轻量
    private static final String FUND_PRICE_URL = "http://fund.eastmoney.com/js/netvalue.js";

    @Override
    public int updateAllFundPrices() {
        String responseText = "";

        // 使用HttpClient获取数据
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(FUND_PRICE_URL);
            httpGet.setHeader("User-Agent", "Mozilla/5.0");
            httpGet.setHeader("Referer", "http://fund.eastmoney.com/");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                    responseText = EntityUtils.toString(entity, "UTF-8");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }

        if (responseText.isEmpty()) {
            return 0;
        }

        // 解析净值数据的正则表达式
        // 格式: "000001":{"name":"...","jz":"1.2345","gsz":"1.2356",...}
        Pattern pattern = Pattern.compile("\"(.*?)\":\\{\"name\":\".*?\",\"jz\":\"(.*?)\"");
        Matcher matcher = pattern.matcher(responseText);

        // 要更新的列表
        List<FundInfo> fundsToUpdate = new ArrayList<>();

        while (matcher.find()) {
            String fundCode = matcher.group(1);
            String netValueStr = matcher.group(2);

            // 检查净值是否有效
            if (netValueStr != null && !netValueStr.isEmpty()) {
                FundInfo fundInfo = new FundInfo();
                fundInfo.setFundCode(fundCode);
                fundInfo.setNetValue(new BigDecimal(netValueStr));
                fundsToUpdate.add(fundInfo);
            }
        }

        if (!fundsToUpdate.isEmpty()) {
            // 使用updateBatchById，它会无差别地更新所有我们提供的基金记录
            fundInfoService.updateBatchById(fundsToUpdate);
            return fundsToUpdate.size();
        }
        return 0;
    }
}