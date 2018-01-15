/*  TFTPSim.java
 *  This class is an error simulator for a TFTP 
 *  server based on UDP/IP. One socket (23) is
 *  used to receive from the client, and another 
 *  to send/receive from the server.  A new socket 
 *  is used for each communication back to the client.
 *  
 *  Once started the simulator will ask the user if 
 *  they would like to simulate an error. The user 
 *  must answer a series of prompts specifying what 
 *  kind of error they would like to simulate. 
 *  Depending on the error, the user can specify
 *  exactly which byte in which block they
 *  would like to change, and to what. Once the user 
 *  creates the error, the information is saved
 *  and placed in a priority queue (using a comparator 
 *  based on lowest packet number). The user is then 
 *  asked whether they would like to simulate another 
 *  error (on the same transfer). The user can create 
 *  as many alterations to a transfer as they like. 
 *  These alterations are processed by packet number. 
 *  Once the simulation has started, the main function 
 *  will pass on packets from client to server and
 *  server to client. Once a packet is received 
 *  from either the client or the server, the simulator
 *  will check the packet number and check if there is 
 *  an alteration with that number at the beginning of
 *  the queue. It will then ensure that the packet 
 *  alteration in the queue specifies the same direction 
 *  it is intercepting (server to client vs. client to 
 *  server). This is all checked in the checkForAlterations
 *  function. If the packet and direction correspond, 
 *  instead of sending the packet normally, the packet is
 *  sent to the SimulateErrors function which will then take
 *  over the responsibilities for passing on the packets. 
 *  
 *  In the SimulateErrors function, the object holding
 *  the specifications for handling errors is removed from
 *  the queue and the alterations/simulation will be 
 *  handled accordingly. For example, if a packet is 
 *  specified to change the opcode, the function will
 *  create a new packet changing the "opcode field" to 
 *  the specified value, send the altered packet normally, 
 *  and continue from the main program waiting to receive
 *  the next packet. Another possible simulation would be 
 *  sending a duplicate packet. In this case the simulator
 *  will send the unaltered packet twice. 
 *   
 *  To restart the simulator, you must close the simulator
 *  and follow the prompts to specify any errors one would
 *  like to produce. 
 *  
 *  Please note, after entering all the alterations you 
 *  desire for a specific transfer, you must enter 'N' 
 *  when prompted if you would like to add another alteration. 
 *  If not, the error packet will not be able to receive 
 *  packets. 
 *  
 *  Author:         Team 12 (Group Project - SYSC3303)                  
 *  Date:           5/19/2017
 */
import java.io.*;
import java.net.*;
import java.util.*;


public class TFTPErrorSimulator {
	public static enum Verbose {ON, OFF};
	public static enum OPCODE {
		RRQ(1), WRQ(2), DATA(3), ACK(4), ERROR(5);

		private final int id;
		OPCODE(int id) {this.id = id;}
		public int getValue() {return id;}
	}

	/*Error Simulator's Singleton Instance*/
	private static TFTPErrorSimulator instance = null;

	/* Types of requests that we can receive. */
	public static enum Request {READ, WRITE, DATA, ACK, ERROR};

	//Constants for port numbers
	private static final int CLIENT_RECV_PORT = 2300;
	private static final int SERVER_RECV_PORT = 6900;

	//Verbose flag
	public static Verbose verbose = Verbose.ON;

	// UDP datagram packets and sockets used to send / receive
	private DatagramPacket sendPacket, receivePacket;
	private TFTPSocket receiveSocket, sendReceiveSocket;

	private Scanner responseScanner = new Scanner( System.in );

	// Holds the current port number for the server's communication thread.
	private int serverCommunicationPort;
	private int clientPort;
	private InetAddress clientAddress;
	private InetAddress serverAddress;
	private AlterationPriority altPriority = new AlterationPriority();
	private PriorityQueue<PacketAlteration> alterationQueue = new PriorityQueue<PacketAlteration>(altPriority);

	//ErrorSim is singleton. Private to defeat instantiation
	private TFTPErrorSimulator() {
		receiveSocket = new TFTPSocket(CLIENT_RECV_PORT);
		sendReceiveSocket = new TFTPSocket();
	}

	/**
	 * Returns the singleton instance of the TFTPErrorSimulator. If the 
	 * TFTPErrorSimulator hasn't been instantiated yet, this function will
	 * create a new instance and return a reference to it.
	 * 
	 * @return reference to TFTPErrorSimulator instance.
	 */
	public static TFTPErrorSimulator instanceOf() {
		//If instance not instantiated, instantiate.
		if (instance == null)
			instance = new TFTPErrorSimulator();

		//Return reference to singleton instance.
		return instance;
	}

	/**
	 * Handles the Server/Client logic of the TFTPErrorSimulator
	 * 
	 */
	public void passOnTFTP() {
		System.out.println("Error Simulator started.");
		//variables for handling client connection
		byte[] data;

		try {
			serverAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		System.out.println("Error Simulator running.");

		//Create new packet to receive data
		data = new byte[1000];
		receivePacket = new DatagramPacket(data, 1000);

		if (isVerbose())
			System.out.println("\nSimulator: Waiting for packet.");

		// Block until a datagram packet is received from receiveSocket.

		// CLIENT PACKET RECEIVE
		receiveSocket.receivePacket(receivePacket);
		clientPort = receivePacket.getPort();
		clientAddress = receivePacket.getAddress();
		// Output packet information
		if (isVerbose()) {
			printPacketData(false, receivePacket);
		}

		if (checkForAlteration(receivePacket, true)){
			simulateErrors(receivePacket);
		}
		else{	
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverAddress, SERVER_RECV_PORT);

			// Print packet information
			if (isVerbose()) {
				printPacketData(true, sendPacket);
			}

			// SEND PACKET TO SERVER
			sendReceiveSocket.sendPacket(sendPacket);
		}

		//Construct a DatagramPacket for receiving packets
		data = new byte[1000];
		receivePacket = new DatagramPacket(data, 1000);

		if (isVerbose()){
			System.out.println("\nSimulator: Waiting for packet.");
		}

		// SERVER PACKET RECEIVE
		sendReceiveSocket.receivePacket(receivePacket);


		// This is the port number for the ClientCommunicationThread that the server created.
		serverCommunicationPort = receivePacket.getPort();

		// Process the received datagram.
		if (isVerbose()) {
			printPacketData(false, receivePacket);
		}

		if (checkForAlteration(receivePacket, false)){
			simulateErrors(receivePacket);
		} else{
			//Construct new packet to send in response to client
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientAddress, clientPort);

			if (isVerbose()) {
				printPacketData(true, receivePacket);
			}
			// SEND PACKET TO CLIENT
			receiveSocket.sendPacket(sendPacket);
		}

		while (true) { 
			data = new byte[1000];
			if (isVerbose()){
				System.out.println("\nSimulator: Waiting for packet.");
			}

			receivePacket = new DatagramPacket(data, 1000);

			// CLIENT PACKET RECEIVE
			receiveSocket.receivePacket(receivePacket);

			//Output packet information 
			if (isVerbose()) {
				printPacketData(false, receivePacket);
			}

			if (checkForAlteration(receivePacket, true)){
				simulateErrors(receivePacket);
			}
			else{
				//Create new packet to send
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverAddress, serverCommunicationPort);

				//Print packet information
				if (isVerbose()) {
					printPacketData(true, sendPacket);
				}

				// SEND PACKET TO SERVER
				sendReceiveSocket.sendPacket(sendPacket);
			}

			//Construct a DatagramPacket for receiving packets
			data = new byte[1000];
			receivePacket = new DatagramPacket(data, 1000);

			if (isVerbose()){
				System.out.println("\nSimulator: Waiting for packet.");
			}

			// RECEIVE PACKET FROM SERVER
			sendReceiveSocket.receivePacket(receivePacket);

			// Process the received datagram.
			if (isVerbose()) {
				printPacketData(false, receivePacket);
			}

			if (checkForAlteration(receivePacket, false)){
				simulateErrors(receivePacket);
			} else{	

				//Construct new packet to send in response to client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientAddress, clientPort);

				if (isVerbose()) {
					printPacketData(true, receivePacket);
				}
				// SEND PACKET TO CLIENT
				receiveSocket.sendPacket(sendPacket);
			}
		} // Outer while
	}

	/**
	 * Prints the TFTP packet information of a provided DatagramPacket to standard output.
	 * 
	 * @param send flag of whether or not the packet is a send packet.
	 * @param packet to be print.
	 */
	private void printPacketData(boolean send, DatagramPacket packet) {
		//If send packet is sendPacket, else is recvPacket
		if(send){
			System.out.println("\nSimulator: Sending packet");
			System.out.println("To host: " + packet.getAddress());
		} else{
			System.out.println("Simulator: Packet received");
			System.out.println("From host: " + packet.getAddress());
		}

		Request req = getRequest(packet.getData());

		//Get packet type
		if(req == Request.READ){
			System.out.println("Packet Type: RRQ");
		} else if(req == Request.WRITE){
			System.out.println("Packet Type: WRQ");
		} else if(req == Request.DATA){
			System.out.println("Packet Type: DATA");
		} else if(req == Request.ACK){
			System.out.println("Packet Type: ACK");
		}



		//Output host port and packet length
		System.out.println("Host port: " + packet.getPort());
		System.out.println("Length: " + packet.getLength());

		if((req == Request.READ) || (req == Request.WRITE)){			
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

		if((req == Request.DATA) || (req == Request.ACK)){
			System.out.print("Packet Number: ");
			// Extracts and prints packet number 
			// Takes second 2 bytes of packet, shift MSB 8 bits left, bitmask then add.
			System.out.println(((packet.getData()[2] & 0xFF) << 8) + packet.getData()[3]);
		}

		if(req == Request.DATA){
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
	 * Method to check if packet is one of the packets in our list of 
	 * alterations. It also ensures we are altering it in the correct
	 * direction (client to server vs. server to client).
	 * 
	 * @param packet is the packet we are checking
	 * @param direction True if client to server, false if server to client
	 * @return true if the packet needs to be altered, false if not.
	 */
	public boolean checkForAlteration(DatagramPacket packet, boolean direction){
		if(alterationQueue.isEmpty()){
			return false;
		}
		// If we are altering an RRQ/WRQ, packetNumber is -1
		if((alterationQueue.peek().getPacketNumber() == -1) && (direction == alterationQueue.peek().getDirection())){
			return true;
		}
		if((getPacketNum(packet) == alterationQueue.peek().getPacketNumber()) && (direction == alterationQueue.peek().getDirection())){
			return true;
		} 
		return false;
	}

	/**
	 * Method to simulate possible errors. Pulls instructions for the 
	 * error from the queue and acts accordingly. Creates packets, alters
	 * them and sends them on. Can do things such changing opcodes, 
	 * sending duplicates etc. 
	 * 
	 * @param packet is the packet we will alter
	 */
	public void simulateErrors(DatagramPacket packet){
		// This is the first alteration in Queue
		PacketAlteration alteration = alterationQueue.remove();
		int port;
		if((alteration.getPacketNumber() == -1) && (alteration.getDirection() == true)){
			port = SERVER_RECV_PORT; 
		} else if(alteration.getDirection() == true){
			// Send alteration to server
			port = serverCommunicationPort;
		} else{
			// Send alteration to client
			port = clientPort;
		}
		if(alteration.getDirection()){
			sendPacket = new DatagramPacket(packet.getData(), packet.getLength(), serverAddress, port);
		} else{
			sendPacket = new DatagramPacket(packet.getData(), packet.getLength(), clientAddress, port);
		}
		

		// Check if we need to change a specific byte
		if ((alteration.getByteNumber() != -1) && (alteration.getByteValue() != -1)) { 
			changePacketByte(sendPacket, alteration.getByteNumber(), alteration.getByteValue());
		}

		if (alteration.getNewPacketNumber() != -1) {
			changeBlockNum(sendPacket, alteration.getNewPacketNumber());
		}

		if (alteration.getMode() != null) {
			changeModeBytes(sendPacket, alteration.getMode());
		}

		if (alteration.getNewSize() != -1) {
			changePacketSize(sendPacket, alteration.getNewSize());
		}

		if (alteration.getOpCode() != -1) {
			changeOpCode(sendPacket, alteration.getOpCode());
		}

		if (alteration.getFileName() != null) {
			changeFileNameBytes(sendPacket, alteration.getFileName());
		}
		
		// End of packet construction

		if (isVerbose()) {
			printPacketData(true, sendPacket);
		}

		// Creates new temporary socket with desired TID
		if(alteration.getTid() != -1){
			TFTPSocket tempSocket = new TFTPSocket(alteration.getTid());
			tempSocket.sendPacket(sendPacket);
			return;
		}

		// Creates new temporary socket with desired InetAddress
		if(alteration.getAddress() != null){
			TFTPSocket tempSocket = new TFTPSocket(sendReceiveSocket.getPort(), alteration.getAddress());
			tempSocket.sendPacket(sendPacket);
			return;
		}

		// Delay the packet
		if(alteration.getDelay() != -1){
			try {
				System.out.println("Delaying for: " + alteration.getDelay() + " milliseconds");
				Thread.sleep(alteration.getDelay());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Lose the packet
		if(alteration.getLosePacket()){
			System.out.println("Sending Cancelled, lost the packet.");
			//Construct a DatagramPacket for receiving packets
			byte data[] = new byte[1000];
			receivePacket = new DatagramPacket(data, 1000);

			if (isVerbose()){
				System.out.println("\nSimulator: Waiting for packet.");
			}

			if(alteration.getDirection()){
				// RECEIVE PACKET FROM Client
				receiveSocket.receivePacket(receivePacket);
			} else{ // Server
				sendReceiveSocket.receivePacket(receivePacket);
			}
			if (isVerbose()) {
				printPacketData(false, receivePacket);
			}
			if(alteration.getDirection()){
				// Send packet for server
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverAddress, port);
			} else{ // Client
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientAddress, port);
			}
			
			
			if (isVerbose()) {
				printPacketData(true, sendPacket);
			}
		}

		// SEND PACKET
		if(alteration.getDirection()){
			// To server
			sendReceiveSocket.sendPacket(sendPacket);
		} else{ // Client
			receiveSocket.sendPacket(sendPacket);
		}


		if(alteration.getDuplicate()){
			try {
				System.out.println("Delaying for: " + alteration.getDuplicateDelay() + " milliseconds");
				Thread.sleep(alteration.getDuplicateDelay());
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (isVerbose()){
				System.out.println("Sending Duplicate Packet");
				printPacketData(true, sendPacket);
			}
			// SEND PACKET
			if(alteration.getDirection()){
				// server
				sendReceiveSocket.sendPacket(sendPacket);
			} else{ // client
				receiveSocket.sendPacket(sendPacket);
			}
		}
	}


	/**
	 * Checks if verbose flag is set.
	 * 
	 * @return true if verbose, else false.
	 */
	private boolean isVerbose() {
		return (verbose == Verbose.ON);
	}

	/**
	 * Parses an array of byte data for the purpose of determining what type of request it contains.
	 * 
	 * @param data
	 * @return
	 */
	private Request getRequest(byte[] data) {
		Request req;

		if (data[0] != 0) 
			req = Request.ERROR; // bad
		else if (data[1] == OPCODE.RRQ.getValue()) 
			req = Request.READ; // could be read
		else if (data[1] == OPCODE.WRQ.getValue()) 
			req = Request.WRITE; // could be write
		else if (data[1] == OPCODE.DATA.getValue())
			req = Request.DATA;  // could be data
		else if (data[1] == OPCODE.ACK.getValue())
			req = Request.ACK; // could be ack
		else 
			req = Request.ERROR;

		return req;
	}

	/**
	 * A menu to scan for specific user entries
	 * 
	 * @param input string
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
				} else{
					userInput(response);
					return true;
				}
			}
			return true;
		} else if(input.equals("H")){
			System.out.println("TFTP SIMULATOR HELP"
					+ "\nThe following commands may be entered at any time"
					+ "\nV: Toggle verbosity"
					+ "\nQ: Quit and shut down Simulator"
					+ "\nH: Help");
			return true;
		}
		return false;
	}

	/**
	 * An extensive error menu to alter transfers in some way
	 * 
	 */
	public void errorMenu(){
		System.out.println("TFTP SIMULATOR ERROR MENU"
				+ "\n1: Alter a packet"
				+ "\n2: Change the TID of a packet"
				+ "\n3: Simulate Network Errors"
				+ "\n0: Cancel and start error simulator");
		while(true){
			System.out.println("\nPlease input your selection:");
			String response = responseScanner.nextLine().toUpperCase();
			if(response.equals("1")){
				System.out.println("Alter a packet selected");
				errorMenu1();
			} else if(response.equals("2")){
				System.out.println("Change the TID of a packet selected");
				errorMenu2();
			} else if(response.equals("3")){
				System.out.println("Simulate Network Errors selected");
				errorMenu3();
			} else if(response.equals("0")){
						break;
			} else if(userInput(response)){ // Check against keys
				continue;
			} else{
				System.out.println("Please enter a valid command");
			}
		}
	}

	/**
	 * An error menu to create an illegal FTP operation
	 * 
	 */
	public void errorMenu1(){
		System.out.println("Please select one of the following 8 options:"
				+ "\n1: Alter Opcode of a Packet"
				+ "\n2: Change the Block Number of a Packet"
				+ "\n3: Alter the Mode of a WRQ or RRQ"
				+ "\n4: Change the Size of a Packet"
				+ "\n5: Alter a Specific Byte in a Packet"
				+ "\n6: Change the filename of a WRQ or RRQ"
				+ "\n0: Return to Error Menu");
		while(true){
			PacketAlteration alteration = new PacketAlteration();
			String response = responseScanner.nextLine().toUpperCase();
			if(response.equals("1")){
				System.out.println("1 selected");
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setOpCode(newOpcode());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("2")){
				System.out.println("2 selected");
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setNewPacketNumber(newPacketNumber());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("3")){
				System.out.println("3 selected");
				alteration.setDirection(true);
				alteration.setPacketNumber(-1);
				alteration.setMode(newMode());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("4")){
				System.out.println("4 selected");
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setNewSize(newSize());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("5")){
				System.out.println("5 selected");
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setByteNumber(byteNumber());
				alteration.setByteValue(newByte());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("6")){
				System.out.println("6 selected");
				alteration.setDirection(true);
				alteration.setPacketNumber(-1);
				alteration.setFileName(newFileName());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("0")){
				errorMenu();
			} else if(userInput(response)){
				continue;
			} else{ 
				System.out.println("Please enter a valid command");
			}
		}
	}

	/**
	 * An error menu to create an unknown TID error
	 * 
	 */
	public void errorMenu2(){
		System.out.println("Please select one of the following 3 options:"
				+ "\n1: Alter TID (Change Port)"
				+ "\n2: Change Address"
				+ "\n0: Return to Error Menu");
		while(true){
			PacketAlteration alteration = new PacketAlteration();
			String response = responseScanner.nextLine().toUpperCase();
			if(response.equals("1")){
				System.out.println("1 selected"); 
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setTid(newTID());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("2")){
				System.out.println("2 selected");
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setAddress(newAddress());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("0")){
				errorMenu();
			} else if(userInput(response)){
				continue;
			} else{
				System.out.println("Please enter a valid command");
			}
		}
	}

	/**
	 * An error menu to create an unknown TID error
	 * 
	 */
	public void errorMenu3(){
		System.out.println("Please select one of the following 3 options:"
				+ "\n1: Delay a Packet"
				+ "\n2: Duplicate a Packet"
				+ "\n3: Lose a Packet"
				+ "\n0: Return to Error Menu");
		while(true){
			PacketAlteration alteration = new PacketAlteration();
			String response = responseScanner.nextLine().toUpperCase();
			if(response.equals("1")){
				System.out.println("1 selected"); 
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setDuplicateDelay(delay());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("2")){
				System.out.println("2 selected");
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setDuplicate(true);
				alteration.setDuplicateDelay(delay());
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("3")){
				System.out.println("3 selected"); 
				alteration.setDirection(clientToServer());
				alteration.setPacketNumber(packetNumber());
				alteration.setLosePacket(true);
				alterationQueue.add(alteration);
				nextError();
			} else if(response.equals("0")){
				errorMenu();
			} else if(userInput(response)){
				continue;
			} else{
				System.out.println("Please enter a valid command");
			}
		}
	}

	/**
	 * Asks where to send/intercept a packet
	 * 
	 * @return true from client to server, returns false from server to client
	 */
	public boolean clientToServer(){
		System.out.println("Which direction?"
				+ "\n1: Client to Server"
				+ "\n2: Server to Client"
				+ "\n0: Return to Error Menu");
		while(true){
			String response = responseScanner.nextLine().toUpperCase();
			if(!userInput(response)){
				if(response.equals("1")){
					System.out.println("Client to Server selected"); 
					return true;
				} else if(response.equals("2")){
					System.out.println("Server to Client selected");
					return false;
				} else if(response.equals("0")){
					errorMenu();
				} else{
					System.out.println("Please enter a valid command");
				}
			}
		}
	}

	/**
	 * Asks which packet you would like to alter.
	 * 
	 * @return the integer entered by user
	 */
	public int packetNumber(){
		System.out.println("Which packet number? (For first packet 'WRQ' or 'RRQ' enter 0)");
		while(true){
			String response = responseScanner.nextLine().toUpperCase();
			if(!userInput(response)){
				try{
					if(response.equals("0")){
						response = "-1";
					}
					return Integer.parseInt(response);
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid number");
				}
			}
		}
	}

	/**
	 * Asks what you would like to change packet number to
	 * 
	 * @return the integer entered by user
	 */
	public int newPacketNumber(){
		System.out.println("What would you like the new packet number to be?");
		while(true){
			String response = responseScanner.nextLine().toUpperCase();
			if(!userInput(response)){
				try{
					return Integer.parseInt(response);
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid number");
				}
			}
		}
	}

	/**
	 * Asks what you would like to change size of packet to
	 * 
	 * @return the integer entered by user
	 */
	public int newSize(){
		System.out.println("What size would you like to make the packet?");
		while(true){
			String response = responseScanner.nextLine().toUpperCase();
			if(!userInput(response)){
				try{
					return Integer.parseInt(response);
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid number");
				}
			}
		}
	}

	/**
	 * Asks what you which byte user would like to change
	 * 
	 * @return the integer entered by user
	 */
	public int byteNumber(){
		System.out.println("Which byte would you like to alter? (Enter byte number)");
		while(true){
			String response = responseScanner.nextLine().toUpperCase();
			if(!userInput(response)){
				try{
					return Integer.parseInt(response);
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid number");
				}
			}
		}
	}

	/**
	 * Asks what you would like to change byte to
	 * 
	 * @return the byte value entered by user
	 */
	public byte newByte(){
		System.out.println("What would you like to change the byte to?");
		while(true){
			String response = responseScanner.nextLine().toUpperCase();
			if(!userInput(response)){
				try{
					return Byte.parseByte(response);
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid Byte");
				}
			}
		}
	}

	/**
	 * Asks what you would like to change the opcode to
	 * 
	 * @return the byte entered by user
	 */
	public byte newOpcode(){
		System.out.println("What would you like to change the Opcode to?");
		while(true){
			String response = responseScanner.nextLine().toUpperCase();
			if(!userInput(response)){
				try{
					return Byte.parseByte(response);
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid integer");
				}
			}
		}
	}

	/**
	 * Asks what you would like to change the mode to
	 * 
	 * @return the String entered by user
	 */
	public String newMode(){
		System.out.println("What would you like to change the Mode to?");
		return responseScanner.nextLine();
	}

	/**
	 * Asks what you would like to change file name to
	 * 
	 * @return the string entered by user
	 */
	public String newFileName(){
		System.out.println("What would you like to change the filename to?");
		return responseScanner.nextLine();
	}

	/**
	 * Asks what you would like to change port to
	 * 
	 * @return the integer entered by user
	 */
	public int newTID(){
		System.out.println("What would you like to change the port to?");
		while(true){
			String response = responseScanner.nextLine().toUpperCase();
			if(!userInput(response)){
				try{
					return Integer.parseInt(response);
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid integer");
				}
			}
		}
	}

	/**
	 * Asks what you would like to change InetAddress to
	 * 
	 * @return the integer entered by user
	 */
	public String newAddress(){
		System.out.println("What would you like to change the InetAddress to?");
		while(true){
			String response = responseScanner.nextLine();
			if(!userInput(response)){
				try{
					return response;
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid String");
				}
			}
		}
	}

	/**
	 * Asks what you would like to change delay to
	 * 
	 * @return the integer entered by user
	 */
	public int delay(){
		System.out.println("What would you like the delay to be (in milliseconds!!)?");
		while(true){
			String response = responseScanner.nextLine();
			if(!userInput(response)){
				try{
					return Integer.parseInt(response);
				} catch(NumberFormatException e){
					System.out.println("Please enter a valid integer");
				}
			}
		}
	}

	/**
	 * UI to ask user whether to create another error. Starts waiting
	 * for packets if not. 
	 * 
	 */
	public void nextError(){
		while(true){ // Loops until user quits
			while(true){ // Until the user response valid, keep asking
				System.out.println("Would you like to simulate another error? (Y/N) (H for help)");
				String response = responseScanner.nextLine().toUpperCase();
				if(response.equals("Y")){
					errorMenu();
					break; // break out of loop
				} else if(response.equals("N")){
					break; // break out of loop
				} else if(userInput(response)){
					continue;
				} else{
					System.out.println("Please enter a valid response");
				}
			}
			break;
		}
		System.out.println("Error Simulator Ready \nBegin transfer at any time");
		passOnTFTP();
	}

	/**
	 * UI to ask user whether to create an error. Starts waiting
	 * for packets if not. 
	 * 
	 */
	public void start(){
		System.out.println("Welcome the TFTP Error Simulator (H for help)");
		while(true){ // Loops until user quits
			while(true){ // Until the user response valid, keep asking
				System.out.println("Would you like to simulate an error? (Y/N) (H for help)");
				String response = responseScanner.nextLine().toUpperCase();
				if(response.equals("Y")){
					errorMenu();
					break; // break out of loop
				} else if(response.equals("N")){
					break; // break out of loop
				} else if(userInput(response)){
					continue;
				} else{
					System.out.println("Please enter a valid response");
				}
			}
			break;
		}
		System.out.println("Error Simulator Ready \nBegin transfer at any time");
		passOnTFTP();
	}

	/**
	 * Changes opcode of a packet
	 * 
	 * @param packet is the packet to alter
	 * @param opcode is the integer to replace opcode with
	 */
	private void changeOpCode(DatagramPacket packet, byte opCode) {
		byte[] data = packet.getData();

		data[1] = opCode;

		packet.setData(data, packet.getOffset(), packet.getLength());
	}


	/**
	 * Changes Block number of a packet
	 * 
	 * @param packet is the packet to alter
	 * @param blockNum is the integer to replace block number with
	 */
	private void changeBlockNum(DatagramPacket packet, int blockNum) {
		byte[] data = packet.getData();

		byte byteA = ((byte) (blockNum >>> 8));
		byte byteB = ((byte) (blockNum));

		data[2] = byteA;
		data[3] = byteB;

		packet.setData(data, packet.getOffset(), packet.getLength());
	}


	/**
	 * Method to change file name of a packet
	 * 
	 * @param packet is the packet to alter
	 * @param fileName is the String to replace file name with
	 */
	private void changeFileNameBytes(DatagramPacket packet, String fileName) {
		ByteArrayOutputStream newData = new ByteArrayOutputStream();
		byte[] data = packet.getData();

		int offset = 0;
		int count = 0;

		for (int i = 0; i < packet.getLength(); i++) {
			if (data[i] == 0)
				count += 1;

			if (count == 2) {
				offset = i;
				break;
			}
		}

		newData.write(0);
		newData.write(data[1]);
		try {
			newData.write(fileName.getBytes());
		} catch (IOException e) {
			e.printStackTrace();
		}

		for (int i = offset; i < packet.getLength(); i++)
			newData.write(data[i]);

		packet.setData(newData.toByteArray(), 0, newData.size());
	}

	/**
	 * Method to change mode of a packet
	 * 
	 * @param packet is the packet to alter
	 * @param mode is the String to replace mode with
	 */
	private void changeModeBytes(DatagramPacket packet, String mode) {
		ByteArrayOutputStream newData = new ByteArrayOutputStream();
		byte[] data = packet.getData();

		int offset = 0;
		int count = 0;
		for (int i = 0; i < packet.getLength(); i++) {
			if (data[i] == ((byte) 0))
				count += 1;

			if (count == 2) {
				offset = i;
				break;
			}
		}

		if (offset != 0) {
			for (int i = 0; i < offset+1; i++)
				newData.write(data[i]);

			try {
				newData.write(mode.getBytes());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		newData.write(0);

		packet.setData(newData.toByteArray(), 0, newData.size());
	}

	/**
	 * Method to change size of a packet
	 * 
	 * @param packet is the packet to alter
	 * @param newSize is the size we want to make the packet
	 */
	private void changePacketSize(DatagramPacket packet, int newSize) {
		byte[] data = packet.getData();
		byte[] newData = new byte[newSize];

		System.arraycopy(data, 0, newData, 0, newSize);

		packet.setData(newData, 0, newSize);
	}

	/**
	 * Method to change a specific byte of a packet
	 * 
	 * @param packet is the packet to alter
	 * @param int index is the specific byte to alter
	 * @param value is the new byte value to set the byte to
	 */
	private void changePacketByte(DatagramPacket packet, int index, byte value) {
		byte[] data = packet.getData();

		data[index] = value;

		packet.setData(data, packet.getOffset(), packet.getLength()); 
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

	/**
	 * Main method of error simulator
	 * Creates an instance of error simulator and
	 * starts the newly created simulator
	 * 
	 */
	public static void main( String args[] ) {
		TFTPErrorSimulator errorSim = TFTPErrorSimulator.instanceOf();
		errorSim.start();
	}
}