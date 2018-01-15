/*  TFTPPacket.java
 *  
 *  This class is used to create data packets for 
 *  transfers. It will store details of the packet and
 *  can be used to print data contained in the packet.
 *  
 *  Author:         Team 12 (Group Project - SYSC3303)
 *  Date:           5/19/2017
 */

import java.net.DatagramPacket;
import java.net.InetAddress;

public class TFTPPacket {
	public static enum OPCODE {
		RRQ(1), WRQ(2), DATA(3), ACK(4), ERROR(5);

		private final int id;
		OPCODE(int id) {this.id = id;}
		public int value() {return id;}
	}

	public static final int MIN_PACKET_SIZE = 4;
	public static final int MAX_PACKET_SIZE = 516;
	public static final int MAX_SEGMENT_SIZE = 512;

	private DatagramPacket packet;


	public TFTPPacket(byte[] data, int len) {
		packet = new DatagramPacket(data, len);
	}

	public TFTPPacket(byte[] data, int len, InetAddress addr, int port) {
		packet = new DatagramPacket(data, len, addr, port);
	}

	/**
	 * Function to print details of a packet
	 *
	 */
	@SuppressWarnings("unused")
	private void printPacketData(){
		int op = packet.getData()[1];

		if(op == OPCODE.RRQ.value()){
			System.out.println("Packet Type: RRQ");
		} else if(op == OPCODE.WRQ.value()){
			System.out.println("Packet Type: WRQ");
		} else if(op == OPCODE.DATA.value()){
			System.out.println("Packet Type: DATA");
		} else if(op == OPCODE.ACK.value()){
			System.out.println("Packet Type: ACK");
		} else if(op == OPCODE.ERROR.value()){
			System.out.println("Packet Type: ERROR");
		} else {
			System.out.println("ERROR: packet sent with unknown opcode");
		}

		System.out.println("Host port: " + packet.getPort());
		System.out.println("Length: " + packet.getLength());

		if((op == OPCODE.RRQ.value()) || (op == OPCODE.WRQ.value())){
			System.out.print("Filename: ");
			int i = 2;
			byte fName[] = new byte[packet.getLength()];
			byte mode[] = new byte[packet.getLength()];
			while(packet.getData()[i] != 0){
				fName[i-2] = packet.getData()[i];
				i++;
			}
			System.out.println(new String(fName));
			System.out.print("Mode: ");
			i++;
			int j = 0;
			while(packet.getData()[i] != 0){
				mode[j] = packet.getData()[i];
				i++;
				j++;
			}
			System.out.println(new String(mode));
		}

		if((op == OPCODE.ACK.value()) || (op == OPCODE.DATA.value())){
			System.out.print("Packet Number: ");
			// Extracts and prints packet number
			// Takes second 2 bytes of packet, shift MSB 8 bits left, bitmask then add.
			System.out.println((((int) (packet.getData()[2] & 0xFF)) << 8) + (((int) packet.getData()[3]) & 0xFF));
		}

		if(op == OPCODE.DATA.value()){
			System.out.println("Number of byte of data: " + (packet.getLength()-4));
		}


		if(op == OPCODE.ERROR.value()) {
			// TODO are these codes correct?
			System.out.println("Error code information: ");
			if(packet.getData()[3] == 0){
				System.out.println("Error code: 0");
				System.out.println("Not defined, see error message (if any).");
			} else if(packet.getData()[3] == 1){
				System.out.println("Error code: 1");
				System.out.println("File not found.");
			} else if(packet.getData()[3] == 2){
				System.out.println("Error code: 2");
				System.out.println("Access violation.");
			} else if(packet.getData()[3] == 3){
				System.out.println("Error code: 3");
				System.out.println("Disk full or allocation exceeded.");
			} else if(packet.getData()[3] == 4){
				System.out.println("Error code: 4");
				System.out.println("Illegal TFTP operation.");
			} else if(packet.getData()[3] == 5){
				System.out.println("Error code: 5");
				System.out.println("Unknown transfer ID.");
			} else if(packet.getData()[3] == 6){
				System.out.println("Error code: 6");
				System.out.println("File already exists.");
			} else if(packet.getData()[3] == 7){
				System.out.println("Error code: 7");
				System.out.println("No such user.");
			}
		}	
	}
}
