package com.whu.hongjing.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.service.AISuggestionService;
import com.whu.hongjing.service.CustomerTagRelationService;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AISuggestionServiceImpl implements AISuggestionService {

    @Autowired
    private CustomerTagRelationService customerTagRelationService;

    @Autowired
    private ObjectMapper objectMapper;

    // 【【【 已根据您的截图，使用HTTP接口的“APIPassword”作为令牌 】】】
    // 请注意：这里的 "XYD****baj" 是占位符，您需要从讯飞控制台复制完整的APIPassword并替换它
    private static final String BEARER_TOKEN = "XYDmAcuocSzuUsjftsSY:AOHxvJOLRYsigYzBqbaJ"; // <-- 请在这里填入您完整的APIPassword

    // 【【【 已根据您的截图，使用正确的HTTP接口地址 】】】
    private static final String API_URL = "https://spark-api-open.xf-yun.com/v1/chat/completions";

    @Override
    public String getMarketingSuggestion(Customer customer) {
        String prompt = buildPrompt(customer);
        try {
            return callSparkHttpAPI(prompt);
        } catch (Exception e) {
            e.printStackTrace();
            return "【AI建议生成失败】调用API时发生错误：" + e.getMessage();
        }
    }

    private String buildPrompt(Customer customer) {
        List<CustomerTagRelation> tags = customerTagRelationService.lambdaQuery()
                .eq(CustomerTagRelation::getCustomerId, customer.getId()).list();

        StringBuilder sb = new StringBuilder();
        sb.append("你是一位专业的基金投资顾问，请根据以下客户画像，为我生成一段不超过200字的、可以直接用来和他微信沟通的营销话术建议。话术要自然、专业、有洞察力。客户画像如下：\n");
        sb.append("- 客户姓名: ").append(customer.getName()).append("\n");
        sb.append("- 核心标签: \n");
        Map<String, List<String>> groupedTags = tags.stream().collect(Collectors.groupingBy(CustomerTagRelation::getTagCategory, Collectors.mapping(CustomerTagRelation::getTagName, Collectors.toList())));
        groupedTags.forEach((category, tagList) -> sb.append("  - ").append(category).append(": ").append(String.join(", ", tagList)).append("\n"));
        return sb.toString();
    }

    /**
     * 【核心】使用HttpClient调用讯飞星火的HTTP API
     */
    private String callSparkHttpAPI(String prompt) throws Exception {
        // 1. 构建请求体 (符合OpenAI格式)
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> requestBodyMap = new HashMap<>();
        requestBodyMap.put("model", "Spark Lite"); // 使用免费的lite版
        requestBodyMap.put("messages", Collections.singletonList(message));

        String requestBody = objectMapper.writeValueAsString(requestBodyMap);

        // 2. 发送HTTP请求
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

            // 【【【 核心修正：使用最简单的Bearer Token认证方式 】】】
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + BEARER_TOKEN);

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity());

                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("API返回错误: " + response.getStatusLine().getStatusCode() + " " + responseBody);
                }

                // 3. 解析响应
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
}