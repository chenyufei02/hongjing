// 文件: src/main/java/com/whu/hongjing/service/impl/AISuggestionServiceImpl.java
package com.whu.hongjing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.hongjing.constants.TaggingConstants;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.vo.ProfitLossVO;
import com.whu.hongjing.service.AISuggestionService;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AISuggestionServiceImpl implements AISuggestionService {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BEARER_TOKEN = "sk-NCcKP3c4YSsOSt92q5u6Vp3vUFLifWCkZHK7cL9CkAdi9hgl";
    private static final String API_URL = "https://api.moonshot.cn/v1/chat/completions";

    @Override
    public String getMarketingSuggestion(ProfitLossVO profitLossVO, List<CustomerTagRelation> tags, Map<String, BigDecimal> assetAllocationData) {
        String prompt = buildAdvancedPrompt(profitLossVO, tags, assetAllocationData);
        try {
            return callSparkHttpAPI(prompt);
        } catch (Exception e) {
            e.printStackTrace();
            return "【AI建议生成失败】调用API时发生错误：" + e.getMessage();
        }
    }

    private String callSparkHttpAPI(String prompt) throws Exception {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", "moonshot-v1-8k");
        requestBodyMap.put("messages", Collections.singletonList(message));
        requestBodyMap.put("max_tokens", 4096);

        String requestBody = objectMapper.writeValueAsString(requestBodyMap);

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + BEARER_TOKEN);

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("API返回错误: " + response.getStatusLine().getStatusCode() + " " + responseBody);
                }
                Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, String> responseMessage = (Map<String, String>) choices.get(0).get("message");
                    return responseMessage.get("content");
                } else {
                    if (responseMap.containsKey("error")) {
                         Map<String, String> error = (Map<String, String>)responseMap.get("error");
                         throw new RuntimeException("API返回错误: " + error.get("message"));
                    }
                    throw new RuntimeException("API未返回有效的建议内容。");
                }
            }
        }
    }

    private String buildAdvancedPrompt( ProfitLossVO profitLossVO, List<CustomerTagRelation> tags, Map<String, BigDecimal> assetAllocationData) {
        Map<String, String> tagMap = tags.stream()
            .collect(Collectors.toMap(CustomerTagRelation::getTagCategory, CustomerTagRelation::getTagName, (t1, t2) -> t1));

        StringBuilder sb = new StringBuilder();
        sb.append("你是一位顶级的基金投资策略分析师和营销专家。请为我（客户经理）提供一份关于目标客户的深度分析报告和营销策略。以下是该客户的相关数据：\n\n");
        sb.append("--- 客户数据 ---\n\n");
        sb.append("**1. 客户核心画像与业绩概览:**\n");
        sb.append(String.format("- **身份**: %s, %s, %s。\n",
                tagMap.getOrDefault(TaggingConstants.CATEGORY_AGE, "未知年龄"),
                tagMap.getOrDefault(TaggingConstants.CATEGORY_GENDER, "未知性别"),
                tagMap.getOrDefault(TaggingConstants.CATEGORY_OCCUPATION, "未知职业")));
        sb.append(String.format("- **业绩**: 总资产 %.2f 元，累计投资 %.2f 元，当前盈亏 **%.2f 元 (盈亏率 %.2f%%)**。\n\n",
                profitLossVO.getTotalMarketValue(),
                profitLossVO.getTotalInvestment(),
                profitLossVO.getTotalProfitLoss(),
                profitLossVO.getProfitLossRate()));

        sb.append("**2. 资产配置与风险分析 :**\n");
        sb.append("- **资产穿透**: ");
        if (assetAllocationData.isEmpty()) {
            sb.append("客户暂无持仓。\n");
        } else {
            assetAllocationData.forEach((type, value) -> sb.append(String.format("%s (市值: %.2f), ", type, value)));
            sb.delete(sb.length() - 2, sb.length()).append("。\n");
        }

        String diagnosis = tagMap.getOrDefault(TaggingConstants.CATEGORY_RISK_DIAGNOSIS, "未知");
        sb.append(String.format("- **风险诊断**: 客户的风险匹配状态是: **%s**。\n\n", diagnosis));

        sb.append("**3. 客户动态与潜在风险:**\n");
        String recencyTag = tagMap.getOrDefault(TaggingConstants.CATEGORY_RECENCY, "未知");
        sb.append(String.format("- **近期动向**: %s。", recencyTag));
        if (recencyTag.equals(TaggingConstants.LABEL_RECENCY_SHORT_LOST) || recencyTag.equals(TaggingConstants.LABEL_RECENCY_LONG_OUTFLOW)) {
            sb.append(" **【高危预警：客户已有流失倾向！】**");
        }
        sb.append("\n\n");

        sb.append("--- 营销执行方案 ---\n\n");
        sb.append("请基于以上数据，针对该客户生成以下两部分内容(以下内容请注意分行，格式清晰层次分明的分段回答，不要一大段文字挤在一起。)：\n");

        sb.append("**1. 核心洞察与策略 (面向我们客户经理生成针对该客户的分析和内部建议，因此请不要直接面向客户称呼您，而是面向我们客户经理称呼为该客户):** (不超过200字) \n：" +
                "一、基本画像：总结年龄 性别 职业，与投资盈亏情况，给予打气鼓励或安慰等。" +
                "二、持仓分布：分析该客户的持仓特点，给予适当的平衡建议。（根据“-”前面的前缀进行分类计算持仓，例如指数型、债券型、股票型、混合型、货币型。） " +
                "三、交易行为分析：如果近期是流失倾向给流失预警与资产挽留建议、如果近期是沉睡或停滞状态则给沉睡与停滞激活建议，根据近期行为给一种。" +
                "四、风险再平衡沟通：首先判断当前用户的风险诊断是行为保守型（代表该用户过于保守了）还是行为激进型（代表过于激进了），前者则指出可推荐更激进的策略、后者则相反，根据判断结果给出一种即可。" +
                "五、高潜力客户深挖建议：主要看资产规模与近期交易频率是否符合高潜力客户（资产规模较多且近期流失或不够活跃，如果不符合就不用生成这一条。）\n\n");
        sb.append("**2. 微信沟通话术 (给客户的外部话术):** (不超过200字) \n作为客户经理，写一段可以直接复制发送给客户的微信沟通话术。话术需体现出对客户投资状况的专业洞察，并自然地引出你的营销策略（例如：预约沟通、推荐产品、提示风险等），最好具体指明推荐的处理方式和推荐处理的基金类型。");

        System.out.println("======【V2版AI请求】发送给大模型的Prompt是：======\n" + sb);
        return sb.toString();
    }
}