/*  TFTPSocket.java
 *  
 *  This class is used to create sockets for 
 *  transfers. It will bind socket and is also used
 *  to send and receive packets. It contains methods
 *  to retrieve information from sockets as well. 
 *  
 *  Author:         Team 12 (Group Project - SYSC3303)
 *  Date:           5/19/2017
 */


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;


public class TFTPSocket {
	private DatagramSocket socket;
	
	public TFTPSocket() {
		socket = bind();
	}
	
	public TFTPSocket(int port) {
		socket = bind(port);
	}
	
	public TFTPSocket(int port, String address) {
		InetAddress inet = null;
		try {
			inet = InetAddress.getByName(address);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		socket = bind(port, inet);
	}
	
	/**
	 * Sends a given DatagramPacket to a host through a provided DatagramSocket.
	 * Terminates TFTPErrorSimulator if IOException occurs.
	 * 
	 * @param socket to send through.
	 * @param packet to send.
	 */
	public void sendPacket(DatagramPacket packet) {
		try {
			socket.send(packet);
		} 
		catch (IOException e) {
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
	public void receivePacket(DatagramPacket packet) {
		//Block until packet receives
		try {
			socket.receive(packet);
		} 
		catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	public void close() {
		socket.close();
	}
	
	public DatagramSocket getDatagramSocket() { return socket;}
	
	/**
     * Returns a DatagramSocket bound to port selected by the host. Terminates
     * the TFTPErrorSimulator if a SocketException occurs.
     * 
     * @return DatagramSocket bound to a port.
     */
	private DatagramSocket bind() {
		//Create new socket reference
		DatagramSocket socket = null;
		   
		//Attempt to bind socket
		try {
			socket = new DatagramSocket();
		} 
		catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		   
		//Return bound socket
		return socket;
	}

	/**
	 * Returns a DatagramSocket bound to a provided port. Terminates
	 * the TFTPErrorSimulator if a SocketException occurs.
	 * 
	 * @param port number to bind to socket.
	 * @return DatagramSocket bound to a port.
	 */
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
	 * Returns a DatagramSocket bound to a provided port
	 * and Inet Address. Terminates
	 * the TFTPErrorSimulator if a SocketException occurs.
	 * 
	 * @param port number to bind to socket.
	 * @param address to bind socket to
	 * @return DatagramSocket bound to a port.
	 */
	private DatagramSocket bind(int port, InetAddress address) {
		//Create new socket reference
		DatagramSocket socket = null;
		
		//Attempt to bind socket to port
		try {
			socket = new DatagramSocket(port, address);
		} 
		catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}
		   
		//Return bound socket
		return socket;
	}
	
	/**
	 * Returns a port to which the socket
	 * is locally bound.
	 * 
	 * @return port which socket is bound
	 */
	public int getPort(){
		return socket.getLocalPort();
	}
}
