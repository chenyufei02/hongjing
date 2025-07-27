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

    private static final String FUND_DATA_URL = "https://fund.eastmoney.com/js/fundcode_search.js";

    @Override
    public int importFundsFromDataSource() {
        String responseText = "";

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(FUND_DATA_URL);
            httpGet.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36");
            httpGet.setHeader("Referer", "https://fund.eastmoney.com/");

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

        // --- 后续的解析和保存逻辑 ---

        // 移除开头的BOM字符和变量赋值部分，直接获取JSON数组
        if (responseText.startsWith("var r = ")) {
            responseText = responseText.substring(responseText.indexOf("["));
        }

        Pattern pattern = Pattern.compile("\\[\"(.*?)\",\"(.*?)\",\"(.*?)\",\"(.*?)\",\"(.*?)\"]");
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

    /**
     * 【 基金风险评分方法 】
     * @param fundType 基金类型字符串
     * @return 1-5分的风险评级
     */
    private Integer mapTypeToRiskScore(String fundType) {
        // 1. 绝对优先规则：只要包含这些词，直接确定风险等级
        if (fundType.contains("货币")) return 1;
        if (fundType.contains("纯债") || fundType.contains("固收")) return 2;
        if (fundType.contains("商品") || fundType.contains("原油") || fundType.contains("黄金")) return 4; // 商品类风险较高

        // 2. 确定基础分
        int baseScore = 3; // 默认给予中性评级
        if (fundType.contains("股票") || fundType.contains("指数")) {
            baseScore = 5;
        } else if (fundType.contains("混合") || fundType.contains("FOF") || fundType.contains("QDII")) {
            baseScore = 4;
        } else if (fundType.contains("债券") || fundType.contains("Reits")) {
            baseScore = 2;
        }

        // 3. 应用调整分（风险升高项）
        if (fundType.contains("偏股") || fundType.contains("进取") || fundType.contains("成长") || fundType.contains("增强")) {
            baseScore++;
        }
        // 对于投资海外股票的QDII，风险应为最高
        if (fundType.contains("QDII") && (fundType.contains("股票") || fundType.contains("指数"))) {
            baseScore = 5;
        }

        // 4. 应用调整分（风险降低项）
        if (fundType.contains("偏债") || fundType.contains("稳健") || fundType.contains("对冲") || fundType.contains("量化")) {
            baseScore--;
        }
        // 对于投资海外债券的QDII，风险应为中低
        if (fundType.contains("QDII") && fundType.contains("债")) {
            baseScore = 2;
        }
        // FOF中的稳健型，风险应较低
        if (fundType.contains("FOF") && fundType.contains("稳健")) {
            baseScore = 2;
        }


        // 5. 确保分数在1-5的范围内
        if (baseScore > 5) return 5;
        if (baseScore < 1) return 1;

        return baseScore;
    }
}