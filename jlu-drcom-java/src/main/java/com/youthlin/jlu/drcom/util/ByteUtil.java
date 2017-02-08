package com.youthlin.jlu.drcom.util;

import java.math.BigInteger;
import java.util.Random;

/**
 * Created by lin on 2017-01-11-011.
 * 字节数组工具, byte 数字当作无符号数处理. 因此 toInt(0xff) 将得到 255.
 */
@SuppressWarnings("WeakerAccess")
public class ByteUtil {
    private static final Random random = new Random(System.currentTimeMillis());

    public static byte randByte() {
        return (byte) random.nextInt();
    }

    public static int toInt(byte b) {
        return b & 0xff;//0xff -> 255
    }

    /*得到两位长度的 16 进制表示*/
    public static String toHexString(byte b) {
        String tmp = Integer.toHexString(toInt(b));
        if (tmp.length() == 1) {
            tmp = '0' + tmp;
        }
        return tmp;
    }

    /*使用空格分割的十六进制表示字符串*/
    public static String toHexString(byte[] bytes) {
        return toHexString(bytes, ' ');
    }

    /*使用 split 字符分割的十六进制表示字符串*/
    public static String toHexString(byte[] bytes, char split) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int len = bytes.length;
        StringBuilder sb = new StringBuilder(len * 3);//两位数字+split
        for (byte b : bytes) {
            sb.append(toHexString(b)).append(split);
        }
        return sb.toString().substring(0, len * 3 - 1).toUpperCase();
    }

    public static byte[] fromHex(String hexStr) {
        return fromHex(hexStr, ' ');
    }

    public static byte[] fromHex(String hexStr, char split) {
        // hexStr = 00 01 AB str len = 8 length = (8+1)/3=3
        int length = (hexStr.length() + 1) / 3;
        byte[] ret = new byte[length];
        for (int i = 0; i < length; i++) {
            ret[i] = (byte) Integer.parseInt(hexStr.substring(i * 3, i * 3 + 2), 16);
        }
        return ret;
    }

    public static byte[] ljust(byte[] src, int count) {
        return ljust(src, count, (byte) 0x00);
    }

    public static byte[] ljust(byte[] src, int count, byte fill) {
        int srcLen = src.length;
        byte[] ret = new byte[count];
        if (srcLen >= count) {//只返回前 count 位
            System.arraycopy(src, 0, ret, 0, count);
            return ret;
        }
        System.arraycopy(src, 0, ret, 0, srcLen);
        for (int i = srcLen; i < count; i++) {
            ret[i] = fill;
        }
        return ret;
    }

    public static byte[] ror(byte[] md5a, byte[] password) {
        int len = password.length;
        byte[] ret = new byte[len];
        int x;
        for (int i = 0; i < len; i++) {
            x = toInt(md5a[i]) ^ toInt(password[i]);
            //e.g. x =   0xff      1111_1111
            // x<<3  = 0x07f8 0111_1111_1000
            // x>>>5 = 0x0007           0111
            // +       0x07ff 0111_1111_1111
            //(byte)截断=0xff
            ret[i] = (byte) ((x << 3) + (x >>> 5));
        }
        return ret;
    }

    /**
     * 每四个数倒过来后与sum相与, 最后*1968取后4个数.
     * <p>
     * Python版用<code>'....'</code>匹配四个字节，但是这个正则会忽略换行符 0x0a, 因此计算出来的是错误的.
     * 但是python版代码是可用的，因此应该是服务器没有检验
     * (Python 版协议与本工程有些许不一样, 所以这里需要返回正确的校验码)
     * <pre>
     * import struct
     * import re
     * def checksum(s):
     *    ret = 1234
     *     for i in re.findall('....', s):
     *        tmp = int(i[::-1].encode('hex'), 16)
     *         print(i, i[::-1], ret, tmp, ret ^ tmp)
     *         ret ^= tmp
     *     ret = (1968 * ret) & 0xffffffff
     *     return struct.pack('<I', ret)
     * </pre>
     **/
    public static byte[] checksum(byte[] data) {
        // 1234 = 0x_00_00_04_d2
        byte[] sum = new byte[]{0x00, 0x00, 0x04, (byte) 0xd2};
        int len = data.length;
        int i = 0;
        //0123_4567_8901_23
        for (; i + 3 < len; i = i + 4) {
            //abcd ^ 3210
            //abcd ^ 7654
            //abcd ^ 1098
            sum[0] ^= data[i + 3];
            sum[1] ^= data[i + 2];
            sum[2] ^= data[i + 1];
            sum[3] ^= data[i];
        }
        if (i < len) {
            //剩下_23
            //i=12,len=14
            byte[] tmp = new byte[4];
            for (int j = 3; j >= 0 && i < len; j--) {
                //j=3 tmp = 0 0 0 2  i=12  13
                //j=2 tmp = 0 0 3 2  i=13  14
                tmp[j] = data[i++];
            }
            for (int j = 0; j < 4; j++) {
                sum[j] ^= tmp[j];
            }
        }
        BigInteger bigInteger = new BigInteger(1, sum);//无符号数即正数
        bigInteger = bigInteger.multiply(BigInteger.valueOf(1968));
        bigInteger = bigInteger.and(BigInteger.valueOf(0xff_ff_ff_ffL));
        byte[] bytes = bigInteger.toByteArray();
        //System.out.println(ByteUtil.toHexString(bytes));
        len = bytes.length;
        i = 0;
        byte[] ret = new byte[4];
        for (int j = len - 1; j >= 0 && i < 4; j--) {
            ret[i++] = bytes[j];
        }
        return ret;
    }

    //参考了 Python 版
    public static byte[] crc(byte[] data) {
        byte[] sum = new byte[2];
        int len = data.length;
        for (int i = 0; i + 1 < len; i = i + 2) {
            sum[0] ^= data[i + 1];
            sum[1] ^= data[i];
        }//现在数据都是偶数位
        BigInteger b = new BigInteger(1, sum);
        b = b.multiply(BigInteger.valueOf(711));
        byte[] bytes = b.toByteArray();
        len = bytes.length;
        //System.out.println(toHexString(bytes));
        byte[] ret = new byte[4];
        for (int i = 0; i < 4 && len > 0; i++) {
            ret[i] = bytes[--len];
        }
        return ret;
    }

}
