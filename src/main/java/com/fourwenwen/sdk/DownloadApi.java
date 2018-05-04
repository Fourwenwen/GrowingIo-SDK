package com.fourwenwen.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by king on 7/20/16.
 * 用户可以自己实现store方法,控制下载数据的存储。
 */
public abstract class DownloadApi implements StoreApi {
    private static final Logger logger = LoggerFactory.getLogger(DownloadApi.class);

    private static final String BASE_ENDPOINT = "https://www.growingio.com";
    private static HttpClient httpClient = HttpClients.createDefault();
    private static ObjectMapper objectMapper = new ObjectMapper();

    protected final GrowingConfig config;

    public DownloadApi(String configName) {
        config = new GrowingConfig(configName);
    }

    public String authorize() {
        try {
            long tm = System.currentTimeMillis();
            String auth = authToken(config.getSecretKey(), config.getProjectId(), config.getAi(), tm);
            return sendAuthPost(auth, tm);
        } catch (Exception e) {
            logger.error("can not authorize the identity");
        }
        return null;
    }

    /**
     * 下载对应天或者小时的导出数据
     *
     * @param date date的格式包括天和小时,为北京时间,例如:20160718指代2016年7月18日数据,
     *             2016071809指代2016年7月18日早上9点数据
     */
    public String[] download(String date) {
        return download(date, 5);
    }

    public String[] download(String date, String dataType) {
        return download(date, dataType, 5, null);
    }

    public String[] download(String date, String dataType, DownCallback callback) {
        return download(date, dataType, 5, callback);
    }

    public String[] download(String date, int expire) {
        return download(date, null, expire, null);
    }

    public String[] download(String date, String dataType, int expire, DownCallback callback) {
        String authorized = authorize();
        if (authorized != null) {
            return download(authorized, dataType, date, expire, callback);
        } else {
            logger.error("Authorize Failed");
            return null;
        }
    }

    public String[] download(String authorized, String dataType, String date, int expire, DownCallback callback) {
        String[] links;
        if (dataType == null) {
            // api 1.0
            links = sendInsightsGet(authorized, date, expire);
        } else {
            // api 2.0
            links = sendV2InsightsGet(authorized, dataType, date, expire);
        }
        if (links != null) {
            for (int i = 0; i < links.length; i++) {
                logger.info("download links: " + links[i]);
            }
            try {
                return store(links, callback);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            logger.error("failed to download, links is null");
        }
        return null;
    }

    /**
     * 将获取的下载链接存储在指定的目录下,同时可以选择是否解压
     *
     * @param links 下载链接数组
     */
    public abstract String[] store(String[] links);

    public abstract String[] store(String[] links, DownCallback downCallback);


    private String authToken(String secret, String project, String ai, long tm) throws Exception {
        String message = "POST\n/auth/token\nproject=" + project + "&ai=" + ai + "&tm=" + tm;
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256"));
            byte[] signature = hmac.doFinal(message.getBytes("UTF-8"));
            return Hex.encodeHexString(signature);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 发送认证信息,获取返回的认证码
     *
     * @return 认证码
     */
    private String sendAuthPost(String auth, long tm) {
        String url = BASE_ENDPOINT + "/auth/token";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Client-Id", config.getPublicKey());
        headers.put("Content-Type", "application/x-www-form-urlencoded");

        Map<String, String> params = new HashMap<String, String>();
        params.put("project", config.getProjectId());
        params.put("ai", config.getAi());
        params.put("auth", auth);
        params.put("tm", String.valueOf(tm));

        try {
            HttpResponse response = post(url, headers, params, Charset.forName("utf-8"));
            String jsonString = EntityUtils.toString(response.getEntity());
            Map<String, String> json = objectMapper.readValue(jsonString, Map.class);
            String code = json.get("code");
            return code;
        } catch (IOException e) {
            logger.error("failed to post the auth request: " + e);
        }
        return null;
    }

    /**
     * 获取数据下载链接
     *
     * @param authorized 前面post获取到的认证码
     * @param date       文件时间(天或者小时)
     * @param expire     过期时间,默认5min
     * @return 返回下载链接的数组
     */
    private String[] sendInsightsGet(String authorized, String date, int expire) {
        String url = BASE_ENDPOINT + "/insights/" + config.getAi() + "/" + date + ".json";

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Client-Id", config.getPublicKey());
        headers.put("Authorization", authorized);

        Map<String, String> params = new HashMap<String, String>();
        params.put("expire", String.valueOf(expire));

        try {
            HttpResponse response = get(url, headers, params, Charset.forName("utf-8"));
            String jsonString = EntityUtils.toString(response.getEntity());
            Map<String, List<String>> json = objectMapper.readValue(jsonString, Map.class);
            if (json.containsKey("downlinks")) {
                List<String> linkList = json.get("downlinks");
                String[] links = new String[linkList.size()];
                for (int i = 0; i < linkList.size(); i++) {
                    links[i] = linkList.get(i);
                }
                return links;
            } else {
                logger.error("the response has no downlinks: " + jsonString);
            }
        } catch (IOException e) {
            logger.error("failed to get insights download links", e);
        }
        return null;
    }

    /**
     * 获取数据下载链接
     *
     * @param authorized 前面post获取到的认证码
     * @param date       文件时间(天或者小时)
     * @param expire     过期时间,默认5min
     * @return 返回下载链接的数组
     */
    private String[] sendV2InsightsGet(String authorized, String dataType, String date, int expire) {
        String exportType = "hour";
        if (date.length() == 8) {
            exportType = "day";
        }
        String url = BASE_ENDPOINT + "/v2/insights/" + exportType + "/" + dataType + "/" + config.getAi() + "/" + date + ".json";

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Client-Id", config.getPublicKey());
        headers.put("Authorization", authorized);

        Map<String, String> params = new HashMap<String, String>();
        params.put("expire", String.valueOf(expire));

        try {
            HttpResponse response = get(url, headers, params, Charset.forName("utf-8"));
            String jsonString = EntityUtils.toString(response.getEntity());
            Map<String, List<String>> json = objectMapper.readValue(jsonString, Map.class);
            List<String> linkList = json.get("downloadLinks");
            if (linkList != null) {
                String[] links = new String[linkList.size()];
                for (int i = 0; i < linkList.size(); i++) {
                    links[i] = linkList.get(i);
                }
                return links;
            } else {
                logger.error("this date maybe has no data,date:" + date);
            }
        } catch (IOException e) {
            logger.error("failed to get insights download links: " + e);
        }
        return null;
    }

    public HttpResponse post(String url, Map<String, String> headers, Map<String, String> params, Charset charset) throws IOException {
        HttpResponse response = null;
        try {
            HttpPost httpPost = new HttpPost(url);
            setHeaders(httpPost, headers);

            httpPost.setEntity(buildEntity(params, charset));

            response = httpClient.execute(httpPost);
        } catch (IOException e) {
            throw new IOException("error when post the request: " + url + ", params: " + params);
        }
        return response;
    }

    public HttpResponse get(String url, Map<String, String> headers, Map<String, String> params, Charset charset) throws IOException {
        HttpResponse response = null;
        try {
            if (params != null && !params.isEmpty()) {
                String query = EntityUtils.toString(buildEntity(params, charset));
                url = url + "?" + query;
            }
            HttpGet httpGet = new HttpGet(url);
            setHeaders(httpGet, headers);

            response = httpClient.execute(httpGet);
        } catch (IOException e) {
            throw new IOException("error when post the request: " + url + ", params: " + params);
        }
        return response;
    }

    private void setHeaders(HttpRequestBase request, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    request.setHeader(entry.getKey(), value);
                }
            }
        }
    }

    private HttpEntity buildEntity(Map<String, String> params, Charset charset) {
        if (params != null && !params.isEmpty()) {
            List<NameValuePair> pairs = new ArrayList<NameValuePair>(params.size());
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String value = entry.getValue();
                if (value != null) {
                    pairs.add(new BasicNameValuePair(entry.getKey(), value));
                }
            }
            return new UrlEncodedFormEntity(pairs, charset);
        }
        return null;
    }

}
