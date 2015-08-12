package com.example.drcom;

public class ror {
	public static byte[] dr_ror(byte[] md5, String pwd) throws Exception{
		String ret = "";
		char[] pwd_foo = pwd.toCharArray();
		
		for(int i=0;i<pwd.length();i++){
			byte[] gg = {md5[i]};
			int x = Integer.valueOf(ByteArr2HexStr.byteArr2HexStr(gg), 16) ^ Integer.valueOf(pwd_foo[i]);
			int a = (x << 3)&0xFF;
			int b = x >> 5;
			int c = (a + b) & 0xFF;
			if(Integer.toHexString(c).length()==1){
				String foo = "0" + Integer.toHexString(c);
				ret += foo;
			}else{
				ret = ret + Integer.toHexString(c);
				
			}
			
			
		}
		return HexStr2ByteArr.hexStr2ByteArr(ret);
		
	}
}
