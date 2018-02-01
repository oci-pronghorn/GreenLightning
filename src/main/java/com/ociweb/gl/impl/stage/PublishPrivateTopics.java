package com.ociweb.gl.impl.stage;

import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.RawDataSchema;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class PublishPrivateTopics {
	
	private TrieParser privateTopicsPublishTrie;
	private Pipe<MessagePrivate>[] privateTopicPublishPipes;
	private TrieParserReader privateTopicsTrieReader;

	public PublishPrivateTopics(TrieParser privateTopicsPublishTrie,
								Pipe<MessagePrivate>[] privateTopicPublishPipes,
								TrieParserReader privateTopicsTrieReader
			) {
		
		this.privateTopicsPublishTrie = privateTopicsPublishTrie;
		this.privateTopicPublishPipes = privateTopicPublishPipes;
		this.privateTopicsTrieReader = privateTopicsTrieReader;
	
	}

	public int count() {
		return privateTopicPublishPipes.length;
	}

	public void copyPipes(Pipe<?>[] results, int idx) {
		assert(checkPipes(results));
		
 		System.arraycopy(privateTopicPublishPipes, 0, results, idx, privateTopicPublishPipes.length);
	}

	private boolean checkPipes(Pipe<?>[] results) {
		int i = results.length;
		while (--i>=0) {
			assert(Pipe.isForSchema(results[i], MessagePrivate.instance)) : "bad pipe of "+Pipe.schemaName(results[i]);
		}
		return true;
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
