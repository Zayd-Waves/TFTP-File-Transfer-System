# TFTP File Transfer System

Final project for the SYSC 3303 course at Carleton University. Implementation of the TFTP protocol.

 _______________________________________
|                                       |
|           TABLE OF CONTENTS           |
|_______________________________________|

1 --------- GENERAL INFORMATION ---------
  1.1 - Collaborators
2 --------- PROGRAM INFORMATION ---------
  2.1 - How to Run
  2.2 - Error Code Documentation
  2.3 - Timeout/Retransmission Procedures
  2.4 - Documentation of our java files
  ---------------------------------------



1.1 -------------------------------------------------- Collaborators

 _______________________
| NAME                 |
|______________________|
| Zayd Bille           |
| Michelle Shaheen     |
| Christopher Morency  |
| Hanad Sharmarke      |
|______________________|

2.1 ----------------------------------------------------------------- HOW TO RUN

- Iteration 5 Running Instructions

- Open /Iteration 5/ in Eclipse
- Specifically, open the /src/TFTPClient.java, TFTPErrorSimulator.java, TFTPServer.java
- To run, begin by running TFTPServer.java
- Continue by running TFTPClient.java
- If you would like to use the simulator, continue following from the instructions below, 
  otherwise follow the prompts using the client
- Next, run TFTPErrorSimulator.java
- User will be prompted with a UI regarding errors they wish to simulate
- Refer to /Iteration 5/Testing Instructions.java for a detailed explanation on how to navigate the UI
- Once all errors have been chosen to simulate, simply select 'n' in the TFTPErrorSimulator.java UI
- Follow the prompts for the client, however ensure to put the client in test mode. 
  To put the client in test mode, type M, and then T in client. 
- Information on the packets will be printed for all 3 files ran, on the 3 separate consoles
- To change directories for either client or server, type CD at any time, then type the 
  new directory. (eg. C:\Users\...\)


2.2 ----------------------------------------------------------------- Error Code Documentation

Note: The only error code which will attempt to continue the transfer is error code 5. All
other error codes will terminate the transfer.

ERROR CODE 1: File Not Found

The error code will be generated if a file is not found on the client or server. If the request
is a read request, the server will send an error packet to client. If the request is a write 
request, the client will not send any error packet, however the client will display the error code 
to the user. 

ERROR CODE 2: Access Violation

Error code 2 is displayed on the client if the file which the client attempts to write is locked 
in some way. Error code 2 packets are generated for every other scenario. They will be sent from 
the server if the client attempts to access or write to a locked file/directory on the server. They
will also be generated if the client receives a file from the server and attempts to save it to a 
directory in which it cannot be saved in which case the packet is sent to the server. 

ERROR CODE 3: Disk full or allocation exceeded

Server or client will return the error code if a transfer which involves storing information, 
is unable to complete because either the disk on server or client side is full. If the transfer is 
a write operation the server would throw this error as well as send an error packet to client. 
If it is a read request, the client will send this error packet to the server and display an error
message to the user. 

ERROR CODE 4: Illegal TFTP operation

Generated for any malformed packets between either the client to server or server to client. There are 
numerous ways to generate this error code. For a detailed description of test cases, please see our 
testing and test cases file. 

ERROR CODE 5: Unknown transfer ID

This error will result due to a port number which is not recognized or a different IP address. In either 
of these cases, the server will send an error packet to the sender (if possible). This is the only 
error code in which the server will attempt continue the file transfer, and wait until the new file is 
received. 

ERROR CODE 6: File already exists

If the file already exists on the client side, the response of our system is to add a '(n)' to the start of 
the file name. The n will start at 1, and increment by one until it finds a name for which the file does
not exist. Server side, the system will not override or rename, but will send an error packet with error code 
6. This will terminate the transfer. The reason for this choice is to ensure if multiple users are on the server, 
other files will not be overriden. As well, the file will not be renamed, as the client will have no way of
knowing if that occured. 


2.3 ----------------------------------------------------------------- Timeout/Retransmission Procedures

Behaviour is the same for client and server

Duplicate packet behaviours:

	RRQ - Duplicate ignored
	WRQ - Duplicate acknowledged
	DATA - Duplicate acknowledged
	ACKs - Duplicate ignored
	ERROR - Duplicate ignored

Retransmission and timeout details:

	RRQ - Retransmitted after 3 seconds
	WRQ - Retransmitted after 1.5 seconds
	DATA - Retransmitted after 1.5 seconds
	ACK - Retransmitted after 3 seconds
	ERROR - Not retransmitted

	All transfers will time out after 30 seconds if no response is received
	This does not apply for the last packet ACK or ERROR packets as these are not acknowledged.

	
2.4 ----------------------------------------------------------------- Documentation of our java files

 *  AlterationPriority.java
 *  
 *  This class is used to override the compare function
 *  in Comparator in order to override our alterations 
 *  properly using the priority queue. This is so our
 *  error simulator alters packets in the correct order
 *  and by packet number. 





 *  PacketAlteration.java
 *  
 *  PacketAlterations is used in conjunction with the 
 *  priority queue of our error simulator. Any and all 
 *  alterations which will be made to a specific packet
 *  in order to simulate errors are held here.
 *  
 *  The decision to use a class to hold this information
 *  was made in order to use a priority queue allowing
 *  multiple alterations in one transfer. This means 
 *  one can ensure a transfer can have multiple 
 *  recoverable errors, and continue to function properly. 
 *  
 *  The order of which the errors are simulated is set
 *  by AlterationPriority.java which sorts these alterations
 *  by packet number. 





 *  TFTPClient.java
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





 *  TFTPClientConnection.java
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





 *  TFTPErrorSimulator.java
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





 *  TFTPPacket.java
 *  
 *  This class is used to create data packets for 
 *  transfers. It will store details of the packet and
 *  can be used to print data contained in the packet.





 *  TFTPServer.java
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





 *  TFTPServerDispatcher.java
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





 *  TFTPSocket.java
 *  
 *  This class is used to create sockets for 
 *  transfers. It will bind socket and is also used
 *  to send and receive packets. It contains methods
 *  to retrieve information from sockets as well.





 *  Verbose.java
 *  
 *  This class is used by the server threads to ensure
 *  the correct output is being applied. 