package com.example.drcom;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;


public class challenge {
    
	public static byte[] dr_challenge(String svr, boolean flag) throws Exception{
			DatagramSocket client = new DatagramSocket();
			InetAddress addr = InetAddress.getByName(svr);
			Random random = new Random();
			byte t = (byte)((random.nextInt(0xF)+0xF0) % 0xFFFF);
			byte[] foo = {0x01, 0x02, t, 0x09, 
					0x00, 0x00, 0x00, 0x00, 0x00, 
					0x00, 0x00, 0x00, 0x00, 0x00, 
					0x00, 0x00, 0x00, 0x00, 0x00};
			if(flag==false){
				foo[1] = 0x03;
				foo[3] = 0x6e;
			}
			DatagramPacket sendPacket = new DatagramPacket(foo, foo.length, addr, 61440);
			client.send(sendPacket);
			
			byte[] v = new byte[76]; 
			DatagramPacket packet=new DatagramPacket(v, 76);
			client.receive(packet);
			if(v[0]==0x02){
				System.out.println("challenge packet received");
			}
			
			//String pack_foo = "";
			byte[] pack = new byte[4];
			System.arraycopy(v, 4, pack, 0, 4);
			
			return pack;
	}
}
