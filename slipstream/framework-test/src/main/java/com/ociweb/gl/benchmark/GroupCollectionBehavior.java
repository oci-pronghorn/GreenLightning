package com.ociweb.gl.benchmark;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class GroupCollectionBehavior implements PubSubListener, StartupListener {

	private final PubSubFixedTopicService pubSubService;

	private final int groupsCount = 256;
	private final int maxGroupSize = 500;
	
	private GroupObject[][] groups;
	
	private class GroupObject {
		public int id;
		public int rnd;
	}
	
	
	
	public GroupCollectionBehavior(String groupTopic, GreenRuntime runtime) {
	
		pubSubService = runtime.newCommandChannel().newPubSubService(groupTopic, groupsCount);
		
	}
	
	@Override
	public void startup() {
		
		groups = new GroupObject[groupsCount][maxGroupSize];
		
		int i = groupsCount; 
		while (--i>=0) {
			final int j = i;
			pubSubService.publishTopic(w-> w.writeInt(j) );
			
		}
		
		
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		
		
		//1 collect data 
		//2 when full  render json to response.
		//3 clear group and send notice for use of this bucket again.
		
		
		// TODO Auto-generated method stub
		return false;
	}

}
