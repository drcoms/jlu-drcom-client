package com.example.drcom;

public class packet_CRC {
	public static byte[] crc(byte[] check) throws NumberFormatException, Exception{
		long ret = 0;
		byte[] check_foo = new byte[2];
		int j_foo = 1;
		for(int j=0;j<check.length;j=j+2){
			
			int i_foo = 0;
			for(int i=0;i<2;i++){
				if(2*j_foo-1-i_foo>check.length){break;}
				check_foo[i] = check[2*j_foo-1-i_foo];
				++i_foo;
			}
			
            j_foo++;
			ret ^= Long.parseLong(ByteArr2HexStr.byteArr2HexStr(check_foo), 16);
		}
		ret = ret * 711;
		byte[] pack = Int2ByteArr.intToByteArray(ret);
		return pack;
		
	}
}
