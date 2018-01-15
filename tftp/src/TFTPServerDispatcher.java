/*  TFTPServerDispatcher.java
 *  
 *  The server dispatcher listens for 
 *  communication on port 69. The dispatcher 
 *  will create a new client connection thread 
 *  for each new connection (on port 69) which 
 *  it receives. All communications are then 
 *  dealt with by the client connection thread. 
 *  
 *  The dispatcher is created once the server is
 *  started. There is only one instance of the server
 *  dispatcher that is created. 
 *  
 *  Author:         Team 12 (Group Project - SYSC3303)
 *  Date:           5/19/2017
 */

import java.net.*;

public class TFTPServerDispatcher extends Thread {
	/* Responses for valid requests. */
    public static enum OPCODE {
        RRQ(1), WRQ(2), DATA(3), ACK(4), ERROR(5);
        private final int id;
        OPCODE(int id) { this.id = id; }
        public int value() { return id; }
    }
    
    /* Types of requests we can receive. */
    public static enum Request {READ, WRITE, ERROR};
	
    /* Constants for read/write responses */
    public static final byte[] READ_RESP = {0, 3, 0, 1};
    public static final byte[] WRITE_RESP = {0, 4, 0, 0};

    /* Main server port. */
    private static final int SERVER_RECV_PORT = 6900;

	/* Constants for packet sizes. */
    private static final int TFTP_DATA_SIZE = 516;
    
    /* Creates verbose initialized to 'OFF'. */
    private static Verbose verbose = Verbose.ON;

    /* UDP datagram packets and sockets used to send / receive. */
    private DatagramPacket receivePacket;
    private TFTPSocket receiveSocket;
    //private DatagramSocket receiveSocket;
    
    private int threadNumber;
    private TFTPClientConnection clientConnection;
    private boolean running;
    
    
    public TFTPServerDispatcher() {
        /* 
            Construct a datagram socket and bind it to port serverReceivePort
            on the local host machine. This socket will be used to
            receive UDP Datagram packets.
        */
    	receiveSocket = new TFTPSocket(SERVER_RECV_PORT);
        
        threadNumber = 0;
        running = true;
    }
    
    @Override
    public void run() {
     
        if (isVerbose()) { System.out.println("Server's Wait Thread: initializing."); }
        
        while (running) {
            /*
                Construct a DatagramPacket for receiving packets up
                to 100 bytes long (the length of the byte array).
            */
            
            byte[] data;
            data = new byte[TFTP_DATA_SIZE];
            receivePacket = new DatagramPacket(data, data.length);
            
            if (isVerbose()) { System.out.println("Server: Waiting for packet."); }

            /* Block until a datagram packet is received from receiveSocket. */
            try {
                receiveSocket.getDatagramSocket().receive(receivePacket);
            } catch (Exception se) {
                if (!running) {
                    /* This means the server received a shutdown request. We can safely ignore the exception. */
                } else {
                    /*  This means that the exception was thrown while the server was running. In other words,
                        an unexpected exception. */
                        se.printStackTrace();
                        System.exit(1);
                }
            }
            
            if (!running) {
                break;
            }
            
            threadNumber++;
            clientConnection = new TFTPClientConnection(threadNumber, receivePacket, data, verbose);
            clientConnection.start();
        }
        
    }
    
	public static boolean isVerbose() {
		return (verbose == Verbose.ON);
	}
        
    public void toggleVerbosity() {
        if (verbose == Verbose.ON) {
            verbose = Verbose.OFF;
            TFTPClientConnection.toggleVerbosity();
        } else {
            verbose = Verbose.ON;
            TFTPClientConnection.toggleVerbosity();
        }
    }
    
    public void killThread() {
        running = false;
        receiveSocket.close();
    }
}