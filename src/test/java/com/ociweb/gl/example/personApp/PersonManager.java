package com.ociweb.gl.example.personApp;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.impl.file.SerialStoreConsumer;
import com.ociweb.gl.impl.file.SerialStoreProducer;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class PersonManager implements PubSubMethodListener {

	//TODO: this is an in memory solution which will be replaced by a database call..
	private final PubSubService pubService;
	private final SerialStoreProducer storageService;
	private SerialStoreConsumer consumeService;


	
	public PersonManager(GreenRuntime runtime) {
		GreenCommandChannel newCommandChannel = runtime.newCommandChannel();
		pubService = newCommandChannel.newPubSubService();		
		storageService = newCommandChannel.newSerialStoreProducer(0);
		consumeService = newCommandChannel.newSerialStoreConsumer(0);
		
	}

	public boolean query(CharSequence topic, ChannelReader payload) {
		
		boolean enabled = payload.structured().readBoolean(GreenField.enabled);
		
		//but I only want some..
		//consumeService.replay()
		
		// TODO Auto-generated method stub
		return false;
	}
	
	public boolean modify(CharSequence topic, ChannelReader payload) {
				
		long userId = payload.structured().readLong(GreenField.id);
		boolean enabled = payload.structured().readBoolean(GreenField.enabled);
		
		//consumeService.release(blockId)
		//storageService.store(blockId, w)
		
		
		// TODO Auto-generated method stub
		return true;
	}
	
	public boolean fetch(CharSequence topic, ChannelReader payload) {
		
		long userId = payload.structured().readLong(GreenField.id);
		
		//publish this user data in a playload
		//but I only want one
		//consumeService.replay()
		
		
		// TODO Auto-generated method stub
		return true;
	}
	
	public boolean addPerson(CharSequence topic, ChannelReader payload) {
		
		String firstName = payload.structured().readText(GreenField.firstName);
		String lastName = payload.structured().readText(GreenField.lastName);
		int age = payload.structured().readInt(GreenField.age);
		int id = payload.structured().readInt(GreenField.id);
		boolean enabled = payload.structured().readBoolean(GreenField.enabled);
		
		//or we just have an array of values in memory
		//or we index into hash map.. with idx into location...
		//or we use high density, but no strings..
		
		
		
		
		
		//storageService.store(blockId, w)
		
		//create new user
		
		
		// TODO Auto-generated method stub
		return true;
	}
	
	public boolean showAll(CharSequence topic, ChannelReader payload) {
		
		//consumeService.replay()
		
		// TODO Auto-generated method stub
		return true;
	}

}
