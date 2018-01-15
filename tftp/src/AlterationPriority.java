/*  AlterationPriority.java
 *  
 *  This class is used to override the compare function
 *  in Comparator in order to override our alterations 
 *  properly using the priority queue. This is so our
 *  error simulator alters packets in the correct order
 *  and by packet number. 
 *  
 *  Author:         Team 12 (Group Project - SYSC3303)
 *  Date:           5/19/2017
 */


import java.util.Comparator;

public class AlterationPriority implements Comparator<PacketAlteration>{

	@Override
	public int compare(PacketAlteration A, PacketAlteration B) {
		int packetNumberA = A.getPacketNumber();
		int packetNumberB = B.getPacketNumber();
		return Integer.compare(packetNumberA, packetNumberB);
	}
}
