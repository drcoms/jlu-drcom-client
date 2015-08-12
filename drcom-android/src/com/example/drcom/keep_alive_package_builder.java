package com.example.drcom;

public class keep_alive_package_builder {
	public static byte[] build(int number, byte[] random, byte[] tail, int type, boolean first) throws NumberFormatException, Exception{
		byte[] data_1_1 = {0x07, (byte) number};
		byte[] data_1_2 = {0x28, 0x00, 0x0b, (byte)type};
		byte[] data_1 = ByteMerge.byteMerger(data_1_1, data_1_2);
		
		byte[] data_2_1 = {0x0F, 0x27};
		byte[] data_2_2 = {(byte) 0xdc, 0x02};
		byte[] data_2;
		if (first == true){
			data_2 = ByteMerge.byteMerger(data_1, data_2_1);
		}else{
			data_2 = ByteMerge.byteMerger(data_1, data_2_2);
		}
		
		byte[] data_3_1 = random;
		byte[] data_3_2 = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
		byte[] data_3_3 = ByteMerge.byteMerger(data_3_1, data_3_2);
		byte[] data_3 = ByteMerge.byteMerger(data_2, data_3_3);
		
		byte[] data_4 = ByteMerge.byteMerger(data_3, tail);
		
		byte[] data_5_1 = {0x00, 0x00, 0x00, 0x00};
		byte[] data_5 = ByteMerge.byteMerger(data_4, data_5_1);
		
		byte[] data_6;
		if(type==3){
			byte[] foo = {0x31, (byte) 0x8c, 0x62, 0x31};
			byte[] crc = packet_CRC.crc(ByteMerge.byteMerger(data_5, foo));
			byte[] data_6_1 = {0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00};
			byte[] data_6_2 = ByteMerge.byteMerger(crc, foo);
			byte[] data_6_3 = ByteMerge.byteMerger(data_6_2, data_6_1);
			data_6 = ByteMerge.byteMerger(data_5, data_6_3);
		}else{
			byte[] data_6_1 = {0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00};
			data_6 = ByteMerge.byteMerger(data_5, data_6_1);
		}
		return data_6;
	}
}
