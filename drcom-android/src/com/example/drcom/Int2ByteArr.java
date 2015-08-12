package com.example.drcom;

public class Int2ByteArr {
    public static byte[] intToByteArray(long i) {   
        byte[] result = new byte[4];   
        //由高位到低位
        result[3] = (byte)((i >> 24) & 0xFF);
        result[2] = (byte)((i >> 16) & 0xFF);
        result[1] = (byte)((i >> 8) & 0xFF); 
        result[0] = (byte)(i & 0xFF);
        return result;
      }
}
