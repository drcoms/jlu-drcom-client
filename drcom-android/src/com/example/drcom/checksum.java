package com.example.drcom;

public class checksum {
	public static byte[] dr_checksum(byte[] check) throws Exception{
		long ret = 1234;
		
		byte[] check_foo = new byte[4];
		int j_foo = 1;
		for(int j=0;j<check.length;j=j+4){
			int i_foo = 0;
            for(int i=0;i<4;i++){
				if(4*j_foo-1-i_foo>check.length){break;}
				check_foo[i] = check[4*j_foo-1-i_foo];
				++i_foo;
			}
			
            j_foo++;
			ret ^= Long.parseLong(ByteArr2HexStr.byteArr2HexStr(check_foo), 16);
		}
		long ret_foo = 4294967295L;
		ret = (1968 * ret) & ret_foo;
		byte[] pack = Int2ByteArr.intToByteArray(ret);
		
		
		
		return pack;

		
	}
}
