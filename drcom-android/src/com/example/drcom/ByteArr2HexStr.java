package com.example.drcom;

public class ByteArr2HexStr {
	/**  
     * 将byte数组转换为表示16进制值的字符串， 如：byte[]{8,18}转换为：0813， 和public static byte[]  
     * hexStr2ByteArr(String strIn) 互为可逆的转换过程  
     *   
     * @param arrB  
     *            需要转换的byte数组  
     * @return 转换后的字符串  
     * @throws Exception  
     *             本方法不处理任何异常，所有异常全部抛出  
     */  
   public static String byteArr2HexStr(byte[] arrB) throws Exception {
       int iLen = arrB.length;
       // 每个byte用两个字符才能表示，所以字符串的长度是数组长度的两倍
       StringBuffer sb = new StringBuffer(iLen * 2);
       for (int i = 0; i < iLen; i++) {
           int intTmp = arrB[i];
           // 把负数转换为正数
           while (intTmp < 0) {
               intTmp = intTmp + 256;
           }
           // 小于0F的数需要在前面补0
           if (intTmp < 16) {
               sb.append("0");
           }
           sb.append(Integer.toString(intTmp, 16));
       }
       return sb.toString();
   }
}
