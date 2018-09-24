package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.network.BasicClientConnectionFactory;
import com.ociweb.pronghorn.network.ClientConnection;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public class ClientHostPortCollection {

		
	private int membersCount = 0;
	private ClientHostPortInstance[] members = new ClientHostPortInstance[16];
				
	
	public int members() {
		return membersCount;
	}
	
	public ClientConnection connection(int idx, ClientCoordinator ccm, Pipe<NetPayloadSchema>[] outputs) {
		

		ClientConnection result = ClientCoordinator.openConnection(
									ccm, 
									members[idx].hostId, 
									members[idx].port, 
									members[idx].sessionId, 
									ClientHostPortInstance.getTargetResponsePipeIdx(members[idx]),
									outputs,
									members[idx].getConnectionId(),
									BasicClientConnectionFactory.instance);
		
		members[idx].setConnectionId(result.getId());
		return result;
	}
	
	public boolean addMember(String host, int port) {
		return addMember(host,port,-1);
	}
	
	public boolean addMember(String host, int port, long timeoutNS) {
		
		if (members.length == membersCount) {
			//grow first before addition
			ClientHostPortInstance[] newMembers = new ClientHostPortInstance[members.length*2];
			System.arraycopy(members, 0, newMembers, 0, members.length);
			members = newMembers;			
		} 
		JSONExtractorCompleted extractor = null;
		
		int i = membersCount;
		while (--i>=0) {
			if (members[i].isFor(host,port)) {
				return false;//can not add already found
			}
		}
		
		members[membersCount++] = new ClientHostPortInstance(host, port, extractor, timeoutNS);
		return true;
	}
	
	public boolean removeMember(String host, int port) {
		
		int i = membersCount;
		while (--i >= 0) {
			if (members[i].isFor(host,port)) {
				if (i<(members.length-1)) {
					// len of 10  we want to remove pos 8 so we need to move 1
					System.arraycopy(members, i+1, members, i, (members.length-i)-1 );
				}
				membersCount--;				
				return true;
			}
		}		
		return false;
		
	}
	
}
