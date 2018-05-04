package com.fourwenwen.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author: chaowen.zheng
 * @Description:
 * @Date: Create in 15:05 2018/5/4
 */
public class MultiHttpDownloader {

    private ExecutorService pool;

    private int timeOut;

    private boolean uncompress;

    public MultiHttpDownloader(boolean uncompress, int threadNum, int timeout) {
        this.uncompress = uncompress;
        pool = Executors.newFixedThreadPool(threadNum);
        this.timeOut = timeout;
    }
}
