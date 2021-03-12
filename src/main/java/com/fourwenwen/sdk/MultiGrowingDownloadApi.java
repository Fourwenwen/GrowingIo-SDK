package com.fourwenwen.sdk;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/**
 * @Author: chaowen.zheng
 * @Description:
 * @Date: Create in 16:52 2018/4/24
 */
public class MultiGrowingDownloadApi extends DownloadApi {
    private static final Logger logger = LoggerFactory.getLogger(MultiGrowingDownloadApi.class);

    private final String baseStorePath;
    private final boolean uncompress;

    // private ExecutorService pool;
    private ListeningExecutorService pool;

    private ListeningExecutorService workPool;

    private final int retriesNum = 3;

    public MultiGrowingDownloadApi() {
        this("growingApi", 10);
    }

    public MultiGrowingDownloadApi(String configName, int threadNum) {
        super(configName);
        baseStorePath = config.baseStorePath();
        uncompress = config.uncompress();
        // pool = Executors.newFixedThreadPool(threadNum);
        pool = MoreExecutors
                .listeningDecorator(new ThreadPoolExecutor(
                        threadNum, 30, 60000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(10000)));
        workPool = MoreExecutors
                .listeningDecorator(new ThreadPoolExecutor(
                        threadNum, 30, 60000L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(10000)));
    }

    /**
     * 将获取的下载链接存储在指定的目录下,同时可以选择是否解压
     *
     * @param links 下载链接数组
     */
    @Override
    public String[] store(String[] links) {
        return store(links, null);
    }

    @Override
    public String[] store(String[] links, DownCallback downCallback) {
        String[] downFiles = new String[links.length];
        for (int i = 0; i < links.length; i++) {
            long startTime = System.currentTimeMillis();
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
            String filePath = storePath + File.separator + filename;

            pool.execute(new DownloadThread(links[i], filePath, downCallback));
            downFiles[i] = filePath;

            logger.info("Successfully download the file: {},take up time:{}ms", filePath, (System.currentTimeMillis() - startTime));
        }
        return downFiles;
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
            int i = 0;
            while (true) {
                success = download();
                if (success) {
                    logger.info("Download url [{}] successfully, The local address is [{}]", url, filePath);
                    break;
                } else {
                    i++;
                    logger.error("Retry to download url " + url);
                }
                if (i > retriesNum) {
                    logger.error("Retry more than " + retriesNum + " times,not try " + url);
                    break;
                }
            }
            if (callback != null) {
                workPool.execute(() ->
                        callback.handel(filePath)
                );

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
                logger.error("Failed to store the download file: " + url, e);
                return false;
            }

            return true;
        }
    }

    public void close() {
        pool.shutdown();
        workPool.shutdown();
    }

    public void suibian (){
        new DownloadThread("https://growing-insight.cn-bj.ufileos.com/7bcc_9b0d1f56d5baff1a_custom_event_v2_202011301100/part-00000-32c9fdd4-9091-4bb2-bcce-5f316894dd4b-c000.csv.gz?UCloudPublicKey=TOKEN_386140fd-1842-4520-9620-4f75a0b2ceb8&Signature=H5UZ9yVwOfAnECqlyW9Vs3bb0EE%3D&Expires=1608129150","F:\\mfs\\ShareFile\\upload\\hs\\GrowingIo\\custom_event_v2\\20201216\\suibian.zip",null).start();
    }

    public static void main(String[] args) {
        /*MultiGrowingDownloadApi api = new MultiGrowingDownloadApi("F:\\gitproject\\hs-task\\src\\main\\resources\\hs-task-file\\growingApi.conf",10);
        api.suibian();*/
        String link = "https://growing-insight.cn-bj.ufileos.com/4b16_9b0d1f56d5baff1a_ads_track_activation_v2_202012160800/part-00000-4484f4eb-3ba7-4764-943f-7f48dfbfde62-c000.csv.gz?UCloudPublicKey=TOKEN_386140fd-1842-4520-9620-4f75a0b2ceb8&Signature=xDyUUn%2Fzv64MJ92QFAFFcjciP3o%3D&Expires=1608130838";
        String path = link.split("\\?")[0];
        // 跳过https://growing-insights.s3.cn-north-1.amazonaws.com.cn/37bd_xxx
        int first = path.indexOf('_', 63);
        String info = path.substring(first + 1);

        String[] parts = info.split("/");
        if (parts.length != 2) {
            logger.error("wrong download link: " + link);
        }
        String filename = parts[1];
        int pos = parts[0].lastIndexOf("_");
        String tp = parts[0].substring(0, pos);
        String date = parts[0].substring(pos + 1);
        String[] strs = new String[]{tp, date, filename};
        for (String one:strs){
            System.out.println(one);
        }

    }



}
