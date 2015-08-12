package com.example.drcom;


public class mkpkt {
	
	public static byte[] dr_mkpkt(byte[] salt, String ipaddr, String usr, String pwd, String mac) throws Exception{
		
		byte[] data_3 = new byte[36];
		byte[] data_5 = new byte[6];
		byte[] data_8 = new byte[16];
		byte[] data_9 = new byte[8];
		byte[] data_11 = new byte [32];
		
		String ip = ipaddr;    //MainActivity.ipaddr;
		String[] ip_foo = ip.split("\\.");
		
		
		int account_length = usr.length() + 20;
		byte[] account = usr.getBytes();
		byte[] mac_addr = HexStr2ByteArr.hexStr2ByteArr(mac);
		
		byte[] foo_1 = {0x03, 0x01};
		byte[] foo_2 = {0x01};
		byte[] foo_3 = {0x00, 0x00, 0x00, 0x00};
		byte[] foo_4 = {0x14, 0x00, 0x07, 0x0b};
//		byte[] foo_5 = {0x03, 0x01};
		byte[] foo_6 = {0x01, 0x26, 0x07, 0x11, 0x00, 0x00};
		byte[] foo_7 = {0x00, 0x00};
		
		byte[] merge_1 = ByteMerge.byteMerger(foo_1, salt);
		byte[] merge_2 = ByteMerge.byteMerger(merge_1, pwd.getBytes());
		byte[] merge_3 = ByteMerge.byteMerger(foo_2, pwd.getBytes());
		byte[] merge_4 = ByteMerge.byteMerger(merge_3, salt);
		byte[] merge_5 = ByteMerge.byteMerger(merge_4, foo_3);
//		byte[] merge_6 = ByteMerge.byteMerger(foo_5, salt);
//		byte[] merge_7 = ByteMerge.byteMerger(merge_6, pwd.getBytes());
		
		
		byte[] data_1 = {0x03, 0x01, 0x00, (byte) account_length};    //code, type, EOF, username_length
		byte[] data_2 = ByteMD5.MD5_lite(merge_2);    //md5a
		          
		for(int data3_j=0;data3_j<account.length;data3_j++){
			data_3[data3_j] = account[data3_j];     //username
			
		}
		
		for(int data3_i=account.length;data3_i<36;data3_i++){
			   data_3[data3_i] = 0x00;
			
		}
		
		
		
		
		
		byte[] data_4 = {0x00, 0x00};    //unknow, mac_flag
		
		for(int i=0;i<6;i++){
			   data_5[i] = (byte) (data_2[i] ^ mac_addr[i]);    //mac xor md5a
		}
		byte[] data_6 = ByteMD5.MD5_lite(merge_5);    //md5b
		byte[] data_7 = {0x01};    //nic count

		for(int ip_i=0;ip_i<ip_foo.length;ip_i++){
			   data_8[ip_i] = (byte) Integer.parseInt(ip_foo[ip_i]);    //ips
			
		}
		byte[] sum_1 = ByteMerge.byteMerger(data_1, data_2);
		byte[] sum_2 = ByteMerge.byteMerger(sum_1, data_3);
		byte[] sum_3 = ByteMerge.byteMerger(sum_2, data_4);
		byte[] sum_4 = ByteMerge.byteMerger(sum_3, data_5);
		byte[] sum_5 = ByteMerge.byteMerger(sum_4, data_6);
		byte[] sum_6 = ByteMerge.byteMerger(sum_5, data_7);
		byte[] sum_7 = ByteMerge.byteMerger(sum_6, data_8);
		byte[] sum_8 = ByteMerge.byteMerger(sum_7, foo_4);
		byte[] sum_foo = ByteMD5.MD5_lite(sum_8);
		for(int sum_i=0;sum_i<8;sum_i++){
			data_9[sum_i] = sum_foo[sum_i];    //checksum1
			
		}
	    byte[] data_10 = {0x01, 0x00, 0x00, 0x00, 0x00};    //ipdog, zeros

	    for(int host_j=0;host_j<8;host_j++){
	        data_11[host_j] = 0x69;
	    }                                                   //hostname:iiiiiiii
		for(int host_i=8;host_i<32;host_i++){
			
			data_11[host_i] = 0x00;
		}
		byte[] data_12 = {0x0a, 0x0a, 0x0a, 0x0a};    //dns
		byte[] data_13 = new byte[164];    //zero
		byte[] data_14 = {0x6e, 0x00, 0x00, (byte)pwd.length()};    //unknown
		byte[] data_15 = ror.dr_ror(ByteMD5.MD5_lite(merge_2), pwd);    //md5 ror pwd
		
		byte[] data_16 = {0x02, 0x0c};
		
		byte[] sum_7_1 = ByteMerge.byteMerger(sum_7, data_9);
		byte[] sum_7_2 = ByteMerge.byteMerger(sum_7_1, data_10);
		byte[] sum_7_3 = ByteMerge.byteMerger(sum_7_2, data_11);
		byte[] sum_7_4 = ByteMerge.byteMerger(sum_7_3, data_12);
		byte[] sum_7_5 = ByteMerge.byteMerger(sum_7_4, data_13);
		byte[] sum_7_6 = ByteMerge.byteMerger(sum_7_5, data_14);
		byte[] sum_7_7 = ByteMerge.byteMerger(sum_7_6, data_15);
		byte[] sum_7_8 = ByteMerge.byteMerger(sum_7_7, data_16);
		byte[] merge_foo_6 = ByteMerge.byteMerger(sum_7_8, foo_6);
		byte[] merge_foo_mac = ByteMerge.byteMerger(merge_foo_6, dump.dr_dump(mac));
		byte[] data_17 = checksum.dr_checksum(merge_foo_mac);
		
		byte[] data_18 = ByteMerge.byteMerger(foo_7, dump.dr_dump(mac));
		
		byte[] merge_data_1 = ByteMerge.byteMerger(sum_7_8, data_17);
		byte[] data = ByteMerge.byteMerger(merge_data_1, data_18);
		
		return data;
		
	}
}
