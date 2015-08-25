package com.example.drcom;

public class mkopkt {
	public static byte[] dr_mkopkt(byte[] salt, String ipaddr, String usr, String pwd, String mac, byte[] authinfo) throws Exception{
		String ip = ipaddr;    //MainActivity.ipaddr;
		String[] ip_foo = ip.split("\\.");
		
		byte[] username = new byte[36];
		byte[] mxm = new byte[6];
		
		int account_length = usr.length() + 20;
		byte[] ctea = {0x06, 0x01, 0x00, (byte) account_length};
		
		byte[] md5_foo_10 = {0x06, 0x01};
		byte[] md5_foo_1 = ByteMerge.byteMerger(md5_foo_10, salt);
		byte[] md5_foo = ByteMerge.byteMerger(md5_foo_1, pwd.getBytes());
		byte[] md5a = ByteMD5.MD5_lite(md5_foo);
		byte[] account = usr.getBytes();
		for(int i=0;i<usr.length();i++){
			username[i] = account[i];
			
		}
		
		for(int i=usr.length();i<35;i++){
			username[i] = 0x00;
		}
		byte[] fm = {0x00, 0x03};
		byte[] mac_addr = HexStr2ByteArr.hexStr2ByteArr(mac);
		for(int i=0;i<6;i++){
			mxm[i] = (byte) (mac_addr[i] ^ md5a[i]);
		}

		byte[] usr_ip = new byte[4];
		for(int ip_i=0;ip_i<ip_foo.length;ip_i++){
			usr_ip[ip_i] = (byte) Integer.parseInt(ip_foo[ip_i]);
		}
		
		byte[] data_1 = ByteMerge.byteMerger(ctea, md5a);
		byte[] data_2 = ByteMerge.byteMerger(data_1, username);
		byte[] data_3 = ByteMerge.byteMerger(data_2, fm);
		byte[] data_4 = ByteMerge.byteMerger(data_3, mxm);
		byte[] data = ByteMerge.byteMerger(data_4, authinfo);
		System.out.println(data.length);
		return data;
	}
}
