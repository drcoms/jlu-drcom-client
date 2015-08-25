package com.example.drcom;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class logout {
	public static void dr_logout(String usr, String ipaddr, String pwd, String svr, String mac) throws Exception{
		byte[] salt = challenge.dr_challenge(svr, false);
		byte[] packet = mkopkt.dr_mkopkt(salt, ipaddr, usr, pwd, mac);
		
		DatagramSocket client = new DatagramSocket();
		InetAddress addr = InetAddress.getByName(svr);
		DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, addr, 61440);
		client.send(sendPacket);
		
		byte[] receive = new byte[25];
		DatagramPacket re_packet=new DatagramPacket(receive, 25);
		client.receive(re_packet);
		
		if(receive[0]==0x04){
			System.out.println(receive[0]);
		}else{
			System.out.println(receive[0]);
		}
	}
}
