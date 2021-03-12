package com.fourwenwen.sdk;

import com.csvreader.CsvReader;

import java.io.IOException;
import java.util.Calendar;

/**
 * Hello world!
 * 数据是从20170105天开始的
 */
public class AppDemo {

    private static String[] dates = new String[]{"20181112","20181112"};
    // private static String[] dates = new String[]{"20180708"};
    private static String[] times = new String[]{"2018041616", "2018041617", "2018041618", "2018041619", "2018041620"};
    private static String[] apiName = new String[]{"visit", "action", "ads_track_activation", "page", "action_tag", "custom_event", "ads_track_click", "pvar", "evar"};

    private final static String configUri = "classPath*:growingApi.conf";

    public static void main(String[] args) throws Exception {
        GrowingDownloadApi api = new GrowingDownloadApi(configUri);
        // api1 原sdk版功能
        /*for (String date : dates) {
            api.download(date);
        }*/
        // api2 单线程版本
        long startTime = System.currentTimeMillis();
        downDates(api);
        System.err.println("单线程下载耗时：" + (System.currentTimeMillis() - startTime) + "ms");
        /*// api2 多线程版
        MultiGrowingDownloadApi multiApi = null;
        try {
            long startTime1 = System.currentTimeMillis();
            multiApi = new MultiGrowingDownloadApi(configUri, 10);
            downDatesCallBack(multiApi, new DownCallback() {
                @Override
                public void handel(String filePath) {
                    CsvReader csvReader = null;
                    try {
                        // 创建CSV读对象
                        csvReader = new CsvReader(filePath);
                        // 读表头
                        csvReader.readHeaders();
                        Calendar now = Calendar.getInstance();
                        while (csvReader.readRecord()) {
                            // 读一整行
                            System.out.print(1);
                        }
                        System.out.println("解析完一个");
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (csvReader != null) {
                            csvReader.close();
                        }
                    }
                }
            });
            System.err.println("多线程下载耗时：" + (System.currentTimeMillis() - startTime1) + "ms");
        } finally {
            if (multiApi != null) {
                multiApi.close();
            }
        }*/
    }

    private static void downDates(DownloadApi api) {
        for (String time : dates) {
            for (String o : apiName) {
                String[] filePaths = api.download(time, o);
                for(String filePath:filePaths){
                    System.out.println(filePath);
                }
                /*if ("ads_track_activation".equals(o) || "page".equals(o)) {
                    for (String filePath : filePaths) {
                        CsvReader csvReader = null;
                        try {
                            // 创建CSV读对象
                            csvReader = new CsvReader(filePath);
                            // 读表头
                            csvReader.readHeaders();
                            Calendar now = Calendar.getInstance();
                            while (csvReader.readRecord()) {
                                // 读一整行
                                System.out.print(1);
                            }
                            System.out.println("解析完一个");
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (csvReader != null) {
                                csvReader.close();
                            }
                        }
                    }
                }*/
            }
        }
    }

    private static void downDatesCallBack(DownloadApi api, DownCallback downCallback) {
        for (String time : dates) {
            for (String o : apiName) {
                if ("ads_track_activation".equals(o) || "page".equals(o)) {
                    api.download(time, o, downCallback);
                } else {
                    api.download(time, o, null);
                }
            }
        }
    }
}
