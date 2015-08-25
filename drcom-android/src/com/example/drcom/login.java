package com.example.drcom;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.Toast;

public class login {
	@SuppressLint("ShowToast")
	public static byte[] dr_login(String usr, String ipaddr, String pwd, String svr, String mac, Context context) throws Exception{
		byte[] salt = challenge.dr_challenge(svr, true);
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
			Toast.makeText(context, "登陆成功", Toast.LENGTH_SHORT).show();  
		}else{
			Toast.makeText(context, "登陆失败", Toast.LENGTH_SHORT).show();    
		}
		byte[] data = new byte[16];
		for(int i=23;i<39;i++){
			data[i-23] = receive[i];
			
		}
		return data;
	}


}
