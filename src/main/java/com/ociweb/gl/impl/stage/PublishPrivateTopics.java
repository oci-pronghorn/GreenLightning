package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class PublishPrivateTopics {
	
	private TrieParser privateTopicsPublishTrie;
	private Pipe[] privateTopicPublishPipes;
	private TrieParserReader privateTopicsTrieReader;

	public PublishPrivateTopics(TrieParser privateTopicsPublishTrie,
								Pipe[] privateTopicPublishPipes,
								TrieParserReader privateTopicsTrieReader
			) {
		
		this.privateTopicsPublishTrie = privateTopicsPublishTrie;
		this.privateTopicPublishPipes = privateTopicPublishPipes;
		this.privateTopicsTrieReader = privateTopicsTrieReader;
	
	}

	public int count() {
		return privateTopicPublishPipes.length;
	}

	public void copyPipes(Pipe[] results, int idx) {
 		System.arraycopy(privateTopicPublishPipes, 0, results, idx, privateTopicPublishPipes.length);
	}

	public Pipe<MessagePrivate> getPipe(int index) {
		return privateTopicPublishPipes[index];
	}

	public int getToken(CharSequence topic) {
		return (int)TrieParserReader.query(privateTopicsTrieReader,
				privateTopicsPublishTrie, topic);
	}

	public int getToken(byte[] topic, int pos, int length) {
		return (int)TrieParserReader.query(privateTopicsTrieReader,
				privateTopicsPublishTrie, topic, pos, length, Integer.MAX_VALUE);
	}

	public int getToken(Pipe<RawDataSchema> tempTopicPipe) {

		return (int)TrieParserReader.query(privateTopicsTrieReader,
										   privateTopicsPublishTrie, 
                						    tempTopicPipe, -1);
		
	}
	
	
}
