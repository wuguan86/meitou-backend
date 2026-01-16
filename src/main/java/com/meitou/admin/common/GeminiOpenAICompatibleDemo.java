package com.meitou.admin.common;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class GeminiOpenAICompatibleDemo {

    // 1. 按照文档修改域名，注意去掉 v1beta/openai

    //https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
    private static final String ENDPOINT = "https://grsaiapi.com/v1/chat/completions";

    // 2. 填入该平台的 API KEY
    private static final String API_KEY = "sk-4921c794a99047508c1008caa1fddde1";

    public static void main(String[] args) {
        try {
            // 按照你截图中支持的模型列表，填入具体的模型名
            // 注意：图中写的是 gemini-2.5-flash (虽然官方没这个版本，但请按平台文档写)
            String modelName = "gemini-2.5-flash";
            String imageUrl = "https://aitoutou.oss-cn-shenzhen.aliyuncs.com/images/风景/84e062612fc04a89b2c8d06eeb2cde02.jpeg";
            String prompt = "请分析这张图片的构图和色彩。";

            String result = analyzeImage(modelName, imageUrl, prompt);
            System.out.println("--- 分析结果 ---");
            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String analyzeImage(String model, String imageUrl, String prompt) throws Exception {
        // 构建 OpenAI 标准格式的 JSON
        String jsonPayload = """
        {
          "model": "%s",
          "messages": [
            {
              "role": "user",
              "content": [
                { "type": "text", "text": "%s" },
                {
                  "type": "image_url",
                  "image_url": { "url": "%s" }
                }
              ]
            }
          ]
        }
        """.formatted(model, prompt, imageUrl);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "application/json")
                // 注意这里：三方平台通常严格遵守 Bearer 格式
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            return "错误状态码：" + response.statusCode() + " 详情：" + response.body();
        }
    }
}
