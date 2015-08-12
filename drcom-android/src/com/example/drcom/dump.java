package com.example.drcom;

public class dump {
	public static byte[] dr_dump(String mac) throws NumberFormatException, Exception{
		long foo = Long.parseLong(mac, 16);;
		String s= Long.toHexString(foo);
		if(s.length() % 2 == 1){
			String str_foo = "0" + s;
			return HexStr2ByteArr.hexStr2ByteArr(str_foo);
		}
		return HexStr2ByteArr.hexStr2ByteArr(s);
			
		
	}
}
