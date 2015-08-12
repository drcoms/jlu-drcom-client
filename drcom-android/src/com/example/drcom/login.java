package com.example.drcom;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class login {
	public static void dr_login(String usr, String ipaddr, String pwd, String svr, String mac) throws Exception{
		byte[] salt = challenge.dr_challenge(svr);
		/*for(int i=0;i<salt.length;i++){
			
			System.out.println(Integer.toHexString(salt[i]));
		}*/
		//String salt_foo = "c122a300";
		//byte[] salt = HexStr2ByteArr.hexStr2ByteArr(salt_foo);
		byte[] packet = mkpkt.dr_mkpkt(salt, ipaddr, usr, pwd, mac);
		DatagramSocket client = new DatagramSocket();
		InetAddress addr = InetAddress.getByName(svr);
		DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, addr, 61440);
		client.send(sendPacket);
		
		byte[] receive = new byte[45];
		DatagramPacket re_packet=new DatagramPacket(receive, 45);
		client.receive(re_packet);
		
		if(receive[0]==0x04){
			System.out.println(receive[0]);
		}else{
			System.out.println(receive[0]);
		}
	}

}
