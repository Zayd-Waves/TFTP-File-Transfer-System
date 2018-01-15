/*  PacketAlteration.java
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
 *  
 *  Author:         Team 12 (Group Project - SYSC3303)
 *  Date:           5/19/2017
 */

public class PacketAlteration{
	//Direction of packet, true for client to server or false for server to client.
	private boolean direction; 

	//The block number of the packet
	private int packetNumber;

	//Value of packetNumber to be changed to
	private int newPacketNumber;

	//Index and value of byte to be modified
	private int byteNumber; 
	private byte byteValue; 

	//Size of packet to be changed to.
	private int newSize;

	//New opCode for the packet
	private byte opcode;

	//New mode string for the packet
	private String mode;

	//New file mode / filepath for the packet
	private String fileName;

	//New tid for the packet
	private int tid;

	//New InetAddress
	private String address;

	//Select whether to send duplicate
	private boolean duplicate;

	// Set Delay between duplicate
	private int duplicationDelay;

	// Create a delay of a specified time
	private int delay;

	// Lose a packet
	private boolean losePacket;

	PacketAlteration(){
		packetNumber = -1;
		byteNumber = -1;
		newSize = -1;
		byteValue = -1;
		opcode = -1;
		mode = null;
		fileName = null;
		tid = -1;
		address = null;
		newPacketNumber = -1;
		duplicate = false;
		delay = -1;
		losePacket = false;
	}

	public void setDirection(boolean direction) {
		this.direction = direction;
	}

	public boolean getDirection() {
		return direction;
	}

	public void setDuplicate(boolean duplicate) {
		this.duplicate = duplicate;
	}

	public boolean getDuplicate() {
		return duplicate;
	}

	public void setDuplicateDelay(int duplicationDelay){
		this.duplicationDelay = duplicationDelay;
	}

	public int getDuplicateDelay(){
		return duplicationDelay;
	}

	public void setPacketNumber(int packetNumber) {
		this.packetNumber = packetNumber;
	}

	public int getPacketNumber() {
		return packetNumber;
	}

	public void setNewPacketNumber(int newPacketNumber) {
		this.newPacketNumber = newPacketNumber;
	}

	public int getNewPacketNumber() {
		return newPacketNumber;
	}

	public void setByteNumber(int byteNumber) {
		this.byteNumber = byteNumber;
	}

	public int getByteNumber() {
		return byteNumber;
	}

	public void setNewSize(int newSize) {
		this.newSize = newSize;
	}

	public int getNewSize() {
		return newSize;
	}

	public void setByteValue(byte newByte) {
		this.byteValue = newByte;
	}

	public byte getByteValue() {
		return byteValue;
	}

	public void setOpCode(byte opcode) {
		this.opcode = opcode;
	}

	public byte getOpCode() {
		return opcode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getMode() {
		return mode;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileName() {
		return fileName;
	}

	public void setTid(int tid) {
		this.tid = tid;
	}

	public int getTid() {
		return tid;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getAddress() {
		return address;
	}

	public void setDelay(int delay){
		this.delay = delay;
	}

	public int getDelay(){
		return delay;
	}

	public void setLosePacket(boolean losePacket){
		this.losePacket = losePacket;
	}

	public boolean getLosePacket(){
		return losePacket;
	}
}