package com.fourwenwen.sdk;

import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Created by king on 7/19/16.
 * 若是使用GrowingDownApi,则可以
 */
public class MultiGrowingDownloadApi extends DownloadApi {
    private static final Logger logger = LoggerFactory.getLogger(MultiGrowingDownloadApi.class);

    private final String baseStorePath;
    private final boolean uncompress;

    private ExecutorService pool;
    private int timeOut;

    public MultiGrowingDownloadApi() {
        this("growingApi", 3, 5000);
    }

    public MultiGrowingDownloadApi(String configName, int threadNum, int timeout) {
        super(configName);
        baseStorePath = config.baseStorePath();
        uncompress = config.uncompress();
        pool = Executors.newFixedThreadPool(threadNum);
        this.timeOut = timeout;
    }

    /**
     * 将获取的下载链接存储在指定的目录下,同时可以选择是否解压
     *
     * @param links 下载链接数组
     */
    public String[] store(String[] links) {
        return store(links, null);
    }

    @Override
    public String[] store(String[] links, DownCallback downCallback) {
        String[] downFiles = new String[links.length];
        for (int i = 0; i < links.length; i++) {
            String[] linkParts = splitLink(links[i]);
            String filename = linkParts[2];
            if (uncompress) {
                // 获取后缀
                filename = filename.substring(0, 10) + "." + filename.split("\\.")[1];
            }

            // 源文件的下载文件显示的UTC时间，这里转换未
            String UTCTime = linkParts[1];
            DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
            LocalDateTime localDateTime = LocalDateTime.parse(UTCTime, hourFormatter);
            localDateTime = localDateTime.plusHours(8);
            String fileDownTime = localDateTime.format(hourFormatter);

            String storePath = baseStorePath + File.separator +
                    linkParts[0] + File.separator + fileDownTime.substring(0, 8) + File.separator + fileDownTime;
            File dir = new File(storePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            pool.execute(new DownloadThread(links[i], storePath + File.separator + filename, downCallback));

            logger.info("Successfully download the file: " + storePath + File.separator + filename);
        }
        return downFiles;
    }

    /**
     * extract download link type(visit, page, action, etc), date and filename(part-xxxxx.gz)
     * it is a hard coding function, maybe changed
     *
     * @param link the insights download link
     * @return array with three elements: [type, date, filename]
     */
    public String[] splitLink(String link) {
        String path = link.split("\\?")[0];
        // 跳过https://growing-insights.s3.cn-north-1.amazonaws.com.cn/37bd_xxx
        int first = path.indexOf('_', 64);
        String info = path.substring(first + 1);

        String[] parts = info.split("/");
        if (parts.length != 2) {
            logger.error("wrong download link: " + link);
        }
        String filename = parts[1];
        int pos = parts[0].lastIndexOf("_");
        String tp = parts[0].substring(0, pos);
        String date = parts[0].substring(pos + 1);
        return new String[]{tp, date, filename};
    }

    //一个下载线程负责下载一个文件，如果失败则自动重试，直到下载完成
    class DownloadThread extends Thread {
        private String url;
        private String filePath;
        private DownCallback callback;

        public DownloadThread(String url, String filePath, DownCallback callback) {
            this.url = url;
            this.filePath = filePath;
            this.callback = callback;
        }

        //保证文件的下载完成
        @Override
        public void run() {
            boolean success = false;
            while (true) {
                success = download();
                if (success) {
                    System.out.println("* Downloaded url " + url);
                    break;
                } else {
                    System.out.println("Retry to download url " + url);
                }
            }
            if (callback != null) {
                callback.handel(filePath);
            }
        }

        //下载文件指定范围的部分
        public boolean download() {
            BufferedOutputStream bout = null;
            BufferedInputStream bin = null;
            try {
                bout = new BufferedOutputStream(new FileOutputStream(filePath));

                HttpResponse response = get(url, null, null, Charset.forName("utf-8"));
                InputStream in = response.getEntity().getContent();
                if (uncompress) {
                    in = new GZIPInputStream(in);
                }
                bin = new BufferedInputStream(in);
                byte[] chunk = new byte[4096];
                int size = -1;
                while ((size = bin.read(chunk)) > -1) {
                    bout.write(chunk, 0, size);
                    bout.flush();
                }
                bout.close();
                bin.close();
            } catch (IOException e) {
                System.err.println("Failed to store the download file: " + url);
                return false;
            }

            return true;
        }
    }

}
