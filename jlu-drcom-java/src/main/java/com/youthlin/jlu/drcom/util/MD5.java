package com.youthlin.jlu.drcom.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
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

    /*//http://www.tuicool.com/articles/QJ7bYr
     * 字符串 DESede(3DES) 加密
     * ECB模式/使用PKCS7方式填充不足位,目前给的密钥是192位
     * 3DES（即Triple DES）是DES向AES过渡的加密算法（1999年，NIST将3-DES指定为过渡的
     * 加密标准），是DES的一个更安全的变形。它以DES为基本模块，通过组合分组方法设计出分组加
     * 密算法，其具体实现如下：设Ek()和Dk()代表DES算法的加密和解密过程，K代表DES算法使用的
     * 密钥，P代表明文，C代表密表，这样，
     * 3DES加密过程为：C=Ek3(Dk2(Ek1(P)))
     * 3DES解密过程为：P=Dk1((EK2(Dk3(C)))
     * <p>
     * args在java中调用sun公司提供的3DES加密解密算法时，需要使
     * 用到$JAVA_HOME/jre/lib/目录下如下的4个jar包：
     * jce.jar
     * security/US_export_policy.jar
     * security/local_policy.jar
     * ext/sunjce_provider.jar
     */
    private static final String Algorithm = "DESede"; //定义加密算法,可用 DES,DESede,Blowfish
    private static final byte[] none = new byte[]{};
    public static final int DES_KEY_LEN = 24;

    private static byte[] des(int mode, byte[] key, byte[] data) {
        try {
            SecretKey deskey = new SecretKeySpec(key, Algorithm);//生成密钥
            Cipher c1 = Cipher.getInstance(Algorithm);//加密或解密
            c1.init(mode, deskey);
            return c1.doFinal(data);//在单一方面的加密或解密
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException ignore) {
        } catch (java.lang.Exception e3) {
            e3.printStackTrace();
        }
        return none;
    }

    public static byte[] encrypt3DES(byte[] keybyte, byte[] src) {
        return des(Cipher.ENCRYPT_MODE, keybyte, src);
    }

    public static byte[] decrypt3DES(byte[] keybyte, byte[] secret) {
        return des(Cipher.DECRYPT_MODE, keybyte, secret);
    }

    public static void main(String[] args) {
        byte[] enk = ByteUtil.ljust("1".getBytes(), DES_KEY_LEN);//用于加密的密码，必须 24 长度
        String password = "a";//要加密的字符串
        System.out.println("加密前的字符串:" + password + " | " + ByteUtil.toHexString(password.getBytes()));
        byte[] encoded = encrypt3DES(enk, password.getBytes());
        System.out.println("加密后:" + ByteUtil.toHexString(encoded));
        byte[] srcBytes = decrypt3DES(enk, ByteUtil.fromHex(ByteUtil.toHexString(encoded), ' '));
        System.out.println("解密后的字符串:" + new String(srcBytes) + " | " + ByteUtil.toHexString(srcBytes));
    }/*
     * 加密前的字符串:123456 | 31 32 33 34 35 36
     * 加密后的字符串:ǋ�Y�_l� | C7 8B C1 59 94 5F 6C AE
     * 解密后的字符串:123456 | 31 32 33 34 35 36
     */
}
