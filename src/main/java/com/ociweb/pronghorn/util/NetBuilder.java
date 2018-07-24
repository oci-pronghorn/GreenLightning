package com.ociweb.pronghorn.util;

import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.network.ClientSocketWriterStage;
import com.ociweb.pronghorn.network.SSLEngineUnWrapStage;
import com.ociweb.pronghorn.network.SSLEngineWrapStage;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.ServerSocketReaderStage;
import com.ociweb.pronghorn.network.ServerSocketWriterStage;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.ReleaseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class NetBuilder {

	public static void buildSimpleTLSClient(GraphManager graphManager, ClientCoordinator clientCoordinator,
			Pipe<NetPayloadSchema>[] clientPlainOutput, Pipe<NetPayloadSchema>[] clientPlainInput) {
		
		Pipe<ReleaseSchema>[]    clientReleaseAck =new Pipe[] {ReleaseSchema.instance.newPipe(1024, 0)};
		Pipe<NetPayloadSchema>[] clientHandshakePipe = new Pipe[] {NetPayloadSchema.instance.newPipe(8, 1<<16)}; 
		Pipe<NetPayloadSchema>[] clientEncyptedInput = Pipe.buildPipes(clientPlainInput);
		Pipe<NetPayloadSchema>[] clientEncryptedOutput = Pipe.buildPipes(clientPlainOutput);
		
		ClientSocketReaderStage reader = new ClientSocketReaderStage(graphManager, clientCoordinator, clientReleaseAck, clientEncyptedInput );
		GraphManager.addNota(graphManager, GraphManager.DOT_RANK_NAME, "socket reader", reader);
		
	    SSLEngineUnWrapStage unwrap = new SSLEngineUnWrapStage(graphManager, clientCoordinator, clientEncyptedInput, clientPlainInput, clientReleaseAck[0], clientHandshakePipe[0], false /*isServer*/);
	    GraphManager.addNota(graphManager, GraphManager.DOT_RANK_NAME, "socket unwrap", unwrap);
		
	    new SSLEngineWrapStage(graphManager, clientCoordinator, false /*isServer*/, clientPlainOutput, clientEncryptedOutput);		
		new ClientSocketWriterStage(graphManager, clientCoordinator, PronghornStage.join(clientEncryptedOutput, clientHandshakePipe));
	}

	public static void buildSimpleTLSServer(GraphManager graphManager, ServerCoordinator serverCoordinator,
			Pipe<NetPayloadSchema>[] serverPlainInput, Pipe<NetPayloadSchema>[] serverPlainOutput) {
		
		Pipe<ReleaseSchema>[]    serverReleaseAck = new Pipe[] {ReleaseSchema.instance.newPipe(1024, 0)};
		Pipe<NetPayloadSchema>[] serverHandshakePipe = new Pipe[] {NetPayloadSchema.instance.newPipe(8, 1<<16)}; 
		Pipe<NetPayloadSchema>[] serverEncryptedInput = Pipe.buildPipes(serverPlainInput);
		Pipe<NetPayloadSchema>[] serverEncryptedOutput = Pipe.buildPipes(serverPlainOutput);
				
		ServerSocketReaderStage reader = new ServerSocketReaderStage(graphManager, serverReleaseAck, serverEncryptedInput, serverCoordinator);
		GraphManager.addNota(graphManager, GraphManager.DOT_RANK_NAME, "socket reader", reader);
		
		SSLEngineUnWrapStage unwrap = new SSLEngineUnWrapStage(graphManager, serverCoordinator, serverEncryptedInput, serverPlainInput, serverReleaseAck[0], serverHandshakePipe[0], true /*isServer*/);
	    GraphManager.addNota(graphManager, GraphManager.DOT_RANK_NAME, "socket unwrap", unwrap);
		
		new SSLEngineWrapStage(graphManager, serverCoordinator, true /*isServer*/, serverPlainOutput, serverEncryptedOutput);	
		new ServerSocketWriterStage(graphManager, serverCoordinator, PronghornStage.join(serverEncryptedOutput, serverHandshakePipe));
	}

}
