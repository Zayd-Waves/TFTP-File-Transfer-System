/*  TFTPClient.java
 *  This class is the client side for a TFTP system.
 *
 *  UI asks for user for filename, R/W then reads
 *  or writes file. UI then asks user whether
 *  to transfer another file. If the user responds
 *  with no, the client will quit. UI checks all
 *  inputs it receives whenever it asks the user
 *  something against Q, V, M or H and responds
 *  accordingly.
 *  V: Toggle verboseness
 *  M: Select mode (normal or testing)
 *  CD: Change Directories
 *  Q: Quit and shut down client
 *  H: Help
 *  
 *  Send and receive function calls the read or write
 *  request function then sends a read or write
 *  request following TFTP protocols and waits for
 *  simulator/server response. Once response is
 *  received, send and receive function calls either
 *  send file or receive file functions depending on
 *  user's prior input.
 *  
 *  Send file function will read a file from system
 *  into a buffer and send the file in data packets
 *  to server following TFTP specifications. Between
 *  each packet client waits for a data acknowledge
 *  from server. Once the acknowledge is received
 *  client sends following packet. Once last packet
 *  is reached, if the packet is 516 bytes long,
 *  an empty packet is sent, otherwise a shorter
 *  packet is sent to indicate the file transfer is
 *  complete.
 *  
 *  Receive file function receives a file from the
 *  server, and will save as a new file to the
 *  project root. If the file already exists, the
 *  file will be saved as "(#) name". The file is
 *  received in 516 byte packets the same way files
 *  are sent, and these packets are placed in a
 *  buffer which writes the contents of the file to
 *  the file location in the project folder. Between
 *  each packet, the client sends a data acknowledge
 *  packet to server to signal that the file has been
 *  received. The termination of the file transfer is
 *  indicated the same way as with sending a file.
 *  (The last packet's data portion received is less
 *  than 512 bytes).
 *  
 *  Instructions to send a file (using sample.txt):
 *  Create sample.txt file. Put sample.txt is in
 *  main project folder. Press W to write file, enter
 *  file path as sample.txt.
 *  
 *  Instructions to receive a file (using sample.txt):
 *  Create sample.txt file. Put sample.txt is in
 *  main project folder. Press R to read file, enter
 *  file path as sample.txt. Server needs to be in a
 *  separate project folder to be able to see the
 *  successfully copied file (otherwise will just
 *  override file with the same contents).
 *  
 *  Error and error package handling is done through a 
 *  goes through a two step process. 
 *  
 *  Step 1: Determine if the packet is an ErrorPacket.
 *  		If the packet is an error packet, react appropriately.
 *  		This normally means ending the transfer unless the 
 *  		error is error code 5, in which case a recovery will
 *  		be attempted. 
 *  
 *  Step 2: Process the non-error packet and check for validity.
 *  		If the packet contains an error, an error packet will
 *  		be formed and sent back to the server. This will normally
 *  		require terminating the transfer, informing the user and 
 *  		prompting the user if they would like to do another
 *  		transfer. The exception, of course being error code 5. 
 *  
 *  Author:         Team 12 (Group Project - SYSC3303)
 *  Date:           5/19/2017
 */

import java.io.*;
import java.net.*;
import java.util.*;


public class TFTPClient {
	public static enum OPCODE {
		RRQ(1), WRQ(2), DATA(3), ACK(4), ERROR(5);

		private final int id;
		OPCODE(int id) {this.id = id;}
		public int value() {return id;}
	}

	// we can run in normal (send directly to server) or test
	// (send to simulator) mode

	public static enum Request {READ, WRITE, DATA, ACK, ERROR};
	public static enum Mode {NORMAL, TEST};
	public static enum Verbose { ON, OFF };
	public static enum RW {RRQ, WRQ };

	//Constants for packet type sizes
	private static final int TFTP_DATA_PACKET_SIZE = 516;
	private static final int TFTP_ACK_PACKET_SIZE = 4;

	private static final int SIM_RECV_PORT = 2300;
	private static final int SERVER_RECV_PORT = 6900;


	private static Verbose verbose = Verbose.ON; // DEFAULT ON
	private static Mode run = Mode.NORMAL; // DEFAULT NORMAL
	private static RW readWrite;

	private DatagramPacket sendPacket, receivePacket, errorPacket;
	private DatagramSocket sendReceiveSocket;
	private TFTPSocket sendSocket;
	private static InetAddress serverAddress;

	ArrayList<DatagramPacket> requests = new  ArrayList<DatagramPacket>();


	// Declaring static Scanners for file and user input
	private Scanner fileScanner = new Scanner( System.in );
	private Scanner responseScanner = new Scanner( System.in );
	private String filePath;
	private String directory = null;

	public TFTPClient() {
		sendReceiveSocket = bind();
	}

	/**
	 * Initializes connection to host or server, sends a RRQ or WRQ
	 * Then initializes data transfer
	 *
	 * @param file to read or write
	 */
	private void sendAndReceive(File file) {
		int sendPort;
		System.out.println("Client: Started");
		if (isVerbose()){
			System.out.println("Client: initializing Server and ErrorSimulator ports");
		}

		if (run == Mode.NORMAL){
			sendPort = SERVER_RECV_PORT;
		} else {
			sendPort = SIM_RECV_PORT;
		}

		// Send a read or a write request (depends on user selection)
		if (run == Mode.NORMAL) {
			sendReadOrWriteRequest(sendPort);
		} else if (run == Mode.TEST) {
			sendReadOrWriteRequest(sendPort);
		}

		if (readWrite == RW.RRQ){
			receiveFile(serverAddress, file);
		} else{
			sendFile(serverAddress, file);
		}
	}

	/**
	 * Function to send an RRQ or WRQ.
	 * Waits for response
	 *
	 * @param sendPort of host or simulator
	 */
	private void sendReadOrWriteRequest(int sendPort){
		byte[] msg = new byte[TFTP_DATA_PACKET_SIZE], // message we send
				fn, // filename as an array of bytes
				md; // mode as an array of bytes
		String filename, mode; // filename and mode as Strings
		int len;
		if (isVerbose()){
			System.out.println("Client: creating packet ");
		}

		filename = filePath;
		fn = filename.getBytes(); // convert to bytes

		byte opCode;
		if(readWrite == RW.RRQ){
			opCode = 1;
		} else{
			opCode = 2;
		}

		// Build byte array for packet
		System.arraycopy(fn,0,msg,2,fn.length); // Copy into the msg
		msg[fn.length+2] = 0; // Add 0 byte
		mode = "octet";
		md = mode.getBytes();
		System.arraycopy(md,0,msg,fn.length+3,md.length); // Copy mode into msg
		len = fn.length+md.length+4; // length of the message
		msg[len-1] = 0; // Add 0 byte
		msg[1] = opCode; // Add in opcode

		sendPacket = new DatagramPacket(msg, len, serverAddress, sendPort); // Construct packet to send to host

		if(isVerbose()){
			printPacketData(true, sendPacket);
		}

		// Send the datagram packet to the server via the send/receive socket.
		sendPacketToHost(sendReceiveSocket, sendPacket);

		if (isVerbose()){
			System.out.println("Client: Packet sent.\n");
		}  
	}


	/**
	 * Function to receive a data transfer
	 *
	 * @param address InetAddress to communicate with
	 * @param file to save as
	 * @param sendPort Port to communicate with server
	 */
	private void receiveFile(InetAddress address, File file){
		// Commencement of file transfer
		if (isVerbose()){
			System.out.println("\nCommencing file transfer: RRQ" );
		}
		int i = 1;
		/* Rename the file if it already exists. */
		File newFile = file;
		while(newFile.exists()){
			newFile = new File(new String(" (" + i + ") ") + file.getName());
			i++;
		}
		file = newFile;
		int blockNumber = 1;

		// Construct a DatagramPacket for receiving packets up
		// to 516 bytes long (the length of the byte array).
		try {
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
			byte[] fileData = new byte[TFTP_DATA_PACKET_SIZE];
			while(true){

				receivePacket = new DatagramPacket(fileData, fileData.length);

				if (isVerbose()){
					System.out.println("Client: Waiting for packet.");
				}
				// Client ACK/RRQ retransmission here
				int attempts = 0;
				while(true){
					sendReceiveSocket.setSoTimeout(3000);	
					try{
						sendReceiveSocket.receive(receivePacket);
						// If duplicate data, resend ACK
						if(getPacketNum(receivePacket) < blockNumber){
							sendPacketToHost(sendReceiveSocket, sendPacket);
							if(isVerbose()){
								System.out.println("\n Duplicate Packet Received. Sending ACK\n");
								printPacketData(true, sendPacket);
							}
							continue;
						} else if ((blockNumber == 0) && (getPacketNum(receivePacket) == 65535)){
							sendPacketToHost(sendReceiveSocket, sendPacket);
							if(isVerbose()){
								System.out.println("\n Duplicate Packet Received. Sending ACK\n");
								printPacketData(true, sendPacket);
							}
							continue;
						}
					} catch(SocketTimeoutException e){
						// Send the ACK packet via send/receive socket.
						sendPacketToHost(sendReceiveSocket, sendPacket);
						if(isVerbose()){
							System.out.println("Client: Timeout. Attempting packet Retransmission.\n");
							printPacketData(true, sendPacket);
						}
						attempts++;
						if(attempts == 10){
							System.out.println("Error with transfer: Time out" );
							out.close();
							return;
						}
						continue;
					}
					break;
				}
				int sendPort = receivePacket.getPort();
				requests.add(receivePacket);

				if(isVerbose()){
					printPacketData(false, receivePacket);
				}


				int error_code = -1;

				if(code5(receivePacket)){

					System.out.println("This is an error code 5");
					error_code  = 5;		
				}
				else{
					requests.add(receivePacket);
				}	


				/* Step 1: Determine if the packet is an ErrorPacket. */

				if (isErrorPacket(receivePacket)) {
					int code = parseErrorPacket(receivePacket);
					if (code == 4) { /* Signals that the the transfer should end. */
						out.close();
						return;
					} else if (code == 5) { /* Signals that we need to resend our last packet. (or something similar) */
						out.close();
						return;
						// todo: attempt to continue transfer.
					} else { // default behaviour for errors
						out.close();
						return;
					}


				}


				/* Step 2: Process the non-error packet and check for validity. */

				//  int error_code = -1; /* Default value. -1 means no error so far. */
				String errorMessage = "";

				if (!(isOpcodeValid(fileData, TFTPServerDispatcher.OPCODE.DATA.value()))) {
					error_code = 4;
					errorMessage = "Invalid opcode.";
				}

				if (/*getRequest(fileData) != Request.DATA*/ fileData[1] != 3) {
					error_code = 4;
					errorMessage = "Not a data packet.";
				}
				if (getPacketNum(receivePacket) != blockNumber ) {
					error_code = 4;
					errorMessage = "Invalid block number.";
					System.out.println("wrong block number" + fileData[3] + ", right block number: " + blockNumber);
				}

				if (error_code != -1) {
					if (error_code == 4) {
						/* We need to send the error packet, and terminate communication in the case of Error Code 4. */
						sendErrorPacket(error_code, errorMessage, receivePacket.getAddress(), receivePacket.getPort());
						out.close();
						return;
					} else {
						sendErrorPacket(error_code, errorMessage, receivePacket.getAddress(), receivePacket.getPort());
					}
				}

				// Write to file AFTER ensuring data valid
				try {
					out.write(fileData, 4, receivePacket.getLength() - 4);
				} catch (IOException ioe) {                    
					System.out.println("Error Code 3 Occurred: There is not enough space on the disk.");
					sendErrorPacket(3, "Disk full.", receivePacket.getAddress(), receivePacket.getPort());
					out.close();
					return;
				}

				// Form ACK packet
				byte dataACKPacket[] = {0, 4, receivePacket.getData()[2], receivePacket.getData()[3]};

				sendPacket = new DatagramPacket(dataACKPacket, dataACKPacket.length, serverAddress, sendPort);

				if(isVerbose()){
					printPacketData(true, sendPacket);
				}

				// Send the ACK packet via send/receive socket.
				sendPacketToHost(sendReceiveSocket, sendPacket);
				if(blockNumber == 65535){
					blockNumber = 0;
				} else{
					blockNumber++;
				}

				if (isVerbose()){
					System.out.println("Client: Packet sent.\n");
				}

				// This means it's the last packet
				if(receivePacket.getLength() < TFTP_DATA_PACKET_SIZE){
					break;
				}
			}
			out.close();
		}catch(IOException ioe){
			System.out.println("Issue with transfer\n"
					+ "File transfer could not be completed.");
			file.delete();
			return;
		}
		System.out.println("File transfer completed successfully");
	}

	/**
	 * Function to send a data transfer
	 *
	 * @param address InetAddress to communicate with
	 * @param file to save as
	 * @param sendPort Port to communicate with server
	 */
	private void sendFile(InetAddress address, File file){
		// Construct a DatagramPacket for receiving packets up
		// to 4 bytes long (the length of the byte array).
		byte[] data = new byte[TFTP_DATA_PACKET_SIZE];

		if (isVerbose()){
			System.out.println("Client: Waiting for acknowledgement packet from server.");
		}

		receivePacket = new DatagramPacket(data, data.length);

		if (isVerbose()){
			System.out.println("Client: Waiting for packet.");
		}
		// Client WRQ retransmission here
		int attempts = 0;
		try {
			while(true){
				sendReceiveSocket.setSoTimeout(1500);
				try{
					sendReceiveSocket.receive(receivePacket);
					break;
				} catch(SocketTimeoutException e){
					// Send the WRQ packet via send/receive socket.
					sendPacketToHost(sendReceiveSocket, sendPacket);
					if(isVerbose()){
						System.out.println("Client: Timeout. Attempting packet Retransmission.\n");
						printPacketData(true, sendPacket);
					}
					attempts++;
					if(attempts == 10){
						System.out.println("Error with transfer: Time out" );
						return;
					}
					continue;
				}
			}
		} catch(IOException e){
			System.out.println("Exception occured");
		}
        
		int sendPort = receivePacket.getPort();

		/* Step 1: Determine if the packet is an ErrorPacket. */

		if (isErrorPacket(receivePacket)) {
			int code = parseErrorPacket(receivePacket);
			if (code == 4) { /* Signals that the the transfer should end. */
				printPacketData(false, receivePacket);
				return;
			} else if (code == 5) { /* Signals that we need to resend our last packet. (or something similar) */
				printPacketData(false, receivePacket);
				return;
				// todo: attempt to continue transfer.
			} else if (code == 6){
				printPacketData(false, receivePacket);
				return;
			} else if (code == 1){
				printPacketData(false, receivePacket);
				return;
			} else if (code == 2){
				printPacketData(false, receivePacket);
				return;
			} else if (code == 3){
				printPacketData(false, receivePacket);
				return;
			}
		}


		/* Step 2: Process the non-error packet and check for validity. */

		int error_code = -1; /* Default value. -1 means no error so far. */
		String errorMessage = "";

		if (/*getRequest(data) != Request.ACK || getRequest(data) != Request.DATA*/ data[1] != 4 && data[1] != 3) {
			error_code = 4;
			errorMessage = "Not an ack packet.";
		}

		if (error_code != -1) {
			if (error_code == 4) {
				/* We need to send the error packet, and terminate communication in the case of Error Code 4. */
				sendErrorPacket(error_code, errorMessage, receivePacket.getAddress(), receivePacket.getPort());
				return;
			} else {
				sendErrorPacket(error_code, errorMessage, receivePacket.getAddress(), receivePacket.getPort());
				return;
			}
		}


		requests.add(receivePacket);

		if(isVerbose()){
			printPacketData(false, receivePacket);
		}




		// Commencement of file transfer
		if (isVerbose()){
			System.out.println("\nCommencing file transfer: WRQ" );
		}
		int packetNumber = 1;
		int blockNumber = 1;
		byte[] fileData = new byte[512]; // (Number of bytes for data)
		boolean empty = true;
		try {
            
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
			int n;

			while ((n = in.read(fileData,0,512)) != -1){
				empty = false;
				// Build a byte array to properly format packets
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				stream.reset();
				// Packet number is an int, need to turn into 2 bytes. Shift first one 8 bits right
				byte byteA = (byte) (packetNumber >>> 8);
				byte byteB = (byte) (packetNumber);
				try {
					stream.write(0);
					stream.write(3);
					stream.write(byteA);
					stream.write(byteB);
					stream.write(fileData);
				} catch (IOException e) {
					System.out.println("Problem with output stream");
					e.printStackTrace();
				}
				byte packet[] = stream.toByteArray(); // Create byte array to send in packet from stream
				stream.close();

				if(n < 512){ // Last block of data
					sendPacket = new DatagramPacket(packet, n+4, serverAddress, sendPort);
				} else{
					sendPacket = new DatagramPacket(packet, TFTP_DATA_PACKET_SIZE, serverAddress, sendPort);
				}
				if(isVerbose()){
					printPacketData(true, sendPacket);
				}

				// Send the datagram packet to the server via the send/receive socket.
				sendPacketToHost(sendReceiveSocket, sendPacket);

				if (isVerbose()){
					System.out.println("Client: Packet sent.\n");
				}

				// Construct a DatagramPacket for receiving packets up
				// to 4 bytes long (the length of the byte array).

				data = new byte[TFTP_ACK_PACKET_SIZE];
				receivePacket = new DatagramPacket(data, data.length);
				attempts = 0;

				while(true){
					if (isVerbose()){
						System.out.println("Client: Waiting for packet.");
					}
					sendReceiveSocket.setSoTimeout(1500);
					try{
						sendReceiveSocket.receive(receivePacket);
						if(/*(getRequest(receivePacket.getData())== Request.ACK)*/ receivePacket.getData()[1] == 4 && (getPacketNum(receivePacket) < blockNumber)){
							if(isVerbose()){
								System.out.println("\n Duplicate ACK Packet Received.\n");
							}
							continue;
						}
						break;
					} catch(SocketTimeoutException e){
						// Send the WRQ packet via send/receive socket.
						sendPacketToHost(sendReceiveSocket, sendPacket);
						if(isVerbose()){
							System.out.println("Client: Timeout. Attempting packet Retransmission.\n");
							printPacketData(true, sendPacket);
						}
						attempts++;
						if(attempts == 10){
							System.out.println("Error with transfer: Time out" );
							return;
						}
						continue;
					}
				}

				requests.add(receivePacket);

				if(isVerbose()){
					printPacketData(false, receivePacket);
				}


				error_code = -1;

				if(code5(receivePacket)){

					System.out.println("This is an error code 5");
					error_code  = 5;		
				}
				else{
					requests.add(receivePacket);
				}	

				/* Step 1: Determine if the packet is an ErrorPacket. */

				if (isErrorPacket(receivePacket)) {
					int code = parseErrorPacket(receivePacket);
					if (code == 4) { /* Signals that the the transfer should end. */
						return;
					} else if (code == 5) { /* Signals that we need to resend our last packet. (or something similar) */
						return;
						// todo: attempt to continue transfer.
					} else { // default behaviour for errors
						return;
					}
				}


				/* Step 2: Process the non-error packet and check for validity. */

				//   int error_code = -1; /* Default value. -1 means no error so far. */
				errorMessage = "";

				if (/*getRequest(data) != Request.ACK*/ data[1] != 4) {
					error_code = 4;
					errorMessage = "Invalid packet type.";
				}

				if (getPacketNum(receivePacket) != blockNumber) {
					error_code = 4;
					errorMessage = "Invalid block number.";
					System.out.println("wrong block number" + data[3] + ", right block number: " + blockNumber);
				}

				if (error_code != -1) {
					if (error_code == 4) {
						/* We need to send the error packet, and terminate communication in the case of Error Code 4. */
						sendErrorPacket(error_code, errorMessage, receivePacket.getAddress(), receivePacket.getPort());
						return;
					} else {
						sendErrorPacket(error_code, errorMessage, receivePacket.getAddress(), receivePacket.getPort());
					}
				}

				/*

	                    We've finished with the error checking. Proceed with processing the request.
	                    ____________________________________________________________________________

				 */



				if(packetNumber == 65535){
					packetNumber = 0;
				} else{
					packetNumber++;
				}
				if(blockNumber == 65535){
					blockNumber = 0;
				} else{
					blockNumber++;
				}
				fileData = new byte[TFTP_DATA_PACKET_SIZE]; // Clear data from block
			}
			in.close();
		} catch (FileNotFoundException ace) {
            /*
            java.io.FileNotFoundException: ServerOutput\tux.png (Access is denied)
                at java.io.FileInputStream.open0(Native Method)
                at java.io.FileInputStream.open(Unknown Source)
                at java.io.FileInputStream.<init>(Unknown Source)
                at TFTPClientConnection.sendFile(TFTPClientConnection.java:477)
                at TFTPClientConnection.run(TFTPClientConnection.java:236)
            */
			System.out.println("Error Code 2 - Access Violation.");
			sendErrorPacket(2, "Access Violation.", receivePacket.getAddress(), receivePacket.getPort());
			return;
		} catch(IOException ioe){
			System.out.println("Issue with transfer\n"
					+ "File transfer could not be completed.");
			return;
		}
		if((sendPacket.getLength() == 516) || empty){ // If last packet was 516 bytes, send one more
			// Build a byte array to properly format packets
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			stream.reset();
			// Packet number is an int, need to turn into 2 bytes. Shift first one 8 bits right
			byte byteA = (byte) (packetNumber >>> 8);
			byte byteB = (byte) (packetNumber);
			stream.write(0);
			stream.write(3);
			stream.write(byteA);
			stream.write(byteB);
			byte packet[] = stream.toByteArray(); // Create byte array to send in packet from stream
			try {
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			sendPacket = new DatagramPacket(packet, 4, serverAddress, sendPort);

			if(isVerbose()){
				printPacketData(true, sendPacket);
			}
			// Send the datagram packet to the server via the send/receive socket.
			sendPacketToHost(sendReceiveSocket, sendPacket);

			if (isVerbose()){
				System.out.println("Client: Packet sent.\n");
			}

			// Construct a DatagramPacket for receiving packets up
			// to 4 bytes long (the length of the byte array).

			data = new byte[TFTP_ACK_PACKET_SIZE];
			receivePacket = new DatagramPacket(data, data.length);

			if (isVerbose()){
				System.out.println("Client: Waiting for packet.");
			}

			try{ 
				while(true){
					if (isVerbose()) { System.out.println("Client: Waiting for packet."); }
					sendReceiveSocket.setSoTimeout(1500);
					try{
						sendReceiveSocket.receive(receivePacket);
						// Ignore duplicate ACKs
						if(/*(getRequest(receivePacket.getData()) == Request.ACK)*/ receivePacket.getData()[1] == 4 && (getPacketNum(receivePacket) < blockNumber)){
							if(isVerbose()){
								System.out.println("\n Duplicate ACK Packet Received. \n");
							}
							continue;
						}
					} catch(SocketTimeoutException e){
						// Send the Data packet via send/receive socket.
						sendPacketToHost(sendReceiveSocket, sendPacket);
						if(isVerbose()){
							System.out.println("Server: Timeout. Attempting packet Retransmission.\n");
							TFTPServer.printPacketData(true, sendPacket, false);
						}
						attempts++;
						if(attempts == 20){
							System.out.println("Error with transfer: Time out" );
							return;
						}
						continue;
					}
					break;
				}
			} catch(IOException e){
				e.printStackTrace();
			}


			if(isVerbose()){
				printPacketData(false, receivePacket);
			}

			// TODO: Check for errors
		}

		System.out.println("File transfer completed successfully");
	}


	/**
	 * Function to print details of a packet
	 *
	 * @param send: True if it is a packet to be sent, false if received
	 * @param packet: Datagram packet of which you want information
	 * @param printContents: Extensively prints byte contents of packet (For testing)
	 */
	private void printPacketData(boolean send, DatagramPacket packet){
		if(send){
			System.out.println("\nClient: Sending packet");
			System.out.println("To host: " + packet.getAddress());
		} else{
			System.out.println("Client: Packet received");
			System.out.println("From host: " + packet.getAddress());
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

	/**
	 * Function to parse through user input and check against possible
	 * client commands
	 *
	 * @param input string to parse
	 */
	public boolean userInput(String input){
		if(input.equals("Q") || input.equals("SHUTDOWN")){
			System.out.println("Shutting Down...");
			System.exit(1);
		} else if(input.equals("V") || input.equals("VERBOSE")){
			System.out.println("Verbose Mode on? (Y/N) (H for help)");
			while(true){ // Until the user response valid, keep asking
				String response = responseScanner.nextLine().toUpperCase();
				if(response.equals("Y")){
					verbose = Verbose.ON;
					System.out.println("Verbose Mode ON");
					break; // break out of loop
				} else if(response.equals("N")){
					verbose = Verbose.OFF;
					System.out.println("Verbose Mode OFF");
					break; // break out of loop
				} else if(response.equals("Q")){
					System.out.println("Shutting Down...");
					System.exit(1);
				} else if(response.equals("H")){
					System.out.println("TFTP CLIENT HELP"
							+ "\nThe following commands may be entered at any time"
							+ "\nV: Toggle verbosity"
							+ "\nM: Select mode (normal or testing)"
							+ "\nCD: Change Directories"
							+ "\nQ: Quit and shut down client"
							+ "\nH: Help");
					return true;
				} else{
					userInput(response);
					return true;
				}
			}
			return true;
		} else if(input.equals("M")){
			System.out.println("Normal or Test mode? (N/T) (H for help)");
			while(true){ // Until the user response valid, keep asking
				String response = responseScanner.nextLine().toUpperCase();
				if(response.equals("N")){
					run = Mode.NORMAL; // Send directly to server
					System.out.println("Normal Mode selected");
					break; // break out of loop
				} else if(response.equals("T")){
					run = Mode.TEST; // Send to simulator
					System.out.println("Test Mode selected");
					break; // break out of loop
				} else if(response.equals("Q")){
					System.out.println("Shutting Down...");
					System.exit(1);
				} else if(response.equals("H")){
					System.out.println("TFTP CLIENT HELP"
							+ "\nThe following commands may be entered at any time"
							+ "\nV: Toggle verbosity"
							+ "\nM: Select mode (normal or testing)"
							+ "\nCD: Change Directories"
							+ "\nQ: Quit and shut down client"
							+ "\nH: Help");
					return true;
				} else{
					userInput(response);
					return true;
				}
			}
			return true;
		} else if(input.equals("CD")){
			System.out.println("Please enter the directory you would like to change to");
			while(true){ // Until the user response valid, keep asking
				String response = responseScanner.nextLine();
				if(!userInput(response)){
					directory = response;
					return true;
				}
				return true;
			}
		}
		else if(input.equals("H")){
			System.out.println("TFTP CLIENT HELP"
					+ "\nThe following commands may be entered at any time"
					+ "\nV: Toggle verbosity"
					+ "\nM: Select mode (normal or testing)"
					+ "\nCD: Change Directories"
					+ "\nQ: Quit and shut down client"
					+ "\nH: Help");
			return true;
		}
		return false; // This means user may have attempted to enter an actual file path
	}

	/**
	 * Function to Start the UI
	 * Goes in a loop asking the use whether to RW, then asking for a file path and
	 * once the transfer succeeded or failed, asks whether user would like to transfer
	 * another file.
	 *
	 */
	public void start(){
		
		try { // Initialize server address to local host
			serverAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		System.out.println("Welcome the TFTP client (H for help)");
		
		while(true){ // Loops until user quits
			
			System.out.println("Please enter the IP address of the server (L for localhost):");
			while(true){ // Until the IP Address is valid, keeps requesting valid path
				String response = responseScanner.nextLine(); // Save input as filePath
				if(userInput(response.toUpperCase())){
					System.out.println("Please enter the server address:");
					continue; // If user input a change in mode, keep waiting for filePath
				} else if(response.toUpperCase().equals("L")){
					// R allow user to change selection from R/W after selection.
					System.out.println("LocalHost selected");
					break;
				} try {
					serverAddress = InetAddress.getByName(response);
					break;
				} catch (UnknownHostException e) {
					System.out.println("Error Code 5: Could not set address");
					e.printStackTrace();
				}
			}
			
			while(true){ // Until the user response valid, keep asking
				System.out.println("Would you like to read or write a file? (R/W) (H for help)");
				String response = responseScanner.nextLine().toUpperCase();
				if(response.equals("R")){
					readWrite = RW.RRQ; // Send directly to server
					System.out.println("Read selected. Press R to restart.");
					break; // break out of loop
				} else if(response.equals("W")){
					readWrite = RW.WRQ; // Send to simulator
					System.out.println("Write selected. Press R to restart.");
					break; // break out of loop
				} else{
					userInput(response);
				}
			}

			System.out.println("Please enter the path of the file \n(to change client directory, type 'CD' and follow prompts):");
			while(true){ // Until the path entered is valid, keeps requesting valid path
				filePath = fileScanner.nextLine(); // Save input as filePath
				if(userInput(filePath.toUpperCase())){
					System.out.println("Please enter the path for the input file:");
					continue; // If user input a change in mode, keep waiting for filePath
				} else if(filePath.toUpperCase().equals("R")){
					// R allow user to change selection from R/W after selection.
					System.out.println("Restarting...");
					break;
				}
				File file = new File(directory, filePath); // loads file
				// Only check if file exists locally if write selected
				if (readWrite == RW.WRQ) {
					if (file.exists()) { // Make sure file exists
						sendAndReceive(file);
						break; // break out of loop
					}
					System.out.println("Error Code 1: file does not exist  ");
					break;
				} else {
					sendAndReceive(file);
					break; // break out of loop
				}
			}

			while(true){ // Until the user response valid, keep asking
				System.out.println("Would you like to transfer another file? (Y/N) (H for help)");
				String response = responseScanner.nextLine().toUpperCase();
				if(response.equals("Y")){
					break; // break out of loop
				} else if(response.equals("N")){
					System.out.println("Shutting Down...");
					System.exit(1);
				} else {
					userInput(response);
				}
			}
		}
	}


	/**
	 * Returns a DatagramSocket bound to a provided port. Terminates
	 * the TFTPClient if a SocketException occurs.
	 *
	 * @param port number to bind to socket.
	 * @return DatagramSocket bound to a port.
	 */
	@SuppressWarnings("unused")
	private DatagramSocket bind(int port) {
		//Create new socket reference
		DatagramSocket socket = null;

		//Attempt to bind socket to port
		try {
			socket = new DatagramSocket(port);
		}
		catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		//Return bound socket
		return socket;
	}

	/**
	 * Returns a DatagramSocket bound to port selected by the host. Terminates
	 * the TFTPClient if a SocketException occurs.
	 *
	 * @return DatagramSocket bound to a port.
	 */
	private DatagramSocket bind() {
		//Create new socket reference
		DatagramSocket socket = null;

		//Attempt to bind socket
		try {
			socket = new DatagramSocket();
		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		//Return bound socket
		return socket;
	}

	/**
	 * Sends a given DatagramPacket to a host through a provided DatagramSocket.
	 * Terminates TFTPErrorSimulator if IOException occurs.
	 *
	 * @param socket to send through.
	 * @param packet to send.
	 */
	public static void sendPacketToHost(DatagramSocket socket, DatagramPacket packet) {
		try {
			socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Receives a DatagramPacket from a host through a provided DatagramSocket.
	 * Terminates TFTPErrorSimulator if an exception occurs.
	 *
	 * @param socket to receive the packet on.
	 * @param packet to receive.
	 */
	public static void receivePacketFromHost(DatagramSocket socket, DatagramPacket packet) {
		//Block until packet receives
		try {
			socket.receive(packet);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}




	/*
	 *
	 *
        The following methods deal with the checking and handling of faulty packets.
	 *
	 *
	 *
	 */


	private boolean isOpcodeValid(byte[] data, int validOpCode) {
		boolean valid = false;

		if (data[1] == validOpCode) {
			valid = true;
		}

		return valid;
	}

	private void sendErrorPacket(int errorCode, String errorMessage, InetAddress address, int port) {

		/*
            Structure of an error packet.
            _____________________________


            2 bytes     2 bytes      string    1 byte
             -----------------------------------------
            | Opcode |  ErrorCode |   ErrMsg   |   0  |
             -----------------------------------------
		 */

		/* Build byte array for packet. */
		byte[] errmsg = errorMessage.getBytes();
		byte[] errorData = new byte[errmsg.length + 5];

		errorData[0] = 0;
		errorData[1] = 5;
		errorData[2] = 0;
		errorData[3] = (byte) errorCode;
		System.arraycopy(errmsg, 0, errorData, 4, errmsg.length);
		errorData[errorData.length - 1] = 0;

		if (isVerbose()) { System.out.println("Formulating error packet: " + errorMessage + ", with error code: " + errorCode); }
		errorPacket = new DatagramPacket(errorData, errorData.length,
				address, port);

		if (isVerbose()) { System.out.println("Sending error packet."); }
		sendSocket = new TFTPSocket();
		sendSocket.sendPacket(errorPacket);
		//sendSocket.close();

	}

	private boolean isErrorPacket(DatagramPacket packet) {
		boolean isErrorPacket = false;
		Request req = getRequest(packet.getData());

		if (req == Request.ERROR) {
			isErrorPacket = true;
			if (isVerbose()) { System.out.println("Error packet received." ); }
		} else if (req == null) {
            isErrorPacket = false;
			if (isVerbose()) { System.out.println("Unknown packet type received." ); }
        }

		return isErrorPacket;
	}

	private int parseErrorPacket(DatagramPacket packet) {
		byte[] data = packet.getData();
		int errorCode = data[3];

		if (errorCode == 0) {
			/* not required for this iteration. */
		} else if (errorCode == 1) {
			if (isVerbose()) { System.out.println("Error code " + errorCode + " received." ); }
		} else if (errorCode == 2) {
			if (isVerbose()) { System.out.println("Error code " + errorCode + " received." ); }
		} else if (errorCode == 3) {
			if (isVerbose()) { System.out.println("Error code " + errorCode + " received." ); }
		} else if (errorCode == 4) {
			if (isVerbose()) { System.out.println("Error code " + errorCode + " received." ); }
		} else if (errorCode == 5) {
			if (isVerbose()) { System.out.println("Error code " + errorCode + " received." ); }
		} else if (errorCode == 6) {
			if (isVerbose()) { System.out.println("Error code " + errorCode + " received." ); }
		} else if (errorCode == 7) {
			/* not required for this iteration. */
		}

		return errorCode;
	}

	public boolean code5(DatagramPacket packet) {


		if(packet.getPort() != requests.get(requests.size()-1).getPort()){   
			String errorMessage =  "The TransferID is different";
			sendErrorPacket(5, errorMessage, packet.getAddress(), packet.getPort());
			return true;
		}

		if(!(packet.getAddress().equals(requests.get(requests.size()-1).getAddress()))){    
			String errorMessage =  "The The IP address is different";    	
			sendErrorPacket(5, errorMessage, packet.getAddress(), packet.getPort()); 
			return true;
		}
		return false;    
	}
	
	/**
	 * Method to get packet number of a packet
	 * 
	 * @param packet is the packet we need packet number from
	 * @return is the integer packet number extracted from the packet
	 */
	private int getPacketNum(DatagramPacket packet) {
		return (((int) (packet.getData()[2] & 0xFF)) << 8) + (((int) packet.getData()[3]) & 0xFF);
	}

	public static void toggleVerbosity() {
		if (verbose == Verbose.ON) {
			verbose = Verbose.OFF;
		} else {
			verbose = Verbose.ON;
		}
	}

	public static boolean isVerbose() {
		return (verbose == Verbose.ON);
	}

	private Request getRequest(byte[] data) {

		Request req;

		if (data[0] != 0)
			req = Request.ERROR; // bad
		else if (data[1] == OPCODE.RRQ.value())
			req = Request.READ; // could be read
		else if (data[1] == OPCODE.WRQ.value())
			req = Request.WRITE; // could be write
		else if (data[1] == OPCODE.DATA.value())
			req = Request.DATA;  // could be data
		else if (data[1] == OPCODE.ACK.value())
			req = Request.ACK; // could be ack
		else if (data[1] == OPCODE.ERROR.value()) 
			req = Request.ERROR;
        else
            req = null;

		return req;

	}

	public static void main(String args[]){
		TFTPClient c = new TFTPClient();
		c.start();
	}
}
