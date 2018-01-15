/*  TFTPServer.java
 *  
 *  This class is the server side of a TFTP server 
 *  based on UDP/IP. The server receives a read or 
 *  write packet from a client and sends back the
 *  appropriate response without any actual file
 *  transfer. One socket (69) is used to receive 
 *  (it stays open) and another for each response.
 *  
 *  This class will initialize the server dispatcher
 *  which listens for a communication on port 69. 
 *  The server dispatcher creates a new client 
 *  connection thread for each new connection it
 *  receives. 
 *  
 *  The following commands may be entered at any time:
 *  
 *  V or VERBOSE: Toggle verboseness
 *  Q or QUIT: Quit and shut down server
 *  
 *  Note that the server will stop any new
 *  connections if it receives a quit command. It
 *  will then wait for all current connections to 
 *  terminate and once that is complete, terminate
 *  the server. 
 *  
 *  Author:         Team 12 (Group Project - SYSC3303)
 *  Date:           5/19/2017
 */

import java.util.Scanner;
import java.net.*;

public class TFTPServer {

	private static TFTPServer instance = null;

	private TFTPServerDispatcher serverWaitThread;
	@SuppressWarnings("unused")
	private boolean initialized;
	private static String directory = "ServerOutput";

	/* Creates verbose initialized to 'OFF'. */
	private static Verbose verbose = Verbose.ON;

	private TFTPServer() { initialized = false; }

	/**
	 * Returns the singleton instance of the TFTPErrorSimulator. If the 
	 * TFTPErrorSimulator hasn't been instantiated yet, this function will
	 * create a new instance and return a reference to it.
	 * 
	 * @return reference to TFTPErrorSimulator instance.
	 */
	public static TFTPServer instanceOf() {
		//If instance not instantiated, instantiate.
		if (instance == null)
			instance = new TFTPServer();

		//Return reference to singleton instance.
		return instance;
	}

	public void receiveAndSendTFTP() {
		System.out.println("Server Started.");
		Scanner scanner = new Scanner(System.in);
		serverWaitThread = new TFTPServerDispatcher();
		serverWaitThread.start();
		initialized = true;

		while (scanner.hasNextLine()) {
			String command = scanner.nextLine().toUpperCase();
			if (command.equals("SHUTDOWN") || command.equals("Q")) {
				System.out.println("Server: Shutdown command received. Completing remaining connection threads and shutting down.");
				serverWaitThread.killThread();
				scanner.close();
				return; 
			} else if (command.equals("VERBOSE") || command.equals("V")) {
				if (isVerbose()) {
					System.out.println("Server: Verbose mode off.");
					toggleVerbosity();
				} else {
					System.out.println("Server: Verbose mode on.");
					toggleVerbosity();
				}
			} else if(command.equals("CD")){
				System.out.println("Please enter the directory you would like to change to");
				directory = scanner.nextLine();
				System.out.println("Directory changed.");
			}
		}

		scanner.close();
	}
	
	public static String getDirectory(){
		return directory;
	}
	
	public void toggleVerbosity() {
		if (verbose == Verbose.ON) {
			verbose = Verbose.OFF;
			serverWaitThread.toggleVerbosity();
		} else {
			verbose = Verbose.ON;
			serverWaitThread.toggleVerbosity();
		}
	}

	public static boolean isVerbose() {
		return (verbose == Verbose.ON);
	}

	/**
	 * Function to print details of a packet
	 * 
	 * @param send: True if it is a packet to be sent, false if received
	 * @param packet: Datagram packet of which you want information
	 * @param printContents: Extensively prints byte contents of packet (For testing)
	 */
	public static void printPacketData(boolean send, DatagramPacket packet, boolean printContents){
		if(send){
			System.out.println("\nServer: Sending packet");
			System.out.println("To Host: " + packet.getAddress());
		} else{
			System.out.println("Server: Packet received");
			System.out.println("From Host: " + packet.getAddress());
		}

		if(packet.getData()[1] == 1){
			System.out.println("Packet Type: RRQ");
		} else if(packet.getData()[1] == 2){
			System.out.println("Packet Type: WRQ");
		} else if(packet.getData()[1] == 3){
			System.out.println("Packet Type: DATA");
		} else if(packet.getData()[1] == 4){
			System.out.println("Packet Type: ACK");
		} else if(packet.getData()[1] == 5){
			System.out.println("Packet Type: ERROR");
		} else {
			System.out.println("ERROR: packet sent with unknown opcode");
		}

		System.out.println("Host port: " + packet.getPort());
		System.out.println("Length: " + packet.getLength());

		if((packet.getData()[1] == 1) || (packet.getData()[1] == 2)){			
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

		if((packet.getData()[1] == 3) || (packet.getData()[1] == 4)){
			System.out.print("Packet Number: ");
			// Extracts and prints packet number 
			// Takes second 2 bytes of packet, shift MSB 8 bits left, bitmask then add.
			System.out.println((((int) (packet.getData()[2] & 0xFF)) << 8) + (((int) packet.getData()[3]) & 0xFF));
		}

		if(packet.getData()[1] == 3){
			System.out.println("Number of byte of data: " + (packet.getLength()-4));
		}

		if(packet.getData()[1] == 5){
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

	public static void main(String args[]) {
		TFTPServer server = TFTPServer.instanceOf();
		server.receiveAndSendTFTP();
	}

}