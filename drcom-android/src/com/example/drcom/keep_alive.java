package com.example.drcom;
import java.util.Random;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class keep_alive {
	public static void keep(String svr) throws NumberFormatException, Exception{
		Random random = new Random();
		byte[] tail = new byte[4];
		
		int ran = random.nextInt(0xFFFF);
		ran = ran + 1 + random.nextInt(9);
		byte[] tail_foo = {0x00, 0x00, 0x00, 0x00};
		byte[] packet = keep_alive_package_builder.build(0, Int2ByteArr.intToByteArray(ran), tail_foo, 1, true);
		DatagramSocket client = new DatagramSocket();
		InetAddress addr = InetAddress.getByName(svr);
		DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, addr, 61440);
		client.send(sendPacket);
		
		ran = ran + 1 + random.nextInt(9);
		byte[] packet_2 = keep_alive_package_builder.build(1, Int2ByteArr.intToByteArray(ran), tail_foo, 1, false);
		DatagramSocket client_2 = new DatagramSocket();
		DatagramPacket sendPacket_2 = new DatagramPacket(packet_2, packet_2.length, addr, 61440);
		client_2.send(sendPacket_2);
		byte[] receive_2 = new byte[40];
		DatagramPacket re_packet_2=new DatagramPacket(receive_2, 40);
		client_2.receive(re_packet_2);
		for(int i=0;i<4;i++){
			tail[i] = receive_2[16+i];
			
		}
		
		ran = ran + 1 + random.nextInt(9);
		byte[] packet_3 = keep_alive_package_builder.build(2, Int2ByteArr.intToByteArray(ran), tail, 3, false);
		DatagramSocket client_3 = new DatagramSocket();
		DatagramPacket sendPacket_3 = new DatagramPacket(packet_3, packet_3.length, addr, 61440);
		client_3.send(sendPacket_3);
		byte[] receive_3 = new byte[40];
		DatagramPacket re_packet_3=new DatagramPacket(receive_3, 40);
		client_3.receive(re_packet_3);
		for(int i=0;i<4;i++){
			tail[i] = receive_3[16+i];
			
		}
		System.out.println("[keep-alive2] keep-alive2 loop was in daemon.");
		
		while(true){
			Thread.sleep(5000);
			//sleep 5
			ran = ran + 1 + random.nextInt(9);
			byte[] packet_4 = keep_alive_package_builder.build(2, Int2ByteArr.intToByteArray(ran), tail, 1, false);
			DatagramSocket client_4 = new DatagramSocket();
			DatagramPacket sendPacket_4 = new DatagramPacket(packet_4, packet_4.length, addr, 61440);
			client_4.send(sendPacket_4);
			byte[] receive_4 = new byte[40];
			DatagramPacket re_packet_4=new DatagramPacket(receive_4, 40);
			client_4.receive(re_packet_4);
			for(int i=0;i<4;i++){
				tail[i] = receive_4[16+i];
				
			}
			
			
			ran = ran + 1 + random.nextInt(9);
			byte[] packet_5 = keep_alive_package_builder.build(2, Int2ByteArr.intToByteArray(ran), tail, 3, false);
			DatagramSocket client_5 = new DatagramSocket();
			DatagramPacket sendPacket_5 = new DatagramPacket(packet_5, packet_5.length, addr, 61440);
			client_5.send(sendPacket_5);
			byte[] receive_5 = new byte[40];
			DatagramPacket re_packet_5=new DatagramPacket(receive_5, 40);
			client_5.receive(re_packet_5);
			for(int i=0;i<4;i++){
				tail[i] = receive_5[16+i];
				
			}
			
		}
		
	}
}
