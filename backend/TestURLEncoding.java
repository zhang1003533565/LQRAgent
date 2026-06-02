import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TestURLEncoding {
    public static void main(String[] args) {
        String kbName = "kb-public";
        String fileName = "第1章 Python语言基础.pptx";
        
        System.out.println("原始知识库名称: " + kbName);
        System.out.println("URL 编码后: " + URLEncoder.encode(kbName, StandardCharsets.UTF_8));
        
        System.out.println("\n原始文件名: " + fileName);
        System.out.println("URL 编码后: " + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        
        // 测试完整的 URL
        String baseUrl = "http://localhost:8001";
        String encodedKbName = URLEncoder.encode(kbName, StandardCharsets.UTF_8);
        String url = baseUrl + "/api/v1/knowledge/" + encodedKbName + "/upload";
        
        System.out.println("\n完整 URL: " + url);
    }
}
