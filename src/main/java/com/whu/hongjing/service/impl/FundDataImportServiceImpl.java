package com.whu.hongjing.service.impl;

import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.FundDataImportService;
import com.whu.hongjing.service.FundInfoService;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FundDataImportServiceImpl implements FundDataImportService {

    @Autowired
    private FundInfoService fundInfoService;

    private static final String FUND_DATA_URL = "http://fund.eastmoney.com/js/fundcode_search.js";

    @Override
    public int importFundsFromDataSource() {
        String responseText = "";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(FUND_DATA_URL);
            httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36");
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

        // --- 后续的解析和保存逻辑保持不变 ---

        // 移除开头的BOM字符和变量赋值部分，直接获取JSON数组
        if (responseText.startsWith("var r = ")) {
            responseText = responseText.substring(responseText.indexOf("["));
        }

        Pattern pattern = Pattern.compile("\\[\"(.*?)\",\"(.*?)\",\"(.*?)\",\"(.*?)\",\"(.*?)\"\\]");
        Matcher matcher = pattern.matcher(responseText);

        List<FundInfo> fundInfoList = new ArrayList<>();
        while (matcher.find()) {
            FundInfo fundInfo = new FundInfo();
            String fundName = matcher.group(3);
            String fundType = matcher.group(4);

            fundInfo.setFundCode(matcher.group(1));
            fundInfo.setFundName(fundName);
            fundInfo.setFundType(fundType);
            fundInfo.setRiskScore(mapTypeToRiskScore(fundType));

            fundInfoList.add(fundInfo);
        }

        if (!fundInfoList.isEmpty()) {
            fundInfoService.saveOrUpdateBatch(fundInfoList);
            return fundInfoList.size();
        }
        return 0;
    }

    private Integer mapTypeToRiskScore(String fundType) {
        if (fundType.contains("股票") || fundType.contains("指数")) return 5;
        if (fundType.contains("混合")) return 4;
        if (fundType.contains("债券")) return 2;
        if (fundType.contains("货币")) return 1;
        return 3;
    }
}