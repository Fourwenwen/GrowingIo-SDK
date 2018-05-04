package com.fourwenwen.sdk;

/**
 * Created by king on 7/19/16.
 */
public interface StoreApi {

    public String[] store(String[] links) throws Exception;

    public String[] store(String[] links, DownCallback downCallback) throws Exception;

}
