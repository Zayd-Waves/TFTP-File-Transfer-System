/*  TFTPClientConnection.java
 *  
 *  The ClientConnection class encapsulates the
 *  connection between the server and the client. It 
 *  is a thread used handle all communication with 
 *  one connection. For each new connection, a new 
 *  thread is created. 
 *  
 *  The client's network information is passed onto 
 *  this class via its constructor, and from there, 
 *  it maintains the connection for data transfers. 
 *  
 *  Error and error packet handling is done through a 
 *  3 level system. 
 *  
 *  Level 1: Processing the RRQ / WRQ Packet
 *  Level 2: Processing the packets received during a Write Request
 *  Level 3: Processing the packets received during a Read Request
 *  
 *  The following steps are followed during each level:
 *  
 *  1 - Run the data through a series of checks. 
 *  If the data is valid, we'll move on and 
 *  continue with responding to our client. If the 
 *  data is invalid in any way, it'll raise an 
 *  'error flag'. This means that one of the check 
 *  methods returned false.
 *  
 *  2 - Upon triggering an error flag, we categorize 
 *  the error Error Code 1 through 6. 
 *                      
 *  3 - Using the error code, we respond to the client 
 *  with an error packet and, depending on the error 
 *  code, terminate the connection.
 *
 *  Author:         Team 12 (Group Project - SYSC3303)
 *  Date:           5/19/2017
 */

import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.io.FileNotFoundException;



public class TFTPClientConnection extends Thread {
	/* Types of requests that we can receive. */
	public static enum Request {READ, WRITE, DATA, ACK, ERROR};

	//Constants for write/read responses
	public static final byte[] WRITE_RESP = {0, 4, 0, 0};
	public static final byte[] READ_RESP = {0, 3, 0, 1};

	/* Constants for packet type sizes. */
	private static final int TFTP_DATA_PACKET_SIZE = 516;
	private static final int TFTP_ACK_PACKET_SIZE = 4;

	/* Creates verbose initialized to 'OFF'. */
	private static Verbose verbose = Verbose.ON;

	/*Client connection attributes*/
	private DatagramPacket receivedPacket, sendPacket, errorPacket;
	private TFTPSocket sendSocket;
	private int packetNumber;
	private int threadNumber;
	private byte[] data;
	private InetAddress clientAddress;


	ArrayList<DatagramPacket> requests = new  ArrayList<DatagramPacket>();

	public TFTPClientConnection(int number, DatagramPacket packet, byte[] packetData, Verbose verb) {
		threadNumber = number;
		receivedPacket = packet;
		data = packetData;
		verbose = verb;
	}

	@Override
	public void run() {

		clientAddress = receivedPacket.getAddress();

		if (isVerbose()) { System.out.println("ClientConnectionThread number: " + threadNumber + " processing request."); }

		/*

            Error Checking Level 1: Processing the RRQ / WRQ Packet
            _______________________________________________________

            The 'data' variable here represents the first request that was received by the server and passed onto us.
            This means that before responding to the client in any way, we need to make sure that the data is valid.
            In other words, the first level of TFTP error checking occurs here. The steps are as follows:

                1 - Run the data through a series of checks. If the data is valid,
                    we'll move on and continue with responding to our client. If
                    the data is invalid in any way, it'll raise an 'error flag'.
                    This means that one of the check methods returned false.

                2 - Upon triggering an error flag, we categorize the error as either
                    an Error Code 4 or an Error Code 5 (for now). The following is a list
                    of all error codes:
                        - Error Code 0 (Not implemented)
                        - Error Code 1
                        - Error Code 2
                        - Error Code 3
                        - Error Code 4 
                        - Error Code 5 
                        - Error Code 6
                        - Error Code 7 (Not implemented)

                3 - Using the error code, we respond to the client with an error
                    packet and, depending on the error code, terminate the
                    connection.

		 */

		/* Step 1: Determine if the packet is an ErrorPacket. */




		if (isErrorPacket(receivedPacket)) {
			int code = parseErrorPacket(receivedPacket);
			if (code == 4) { /* Signals that the the transfer should end. */
				return;
			} else if (code == 5) { /* Signals that we need to resend our last packet. (or something similar) */
				return;
				// todo: attempt to continue transfer.
			}
		}


		/* Step 2: Process the non-error packet and check for validity. */

		int error_code = -1; /* Default value. -1 means no error so far. */
		String errorMessage = ""; /* Default, empty error message. */

		if (!(isOpcodeValid(data, TFTPServerDispatcher.OPCODE.RRQ.value())) && !(isOpcodeValid(data, TFTPServerDispatcher.OPCODE.WRQ.value()))) {
			error_code = 4;
			errorMessage = "Invalid opcode.";
		}
		if (!(isModeValid(data, receivedPacket.getLength()))) {
			error_code = 4;
			errorMessage = "Invalid mode.";
		}
		if (!(isFilenameValid(data, receivedPacket.getLength()))) {
			error_code = 4;
			errorMessage = "Invalid filename.";
		}

		if (error_code != -1) {
			if (error_code == 4) {
				/* We need to send the error packet, and terminate communication in the case of Error Code 4. */
				sendErrorPacket(error_code, errorMessage, receivedPacket.getAddress(), receivedPacket.getPort());
				return;
			} else {
				sendErrorPacket(error_code, errorMessage, receivedPacket.getAddress(), receivedPacket.getPort());
			}
		}


		/*
            We've finished with the error checking. Proceed with processing the request.
            ____________________________________________________________________________

		 */


		requests.add(receivedPacket);
		Request req = getRequest(data);
		String filename = "";
		int len = receivedPacket.getLength();
		int j = 0, k = 0;

		if (!isError(req)) {
			for(j = 2; j < len; j++) {
				if (data[j] == 0) break;
			}

			filename = new String(data, 2, j-2);

			for(k = j+1; k < len; k++) {
				if (data[k] == 0) break;
			}
		}

		if (k != len - 1) { req = Request.ERROR; }



		if (req == Request.READ) {
			if (isVerbose()) { System.out.println(req + " request received."); }
		} else if (req == Request.WRITE) {
			if (isVerbose()) { System.out.println(req + " request received."); }
		} else {
			if (isVerbose()) { System.out.println(req + " request received."); }
			sendErrorPacket(4, "Invalid filename", receivedPacket.getAddress(), receivedPacket.getPort());
			return;
		}

		// ERROR CODE 6 & 2

		// first check if we can open the file without an access violation (error code 2)
		// then check to see if the file already exists (error code 6)

		File file;
		try {
			file = new File(TFTPServer.getDirectory(), filename);
		} catch (SecurityException ace) {
			System.out.println("Error Code 2 - Access Violation.");
			sendErrorPacket(2, "Access Violation.", receivedPacket.getAddress(), receivedPacket.getPort());
			return;
		}
		if(file.exists() && (req == Request.WRITE)) {
			System.out.println("ClientConnection: Error Code 6 - File already exists.");
			sendErrorPacket(6, "File already exists", receivedPacket.getAddress(), receivedPacket.getPort());
			return;
		}
		// END OF ERROR CODE 6 & 2

		if(isVerbose()){
			System.out.println();
			TFTPServer.printPacketData(false, receivedPacket, false);
		}

		if (req == Request.READ) {
			if (isVerbose()) { System.out.println("\nClientConnection: Read Request."); }
			if(file.exists() && !file.isDirectory()) {
				sendFile(receivedPacket.getAddress(), file, receivedPacket.getPort());
			} else {
				System.out.println("Error Code 1: Can't read file. Does not exist on server.");
				sendErrorPacket(1, "Can't read file. Does not exist on server.", receivedPacket.getAddress(), receivedPacket.getPort());
				sendSocket.close();
				return;
			}
		} else if (req == Request.WRITE) {
			if (isVerbose()) { System.out.println("\nClientConnection: Write Request."); }
			receiveFile(receivedPacket.getAddress(), filename, receivedPacket.getPort());
		}
	}

	private void receiveFile(InetAddress address, String filename, int sendPort) {
		TFTPSocket socket = new TFTPSocket();
		if (isVerbose()) { System.out.println("ClientConnection: Commencing file transfer...\n" ); }
		byte[] writeResp = new byte[]{0, 4, 0, 0};
		DatagramPacket sendPacket = new DatagramPacket(writeResp, 4, address, sendPort);
		if (isVerbose()) {
			TFTPServer.printPacketData(true, sendPacket, false);
		}
		socket.sendPacket(sendPacket);

		if (isVerbose()) { System.out.println("Server: Packet sent.\n"); }

		/* Throw error if file already exists. */
		File file = new File(TFTPServer.getDirectory(), filename);
		int blockNumber = 1;
		try {
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
			while(true) {


				byte[] fileData = new byte[TFTP_DATA_PACKET_SIZE];
				receivedPacket = new DatagramPacket(fileData, 516);


				// Server ACK retransmission here
				int attempts = 0;
				while(true){
					if(isVerbose()){
						System.out.println("Server: Waiting for packet");
					}
					socket.getDatagramSocket().setSoTimeout(3000);	
					try{
						socket.getDatagramSocket().receive(receivedPacket);
						// If duplicate data, resend ACK
						if(getPacketNum(receivedPacket) < blockNumber){
							socket.sendPacket(sendPacket);
							if(isVerbose()){
								System.out.println("\n Duplicate DATA Packet Received. Sending ACK\n");
								TFTPServer.printPacketData(true, sendPacket, false);
							}
							continue;
						} else if ((blockNumber == 0) && (getPacketNum(receivedPacket) == 65535)){
							socket.sendPacket(sendPacket);
							if(isVerbose()){
								System.out.println("\n Duplicate DATA Packet Received. Sending ACK\n");
								TFTPServer.printPacketData(true, sendPacket, false);
							}
							continue;
						}

					} catch(SocketTimeoutException e){
						// Send the ACK packet via send/receive socket.
						socket.sendPacket(sendPacket);
						if(isVerbose()){
							System.out.println("Server: Timeout. Attempting packet Retransmission.\n");
							TFTPServer.printPacketData(true, sendPacket, false);
						}
						attempts++;
						if(attempts == 10){
							System.out.println("Error with transfer: Time out" );
							System.exit(0);
						}
						continue;
					}
					break;
				}

				if(isVerbose()){
					TFTPServer.printPacketData(false, receivedPacket, false);
				}


				int error_code = -1;

				if(code5(receivedPacket)){

					System.out.println("This is an error code 5");
					error_code  = 5;		
				}
				else{
					requests.add(receivedPacket);
				}	

				/*

                    Error Checking Level 2: Processing the packets received during a Write Request
                    _______________________________________________________

                    The 'receivePacket' variable here represents the next packet received from the Client during a
                    Write Request. Before sending the next ACK packet, this is the level where we ensure the data
                    we received is valid. This is the second level of our TFTP error checking framework. The steps are
                    as follows:

                        1 - Run the data through a series of checks. If the data is valid,
                            we'll move on and continue with responding to our client. If
                            the data is invalid in any way, it'll raise an 'error flag'.
                            This means that one of the check methods returned false.

                        2 - Upon triggering an error flag, we categorize the error as either
                            an Error Code 4 or an Error Code 5 (for now). The following is a list
                            of all error codes:
                                - Error Code 0 (Not implemented)
                                - Error Code 1
                                - Error Code 2
                                - Error Code 3
                                - Error Code 4 
                                - Error Code 5 
                                - Error Code 6
                                - Error Code 7 (Not implemented)

                        3 - Using the error code, we respond to the client with an error
                            packet and, depending on the error code, terminate the
                            connection.

				 */

				/* Step 1: Determine if the packet is an ErrorPacket. */

				if (isErrorPacket(receivedPacket)) {

					System.out.println("\n\n Error packet received\n\n ");
					int code = parseErrorPacket(receivedPacket);
					out.close();
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

				/* Default value. -1 means no error so far. */
				String errorMessage = "";




				//				if (!(isOpcodeValid(data, TFTPServerDispatcher.OPCODE.DATA.value()))) {
				//					error_code = 4;
				//					errorMessage = "Invalid opcode.";
				//				}
				//				
				if (!(isOpcodeValid(data, TFTPServerDispatcher.OPCODE.DATA.value())) && !(isOpcodeValid(data, TFTPServerDispatcher.OPCODE.WRQ.value()))) {
					error_code = 4;
					errorMessage = "Invalid opcode.";
				}



				if (getRequest(fileData) != Request.DATA ) {
					error_code = 4;
					errorMessage = "Invalid opCode.";
				}
				if (getPacketNum(receivedPacket) != blockNumber ) {
					error_code = 4;
					errorMessage = "Invalid block number.";
				}

				if (error_code != -1) {
					if (error_code == 4) {
						/* We need to send the error packet, and terminate communication in the case of Error Code 4. */
						sendErrorPacket(error_code, errorMessage, receivedPacket.getAddress(), receivedPacket.getPort());
						out.close();
						return;
					} else {
						sendErrorPacket(error_code, errorMessage, receivedPacket.getAddress(), receivedPacket.getPort());
					}
				}

				/*

                    We've finished with the error checking. Proceed with processing the request.
                    ____________________________________________________________________________

				 */


				try {
					out.write(receivedPacket.getData(), 4, receivedPacket.getLength()-4);
				} catch (IOException ioe) {

					System.out.println("Error Code 3 Occurred: There is not enough space on the disk.");
					sendErrorPacket(3, "Disk full.", receivedPacket.getAddress(), receivedPacket.getPort());
					out.close();
					return;

				}


				packetNumber = ((receivedPacket.getData()[2] & 0xFF) << 8) + receivedPacket.getData()[3];

				/* Our response. */
				byte dataACKPacket[] = {0, 4, receivedPacket.getData()[2], receivedPacket.getData()[3]};

				sendPacket = new DatagramPacket(dataACKPacket, dataACKPacket.length, clientAddress, sendPort);

				if(blockNumber == 65535){
					blockNumber = 0;
				} else{
					blockNumber++;

				}


				if (isVerbose()) {
					TFTPServer.printPacketData(true, sendPacket, false);
				}

				socket.sendPacket(sendPacket);




				if (isVerbose()) { System.out.println("Server: Packet sent.\n"); }

				/* This means it's the last packet. */
				if(receivedPacket.getLength() < TFTP_DATA_PACKET_SIZE) { break; }

			}

			out.close();
		} catch (SecurityException ace) {
			System.out.println("Error Code 2 - Access Violation.");
			sendErrorPacket(2, "Access Violation.", receivedPacket.getAddress(), receivedPacket.getPort());
			return;
		} catch (FileNotFoundException ioe) {
			System.out.println("Error Code 2 - Access Violation");
			sendErrorPacket(2, "Access Violation", receivedPacket.getAddress(), receivedPacket.getPort());
			return;
		} catch(IOException ioe) {
			System.out.println("ClientConnection: Issue with transfer. File transfer could not be completed.");
			// ioe.printStackTrace();
			file.delete();
			return;
		}
		System.out.println("File transfer completed successfully");
	}

	private void sendFile(InetAddress address, File file, int sendPort) {
		TFTPSocket socket = new TFTPSocket();
		if (isVerbose()) { System.out.println("\nCommencing file transfer...\n" ); }
		byte[] fileData = new byte[512];
		boolean empty = true;
		int blockNumber;
		try {
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
			int n;
			packetNumber = 1;
			blockNumber = 0;


			while ((n = in.read(fileData,0,512)) != -1) {


				empty = false;
				/* Build a byte array to properly format packets. */
				ByteArrayOutputStream stream = new ByteArrayOutputStream();
				stream.reset();

				/* Packet number is an int, need to turn into 2 bytes. Shift first one 8 bits right. */
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

				/* Create byte array to send in packet from stream. */
				byte packetData[] = stream.toByteArray();
				stream.close();

				if(n < 512) { /* Last block of data. */
					sendPacket = new DatagramPacket(packetData, n+4, clientAddress, sendPort);
				} else {
					sendPacket = new DatagramPacket(packetData, TFTP_DATA_PACKET_SIZE, clientAddress, sendPort);
				}
				if(isVerbose()) {
					TFTPServer.printPacketData(true, sendPacket, false);
				}

				/* Send the datagram packet to the client via the send/receive socket. */
				socket.sendPacket(sendPacket);
				if(blockNumber == 65535){
					blockNumber = 0;
				} else{
					blockNumber++;
				}

				if (isVerbose()) { System.out.println("Server: Packet sent.\n"); }

				/*
                    Construct a DatagramPacket for receiving packets up
                    to 4 bytes long (the length of the byte array).
				 */

				byte[] data = new byte[TFTP_ACK_PACKET_SIZE];
				receivedPacket = new DatagramPacket(data, data.length);

				int attempts = 0;
				while(true){
					if (isVerbose()) { System.out.println("Server: Waiting for packet."); }
					socket.getDatagramSocket().setSoTimeout(1500);
					try{
						socket.getDatagramSocket().receive(receivedPacket);
						// Ignore duplicate ACKs
						if((getRequest(receivedPacket.getData()) == Request.ACK) && (getPacketNum(receivedPacket) < blockNumber)){
							if(isVerbose()){
								System.out.println("\n Duplicate ACK Packet Received.");
							}
							continue;
						}
					} catch(SocketTimeoutException e){
						// Send the Data packet via send/receive socket.
						socket.sendPacket(sendPacket);
						if(isVerbose()){
							System.out.println("Server: Timeout. Attempting packet Retransmission.\n");
							TFTPServer.printPacketData(true, sendPacket, false);
						}
						attempts++;
						if(attempts == 20){
							System.out.println("Error with transfer: Time out" );
							System.exit(0);
						}
						continue;
					}
					break;
				}


				int error_code = -1;
				if(code5(receivedPacket)){
					error_code  = 5;	      	
				}
				else{  
					requests.add(receivedPacket); 

				}

				if (isVerbose()) {
					TFTPServer.printPacketData(false, receivedPacket, false);
				}


				/*

                    Error Checking Level 3: Processing the packets received during a Read Request
                    _______________________________________________________

                    The 'receivePacket' variable here represents the next packet received from the Client during a
                    Read Request. Before sending the next DATA packet, this is the level where we ensure the ACK packet
                    we received is valid. This is the third level of our TFTP error checking framework. The steps are
                    as follows:

                        1 - Run the data through a series of checks. If it is a valid ACK packet,
                            we'll move on and continue with transferring data to our client. If
                            the packet is invalid in any way, it'll raise an 'error flag'.
                            This means that one of the check methods returned false.

                        2 - Upon triggering an error flag, we categorize the error as either
                            an Error Code 4 or an Error Code 5 (for now). The following is a list
                            of all error codes:
                                - Error Code 0 (Not implemented)
                                - Error Code 1
                                - Error Code 2
                                - Error Code 3
                                - Error Code 4 
                                - Error Code 5
                                - Error Code 6
                                - Error Code 7 (Not implemented)

                        3 - Using the error code, we respond to the client with an error
                            packet and, depending on the error code, terminate the
                            connection.

				 */

				/* Step 1: Determine if the packet is an ErrorPacket. */

				if (isErrorPacket(receivedPacket)) {
					int code = parseErrorPacket(receivedPacket);
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

				/* Default value. -1 means no error so far. */
				String errorMessage = "";

				if (getRequest(data) != Request.ACK) {
					error_code = 4;
					errorMessage = "Invalid packet type.";
				}

				if (getPacketNum(receivedPacket) != blockNumber) {
					error_code = 4;
					errorMessage = "Invalid block number.";
					System.out.println("wrong block number" + data[3] + ", right block number: " + blockNumber);
				}

				if (error_code != -1) {
					if (error_code == 4) {
						/* We need to send the error packet, and terminate communication in the case of Error Code 4. */
						sendErrorPacket(error_code, errorMessage, receivedPacket.getAddress(), receivedPacket.getPort());
						return;
					} else {
						sendErrorPacket(error_code, errorMessage, receivedPacket.getAddress(), receivedPacket.getPort());
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
				/* Clear data from block. */
				fileData = new byte[TFTP_DATA_PACKET_SIZE];
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
			sendErrorPacket(2, "Access Violation.", receivedPacket.getAddress(), receivedPacket.getPort());
			return;
		}catch(IOException ioe) {
			ioe.printStackTrace();
			System.out.println("Issue with transfer" + "File transfer could not be completed.");
			return;
		}

		/* If last packet was 516 bytes, send one more. */
		if((sendPacket.getLength() == 516) || empty) {

			/* Build a byte array to properly format packets. */
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			stream.reset();

			/* Packet number is an int, need to turn into 2 bytes. Shift first one 8 bits right. */
			byte byteA = (byte) (packetNumber >>> 8);
			byte byteB = (byte) (packetNumber);
			stream.write(0);
			stream.write(3);
			stream.write(byteA);
			stream.write(byteB);

			/* Create byte array to send in packet from stream. */
			byte packet[] = stream.toByteArray();
			try {
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			sendPacket = new DatagramPacket(packet, 4, clientAddress, sendPort);

			if(isVerbose()){
				TFTPServer.printPacketData(true, sendPacket, false);
			}

			/* Send the datagram packet to the server via the send/receive socket. */
			socket.sendPacket(sendPacket);


			if (isVerbose()) { System.out.println("Server: Packet sent.\n"); }

			/*
                Construct a DatagramPacket for receiving packets up
                to 4 bytes long (the length of the byte array).
			 */

			byte[] data = new byte[TFTP_ACK_PACKET_SIZE];
			receivedPacket = new DatagramPacket(data, data.length);
			int attempts = 0;
			try{ 
				while(true){
					if (isVerbose()) { System.out.println("Server: Waiting for packet."); }
					socket.getDatagramSocket().setSoTimeout(3000);
					try{
						socket.getDatagramSocket().receive(receivedPacket);
						// Ignore duplicate ACKs
						if((getRequest(receivedPacket.getData()) == Request.ACK) && (getPacketNum(receivedPacket) < blockNumber)){
							if(isVerbose()){
								System.out.println("\n Duplicate ACK Packet Received.\n");
							}
							continue;
						}
					} catch(SocketTimeoutException e){
						// Send the Data packet via send/receive socket.
						socket.sendPacket(sendPacket);
						if(isVerbose()){
							System.out.println("Server: Timeout. Attempting packet Retransmission.\n");
							TFTPServer.printPacketData(true, sendPacket, false);
						}
						attempts++;
						if(attempts == 20){
							System.out.println("Error with transfer: Time out" );
							System.exit(0);
						}
						continue;
					}
					break;
				}
			} catch(IOException e){
				e.printStackTrace();
			}
			if(isVerbose()){
				TFTPServer.printPacketData(false, receivedPacket, false);
			}
		}

		System.out.println("File transfer completed successfully");

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



	private boolean isFilenameValid(byte[] data, int length) {
		boolean valid = true;

		String filename = "";
		int j = 0;
		for(j = 2; j < length; j++) {
			if (data[j] == 0) break;
		}

		filename = new String(data, 2, j - 2);

		/* Check to see if the string is empty. */
		if (!(filename.trim().length() > 0)) {
			valid = false;
		}

		/* Check to see if the string contains Windows' invalid characters for filenames. */
		for (int i = 0; i < filename.length(); i++) {
			if (filename.charAt(i) == '/' ||
					filename.charAt(i) == '/' ||
					filename.charAt(i) == '\\' ||
					filename.charAt(i) == ':' ||
					filename.charAt(i) == '*' ||
					filename.charAt(i) == '?' ||
					filename.charAt(i) == '\"' ||
					filename.charAt(i) == '<' ||
					filename.charAt(i) == '>' ||
					filename.charAt(i) == '|') {
				valid = false;
			}

		}

		return valid;

	}

	private boolean isModeValid(byte[] data, int length) {
		boolean valid = true;

		String mode = "";
		int j = 0, k = 0;
		for (j = 2; j < length; j++) {
			if (data[j] == 0) { break; }
		}

		for(k = j+1; k < length; k++) {
			if (data[k] == 0) { break; }
		}

		mode = new String(data, j + 1, k - j - 1);

		if ((mode.equals("netascii")) || (mode.equals("octet"))) {
			valid = true;
		} else {
			valid = false;
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
		if(isVerbose()){
			TFTPServer.printPacketData(true, errorPacket, false);
		}
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


		if (packet.getPort() != requests.get(requests.size()-1).getPort()){  
			String errorMessage =  "The TransferID is different";
			sendErrorPacket(5, errorMessage, packet.getAddress(), packet.getPort());
			return true;
		}

		if(!(packet.getAddress().equals(requests.get(requests.size()-1).getAddress()))){      
			String errorMessage =  "IP address is different";    	
			sendErrorPacket(5, errorMessage, packet.getAddress(), packet.getPort()); 
			return true;
		}
		return false;    
	}

	private boolean isError(Request req) {
		return (req == Request.ERROR);
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

		Request req = null;


		if (data[0] != 0)
			req = Request.ERROR; // bad
		else if (data[1] == TFTPServerDispatcher.OPCODE.RRQ.value())
			req = Request.READ; // could be read
		else if (data[1] == TFTPServerDispatcher.OPCODE.WRQ.value())
			req = Request.WRITE; // could be write
		else if (data[1] == TFTPServerDispatcher.OPCODE.DATA.value())
			req = Request.DATA;  // could be data
		else if (data[1] == TFTPServerDispatcher.OPCODE.ACK.value())
			req = Request.ACK; // could be ack
		else if (data[1] == TFTPServerDispatcher.OPCODE.ERROR.value())
			req = Request.ERROR;

		return req;

	}
}
