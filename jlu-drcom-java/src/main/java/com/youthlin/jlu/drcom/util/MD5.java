package com.youthlin.jlu.drcom.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by lin on 2017-01-10-010.
 * MD5
 */
public class MD5 {
    private static final byte[] zero16 = new byte[16];

    public static byte[] md5(byte[]... bytes) {
        int len = 0;
        for (byte[] bs : bytes) {
            len += bs.length;//数据总长度
        }
        byte[] data = new byte[len];
        len = 0;//记录已拷贝索引
        for (byte[] bs : bytes) {
            System.arraycopy(bs, 0, data, len, bs.length);
            len += bs.length;
        }
        return md5(data);
    }

    public static byte[] md5(byte[] bytes) {
        try {
            MessageDigest instance = MessageDigest.getInstance("MD5");
            instance.update(bytes);
            return instance.digest();
        } catch (NoSuchAlgorithmException ignore) {
        }
        return zero16;//容错
    }
}
