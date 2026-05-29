package com.lqragent.backend.storage;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

/**
 * 七牛云对象存储服务。
 * 提供上传、下载、预签名URL、删除等基本操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QiniuStorageService {

    private final AppRuntimeConfig runtimeConfig;

    private Auth auth;
    private UploadManager uploadManager;
    private BucketManager bucketManager;
    private String bucket;
    private String domain;

    @PostConstruct
    public void init() {
        String ak = runtimeConfig.get(ConfigKeys.QINIU_ACCESS_KEY, "");
        String sk = runtimeConfig.get(ConfigKeys.QINIU_SECRET_KEY, "");
        String region = runtimeConfig.get(ConfigKeys.QINIU_REGION, "cn-south");
        this.bucket = runtimeConfig.get(ConfigKeys.QINIU_BUCKET, "lqragent");

        if (ak.isEmpty() || sk.isEmpty()) {
            log.warn("[QiniuStorage] Access Key 或 Secret Key 未配置，存储服务不可用");
            return;
        }

        this.auth = Auth.create(ak, sk);

        // 根据区域选择 Region
        Region r = switch (region) {
            case "z0" -> Region.region0();
            case "cn-east-2" -> Region.region2();
            case "cn-north" -> Region.region1();
            case "cn-south" -> Region.region2(); // 华南-广东
            default -> Region.region2();
        };

        Configuration config = new Configuration(r);
        this.uploadManager = new UploadManager(config);
        this.bucketManager = new BucketManager(auth, config);

        this.domain = "http://" + bucket + ".qiniucdn.com";
        log.info("[QiniuStorage] 初始化完成: bucket={}, region={}", bucket, region);
    }

    /**
     * 上传文件到七牛云。
     */
    public String upload(String key, byte[] data, String contentType) {
        StringMap params = new StringMap().put("mime-type", contentType);
        try (var is = new ByteArrayInputStream(data)) {
            uploadManager.put(is, key, getUploadToken(), params, null);
            log.debug("[QiniuStorage] uploaded: key={}, size={}", key, data.length);
            return key;
        } catch (Exception e) {
            throw new RuntimeException("七牛云上传失败: " + key, e);
        }
    }

    /**
     * 从七牛云下载文件。
     */
    public byte[] download(String key) {
        String url = domain + "/" + key;
        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException("七牛云下载失败: HTTP " + response.statusCode() + ", key=" + key);
            }
            return response.body();
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("七牛云下载失败: " + key, e);
        }
    }

    /**
     * 生成临时下载链接（有效期 1 小时）。
     */
    public String getPresignedUrl(String key) {
        long deadline = Instant.now().getEpochSecond() + 3600;
        return auth.privateDownloadUrl(domain + "/" + key, deadline);
    }

    /**
     * 删除七牛云上的文件。
     */
    public void delete(String key) {
        try {
            bucketManager.delete(bucket, key);
            log.debug("[QiniuStorage] deleted: key={}", key);
        } catch (Exception e) {
            log.warn("[QiniuStorage] 删除失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 生成上传凭证（有效期 3600 秒）。
     */
    private String getUploadToken() {
        StringMap policy = new StringMap().put("insertOnly", 1);
        return auth.uploadToken(bucket, null, 3600, policy);
    }
}
