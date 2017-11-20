package com.ociweb.gl.impl.stage;

import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.pronghorn.network.ClientConnection;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.http.HeaderUtil;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.pipe.PipeUTF8MutableCharSquence;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class HTTPClientRequestTrafficStage extends AbstractTrafficOrderedStage {

	public static final Logger logger = LoggerFactory.getLogger(HTTPClientRequestTrafficStage.class);
	
	private final Pipe<ClientHTTPRequestSchema>[] input;
	private final Pipe<NetPayloadSchema>[] output;
	private final ClientCoordinator ccm;
    
	private final boolean isTLS;
    
	private int activeOutIdx = 0;
			
	private static final String implementationVersion = PronghornStage.class.getPackage().getImplementationVersion()==null?"unknown":PronghornStage.class.getPackage().getImplementationVersion();
		
	/**
	 * Parse HTTP data on feed and sends back an ack to the  SSLEngine as each message is decrypted.
	 * 
	 * @param graphManager
	 * @param hardware
	 * @param input
	 * @param goPipe
	 * @param ackPipe
	 * @param output
	 */
	
	public HTTPClientRequestTrafficStage(GraphManager graphManager, 
			BuilderImpl hardware,
			ClientCoordinator ccm,
            Pipe<ClientHTTPRequestSchema>[] input,
            Pipe<TrafficReleaseSchema>[] goPipe,
            Pipe<TrafficAckSchema>[] ackPipe,
            Pipe<NetPayloadSchema>[] output
            ) {
		
		super(graphManager, hardware, input, goPipe, ackPipe, output);
		this.input = input;
		this.output = output;
		this.ccm = ccm;
		this.isTLS = hardware.getHTTPClientConfig().isTLS();
		
		GraphManager.addNota(graphManager, GraphManager.DOT_BACKGROUND, "lavenderblush", this);
	}
	
	
	@Override
	public void startup() {
		super.startup();		
	}
	
	
	@Override
	protected void processMessagesForPipe(int activePipe) {
		
		    Pipe<ClientHTTPRequestSchema> requestPipe = input[activePipe];

	        while (PipeReader.hasContentToRead(requestPipe) && hasReleaseCountRemaining(activePipe) 
	                && isChannelUnBlocked(activePipe)	                
	                && hasOpenConnection(requestPipe, output, ccm)
	                && PipeReader.tryReadFragment(requestPipe) ){
	  	    
	        	
	        	//Need peek to know if this will block.
	        	
	            int msgIdx = PipeReader.getMsgIdx(requestPipe);
	            
				switch (msgIdx) {
	            			case ClientHTTPRequestSchema.MSG_HTTPGET_100:
	            				
				                {
				            		final byte[] hostBack = Pipe.blob(requestPipe);
				            		final int hostPos = PipeReader.readBytesPosition(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
				            		final int hostLen = PipeReader.readBytesLength(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
				            		final int hostMask = Pipe.blobMask(requestPipe);
				                	
				            		int routeId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_DESTINATION_11);
				            		
					                int port = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1);
					                int userId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_SESSION_10);

					                
					                
					                long connectionId = ccm.lookup(hostBack, hostPos, hostLen, hostMask, port, userId);	
					                
					                ClientConnection clientConnection;
					                if (-1 != connectionId && null!=(clientConnection = (ClientConnection)ccm.connectionForSessionId(connectionId) ) ) {
						               
					                	assert(clientConnection.singleUsage(stageId)) : "Only a single Stage may update the clientConnection.";
					                	assert(routeId>=0);
					                	clientConnection.recordDestinationRouteId(routeId);
						        		
					                	int outIdx = clientConnection.requestPipeLineIdx();
					                	
					                	clientConnection.incRequestsSent();//count of messages can only be done here.
										Pipe<NetPayloadSchema> outputPipe = output[outIdx];
						                				                	
						                if (PipeWriter.tryWriteFragment(outputPipe, NetPayloadSchema.MSG_PLAIN_210) ) {
						                    	
						                	PipeWriter.writeLong(outputPipe, NetPayloadSchema.MSG_PLAIN_210_FIELD_CONNECTIONID_201, connectionId);
						                	PipeWriter.writeLong(outputPipe, NetPayloadSchema.MSG_PLAIN_210_FIELD_ARRIVALTIME_210, System.currentTimeMillis());
						                	PipeWriter.writeLong(outputPipe, NetPayloadSchema.MSG_PLAIN_210_FIELD_POSITION_206, 0);
						                	
						                	DataOutputBlobWriter<NetPayloadSchema> activeWriter = PipeWriter.outputStream(outputPipe);
						                	DataOutputBlobWriter.openField(activeWriter);
											
						                	DataOutputBlobWriter.encodeAsUTF8(activeWriter,"GET");
						                	
						                	int len = PipeReader.readBytesLength(requestPipe,ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3);					                	
						                	int  first = PipeReader.readBytesPosition(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3);					                	
						                	boolean prePendSlash = (0==len) || ('/' != PipeReader.readBytesBackingArray(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3)[first&Pipe.blobMask(requestPipe)]);  
						                	
											if (prePendSlash) { //NOTE: these can be pre-coverted to bytes so we need not convert on each write. may want to improve.
												DataOutputBlobWriter.encodeAsUTF8(activeWriter," /");
											} else {
												DataOutputBlobWriter.encodeAsUTF8(activeWriter," ");
											}
											
											//Reading from UTF8 field and writing to UTF8 encoded field so we are doing a direct copy here.
											PipeReader.readBytes(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, activeWriter);
											
											HeaderUtil.writeHeaderBeginning(hostBack, hostPos, hostLen, Pipe.blobMask(requestPipe), activeWriter);
											
											HeaderUtil.writeHeaderMiddle(activeWriter, implementationVersion);
											PipeReader.readBytes(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HEADERS_7, activeWriter);
											HeaderUtil.writeHeaderEnding(activeWriter, true, (long) 0);
											
						                	DataOutputBlobWriter.closeHighLevelField(activeWriter, NetPayloadSchema.MSG_PLAIN_210_FIELD_PAYLOAD_204);
						                					                	
						                	PipeWriter.publishWrites(outputPipe);
						                					                	
						                } else {
						                	System.err.println("unable to write");
						                	throw new RuntimeException("Unable to send request, outputPipe is full");
						                }
					                }
			                	}
	            		break;
	            			case ClientHTTPRequestSchema.MSG_HTTPPOST_101:
	            				
				                {
				                					                	
				            		final byte[] hostBack = Pipe.blob(requestPipe);
				            		final int hostPos = PipeReader.readBytesPosition(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HOST_2);
				            		final int hostLen = PipeReader.readBytesLength(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HOST_2);
				            		final int hostMask = Pipe.blobMask(requestPipe);

				            		int routeId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_DESTINATION_11);
				            		int userId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_SESSION_10);
					                int port = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PORT_1);
					                
					                long connectionId = ccm.lookup(hostBack, hostPos, hostLen, hostMask, port, userId);	
					                //openConnection(activeHost, port, userId, outIdx);
					                
					                ClientConnection clientConnection;
					                if ((-1 != connectionId) && (null!=(clientConnection = (ClientConnection)ccm.connectionForSessionId(connectionId)))) {
					                	
						                
						        		//TODO: due to this thread unsafe method we must only have 1 HTTPClientRequestStage per client coord.
						        		clientConnection.recordDestinationRouteId(userId);
						        		
					                	int outIdx = clientConnection.requestPipeLineIdx();
					                					                  	
					                	clientConnection.incRequestsSent();//count of messages can only be done here.
										Pipe<NetPayloadSchema> outputPipe = output[outIdx];
					                
						                PipeWriter.presumeWriteFragment(outputPipe, NetPayloadSchema.MSG_PLAIN_210);
					                    
						                	
						                	PipeWriter.writeLong(outputPipe, NetPayloadSchema.MSG_PLAIN_210_FIELD_CONNECTIONID_201, connectionId);
						                	
						                	DataOutputBlobWriter<NetPayloadSchema> activeWriter = PipeWriter.outputStream(outputPipe);
						                	DataOutputBlobWriter.openField(activeWriter);
						                			                
						                	DataOutputBlobWriter.encodeAsUTF8(activeWriter,"POST");
						                	
						                	int len = PipeReader.readBytesLength(requestPipe,ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3);					                	
						                	int  first = PipeReader.readBytesPosition(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3);					                	
						                	boolean prePendSlash = (0==len) || ('/' != PipeReader.readBytesBackingArray(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3)[first&Pipe.blobMask(requestPipe)]);  
						                	
											if (prePendSlash) { //NOTE: these can be pre-coverted to bytes so we need not convert on each write. may want to improve.
												DataOutputBlobWriter.encodeAsUTF8(activeWriter," /");
											} else {
												DataOutputBlobWriter.encodeAsUTF8(activeWriter," ");
											}
											
											//Reading from UTF8 field and writing to UTF8 encoded field so we are doing a direct copy here.
											PipeReader.readBytes(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PATH_3, activeWriter);
											
											HeaderUtil.writeHeaderBeginning(hostBack, hostPos, hostLen, Pipe.blobMask(requestPipe), activeWriter);
											
											HeaderUtil.writeHeaderMiddle(activeWriter, implementationVersion);
											PipeReader.readBytes(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_HEADERS_7, activeWriter);
											HeaderUtil.writeHeaderEnding(activeWriter, true, (long) 0);  
											
											
											
											//TODO: must write lenghth in header before we write the payload.
											//un-tested  post payload here, TODO: need to add support for chunking and length??
											PipeReader.readBytes(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5, activeWriter);
											
						                	DataOutputBlobWriter.closeHighLevelField(activeWriter, NetPayloadSchema.MSG_PLAIN_210_FIELD_PAYLOAD_204);
						                					                	
						                	PipeWriter.publishWrites(outputPipe);
						       										
										
					                }
		            		
				                }
	    	        	break;
	            			case ClientHTTPRequestSchema.MSG_CLOSE_104:
	            
			            		final byte[] hostBack = Pipe.blob(requestPipe);
			            		final int hostPos = PipeReader.readBytesPosition(requestPipe, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_HOST_2);
			            		final int hostLen = PipeReader.readBytesLength(requestPipe, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_HOST_2);
			            		final int hostMask = Pipe.blobMask(requestPipe);
				                final int port = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_PORT_1);
				                final int userId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_CLOSE_104_FIELD_SESSION_10);
				                
				                long connectionId = ccm.lookup(hostBack, hostPos, hostLen, hostMask, port, userId);	
				                //only close if we find a live connection
				                if ((-1 != connectionId)) {
				                	ClientConnection connectionToKill = (ClientConnection)ccm.connectionForSessionId(connectionId);
				                	if (null!=connectionToKill) {
				                	
					                	Pipe<NetPayloadSchema> outputPipe = output[connectionToKill.requestPipeLineIdx()];
										
										//do not close that will be done by last stage
										//must be done first before we send the message
										connectionToKill.beginDisconnect();
										
										PipeWriter.presumeWriteFragment(outputPipe, NetPayloadSchema.MSG_DISCONNECT_203);
										PipeWriter.writeLong(outputPipe, NetPayloadSchema.MSG_DISCONNECT_203_FIELD_CONNECTIONID_201, connectionId);
										PipeWriter.publishWrites(outputPipe);
				                	} 						
				                }
	            				
	            		break;
	    	            default:
	    	            	
	    	            	logger.info("not yet supporting message {}",msgIdx);
	            	
	            
	            }
			
				PipeReader.releaseReadLock(requestPipe);
				
				//only do now after we know its not blocked and was completed
				decReleaseCount(activePipe);
	        }

	            
		
	}

	private PipeUTF8MutableCharSquence mCharSequence = new PipeUTF8MutableCharSquence();
	
	//has side effect fo storing the active connectino as a member so it neeed not be looked up again later.
	public boolean hasOpenConnection(Pipe<ClientHTTPRequestSchema> requestPipe, 
											Pipe<NetPayloadSchema>[] output, ClientCoordinator ccm) {
		
		if (PipeReader.peekMsg(requestPipe, -1)) {
			return com.ociweb.pronghorn.network.http.HTTPClientRequestStage.hasRoomForEOF(output);
		}
		
		int hostMeta =  PipeReader.peekDataMeta(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
		int hostLen =  PipeReader.peekDataLength(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
		
		int port = PipeReader.peekInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1);
		int userId = PipeReader.peekInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_SESSION_10);		
						
		PipeUTF8MutableCharSquence mCharSeq = mCharSequence.setToField(requestPipe, hostMeta, hostLen);
		ClientConnection activeConnection = ClientCoordinator.openConnection(
				ccm, mCharSeq, port, userId, output,	
		        ccm.lookup(mCharSeq, port, userId));
				
		
		if (null != activeConnection) {
			
			if (ccm.isTLS) {
				
				//If this connection needs to complete a hanshake first then do that and do not send the request content yet.
				HandshakeStatus handshakeStatus = activeConnection.getEngine().getHandshakeStatus();
				if (HandshakeStatus.FINISHED!=handshakeStatus && HandshakeStatus.NOT_HANDSHAKING!=handshakeStatus) {
					activeConnection = null;
					return false;
				}
	
			}
			
		} else {
			//this happens often when the profiler is running due to contention for sockets.
			
			//"Has no room" for the new connection so we request that the oldest connection is closed.
			
			//instead of doing this (which does not work) we will just wait by returning false.
//			ClientConnection connectionToKill = (ClientConnection)ccm.get( -connectionId, 0);
//			if (null!=connectionToKill) {
//				Pipe<NetPayloadSchema> pipe = output[connectionToKill.requestPipeLineIdx()];
//				if (PipeWriter.hasRoomForWrite(pipe)) {
//					//close the least used connection
//					cleanCloseConnection(connectionToKill, pipe);				
//				}
//			}
		
			return false;
		}
		
		
		int outIdx = activeConnection.requestPipeLineIdx(); //this should be done AFTER any handshake logic
		Pipe<NetPayloadSchema> pipe = output[outIdx];
		if (!PipeWriter.hasRoomForWrite(pipe)) {
			return false;
		}
		return true;
	}


	
	

}
