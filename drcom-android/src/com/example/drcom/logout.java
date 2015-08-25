package com.example.drcom;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.content.Context;
import android.widget.Toast;

public class logout {
	public static void dr_logout(String usr, String ipaddr, String pwd, String svr, String mac, byte[] authinfo, Context context) throws Exception{
		byte[] salt = challenge.dr_challenge(svr, false);
		byte[] packet = mkopkt.dr_mkopkt(salt, ipaddr, usr, pwd, mac, authinfo);
		
		DatagramSocket client = new DatagramSocket();
		InetAddress addr = InetAddress.getByName(svr);
		DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, addr, 61440);
		client.send(sendPacket);
		
		byte[] receive = new byte[25];
		DatagramPacket re_packet=new DatagramPacket(receive, 25);
		client.receive(re_packet);
		
		if(receive[0]==0x04){
			Toast.makeText(context, "注销成功", Toast.LENGTH_SHORT).show();  
		}else{
			Toast.makeText(context, "注销失败", Toast.LENGTH_SHORT).show();  
		}
	}
}
