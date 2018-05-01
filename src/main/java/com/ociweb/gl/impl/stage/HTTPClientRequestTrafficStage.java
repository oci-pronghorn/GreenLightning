package com.ociweb.gl.impl.stage;

import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.pronghorn.network.ClientConnection;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.http.HTTPClientConnectionFactory;
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
import com.ociweb.pronghorn.struct.StructRegistry;
import com.ociweb.pronghorn.util.TrieParserReader;

public class HTTPClientRequestTrafficStage extends AbstractTrafficOrderedStage {

	private final TrieParserReader READER = new TrieParserReader(true);

	private static final byte[] GET = "GET".getBytes();

	public static final Logger logger = LoggerFactory.getLogger(HTTPClientRequestTrafficStage.class);
	
	private final Pipe<ClientHTTPRequestSchema>[] input;
	private final Pipe<NetPayloadSchema>[] output;
	private final ClientCoordinator ccm;
    
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
	
	public HTTPClientRequestTrafficStage(
			GraphManager graphManager, 
			MsgRuntime<?,?> runtime,
			BuilderImpl hardware,
			ClientCoordinator ccm,
			
            Pipe<ClientHTTPRequestSchema>[] input,
            Pipe<TrafficReleaseSchema>[] goPipe,
            Pipe<TrafficAckSchema>[] ackPipe,
            
            Pipe<NetPayloadSchema>[] output
            ) {
		
		super(graphManager, runtime, hardware, input, goPipe, ackPipe, output);
		this.input = input;
		this.output = output;
		this.ccm = ccm;
		
		assert(ccm.isTLS == hardware.getHTTPClientConfig().isTLS());
		
		GraphManager.addNota(graphManager, GraphManager.DOT_BACKGROUND, "lavenderblush", this);
		GraphManager.addNota(graphManager, GraphManager.LOAD_MERGE, GraphManager.LOAD_MERGE, this);
		
		recordTypeData = graphManager.recordTypeData;
	}
	
	
	private final StructRegistry recordTypeData;
	private HTTPClientConnectionFactory ccf;
	
	@Override
	public void startup() {
		
		super.startup();		
		ccf = new HTTPClientConnectionFactory(recordTypeData);

	}

	
	@Override
	protected void processMessagesForPipe(int activePipe) {
		
		    Pipe<ClientHTTPRequestSchema> requestPipe = input[activePipe];

//		    System.err.println(PipeReader.hasContentToRead(requestPipe) 
//	        		+" && "+hasReleaseCountRemaining(activePipe)
//	        		+" && "+isChannelUnBlocked(activePipe)
//	        		+" && "+hasOpenConnection(requestPipe, output, ccm));
		    
	        while (PipeReader.hasContentToRead(requestPipe) 
	        		&& hasReleaseCountRemaining(activePipe)
	                && isChannelUnBlocked(activePipe)
	                && hasOpenConnection(requestPipe, output, ccm, activePipe)
	                && PipeReader.tryReadFragment(requestPipe) ){
	  	    
	        	
	            int msgIdx = PipeReader.getMsgIdx(requestPipe);
	            
				switch (msgIdx) {
							case ClientHTTPRequestSchema.MSG_FASTHTTPGET_200:
								{
					            		final byte[] hostBack = Pipe.blob(requestPipe);
					            		final int hostPos = PipeReader.readBytesPosition(requestPipe, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HOST_2);
					            		final int hostLen = PipeReader.readBytesLength(requestPipe, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HOST_2);
					            		final int hostMask = Pipe.blobMask(requestPipe);
					                	
					            		int routeId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_DESTINATION_11);
					            		
						                int port = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PORT_1);
						                int userId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_SESSION_10);
		                						                
						                long connectionId = PipeReader.readLong(requestPipe, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_CONNECTIONID_20);

						                ClientConnection clientConnection;
						                if (-1 != connectionId && null!=(clientConnection = (ClientConnection)ccm.connectionForSessionId(connectionId) ) ) {
							               
						                	assert(clientConnection.singleUsage(stageId)) : "Only a single Stage may update the clientConnection.";
						                	assert(routeId>=0);
						                	clientConnection.recordDestinationRouteId(routeId);
						                	publishGet(requestPipe, hostBack, hostPos, hostLen, connectionId,
													   clientConnection, 
													   ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_PATH_3, 
													   ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_HEADERS_7);
						                } else {
						                	logger.warn("Unable to http get, connection is not established");
						                }
				                	}
								break;
	            			case ClientHTTPRequestSchema.MSG_HTTPGET_100:
	            				
				                {
				            		final byte[] hostBack = Pipe.blob(requestPipe);
				            		final int hostPos = PipeReader.readBytesPosition(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
				            		final int hostLen = PipeReader.readBytesLength(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
				            		final int hostMask = Pipe.blobMask(requestPipe);
				                	
				            		int routeId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_DESTINATION_11);
				            		
					                int port = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1);
					                int userId = PipeReader.readInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_SESSION_10);
	                
					                
					                long connectionId = ccm.lookup(ccm.lookupHostId(hostBack, hostPos, hostLen, hostMask), port, userId);	
					                
					                ClientConnection clientConnection;
					                if (-1 != connectionId && null!=(clientConnection = (ClientConnection)ccm.connectionForSessionId(connectionId) ) ) {
						               
					                	assert(clientConnection.singleUsage(stageId)) : "Only a single Stage may update the clientConnection.";
					                	assert(routeId>=0);
					                	clientConnection.recordDestinationRouteId(routeId);
					                	publishGet(requestPipe, hostBack, hostPos, hostLen, connectionId,
												   clientConnection, 
												   ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PATH_3, 
												   ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HEADERS_7);
					                } else {
					                	logger.warn("Unable to http get, connection is not established");
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
					                
					                long connectionId = ccm.lookup(ccm.lookupHostId(hostBack, hostPos, hostLen, hostMask), port, userId);	
					                //openConnection(activeHost, port, userId, outIdx);
					                
					                ClientConnection clientConnection;
					                if ((-1 != connectionId) && (null!=(clientConnection = (ClientConnection)ccm.connectionForSessionId(connectionId)))) {
					                	
						                
					                	assert(routeId>=0);
					                	clientConnection.recordDestinationRouteId(routeId);
						        		
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

											long postLength = PipeReader.readBytesLength(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5);
											HeaderUtil.writeHeaderEnding(activeWriter, true, postLength);  
											
											//TODO: How is chunking supported, that code is not here yet but length must be -1 I think.
											
											PipeReader.readBytes(requestPipe, ClientHTTPRequestSchema.MSG_HTTPPOST_101_FIELD_PAYLOAD_5, activeWriter);
											
						                	DataOutputBlobWriter.closeHighLevelField(activeWriter, NetPayloadSchema.MSG_PLAIN_210_FIELD_PAYLOAD_204);
						                					                	
						                	PipeWriter.publishWrites(outputPipe);
						       										
										
					                } else {
					                	logger.warn("Unable to http post, connection is not established");
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
				                
				                long connectionId = ccm.lookup(ccm.lookupHostId(hostBack, hostPos, hostLen, hostMask), port, userId);	
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


	private void publishGet(Pipe<ClientHTTPRequestSchema> requestPipe, final byte[] hostBack, final int hostPos,
			final int hostLen, long connectionId, ClientConnection clientConnection, 
			int fieldNamePath,
			int fieldNameHeaders) {
		int outIdx = clientConnection.requestPipeLineIdx();
		
		clientConnection.incRequestsSent();//count of messages can only be done here.
		Pipe<NetPayloadSchema> outputPipe = output[outIdx];
							
		//TODO: this must be converted to the low level API urgent...
		
		PipeWriter.presumeWriteFragment(outputPipe, NetPayloadSchema.MSG_PLAIN_210);
    	
			PipeWriter.writeLong(outputPipe, NetPayloadSchema.MSG_PLAIN_210_FIELD_CONNECTIONID_201, connectionId);
			PipeWriter.writeLong(outputPipe, NetPayloadSchema.MSG_PLAIN_210_FIELD_ARRIVALTIME_210, System.currentTimeMillis());
			PipeWriter.writeLong(outputPipe, NetPayloadSchema.MSG_PLAIN_210_FIELD_POSITION_206, 0);
			
			DataOutputBlobWriter<NetPayloadSchema> activeWriter = PipeWriter.outputStream(outputPipe);
			DataOutputBlobWriter.openField(activeWriter);
			
			DataOutputBlobWriter.write(activeWriter,GET,0,GET.length);
			
			int len = PipeReader.readBytesLength(requestPipe, fieldNamePath);					                	
			int  first = PipeReader.readBytesPosition(requestPipe, fieldNamePath);					                	
			boolean prePendSlash = (0==len) || ('/' != PipeReader.readBytesBackingArray(requestPipe, fieldNamePath)[first&Pipe.blobMask(requestPipe)]);  
			
			if (prePendSlash) { //NOTE: these can be pre-coverted to bytes so we need not convert on each write. may want to improve.
				DataOutputBlobWriter.encodeAsUTF8(activeWriter," /");
			} else {
				DataOutputBlobWriter.encodeAsUTF8(activeWriter," ");
			}
			
			//Reading from UTF8 field and writing to UTF8 encoded field so we are doing a direct copy here.
			PipeReader.readBytes(requestPipe, fieldNamePath, activeWriter);
			
			HeaderUtil.writeHeaderBeginning(hostBack, hostPos, hostLen, Pipe.blobMask(requestPipe), activeWriter);
			
			HeaderUtil.writeHeaderMiddle(activeWriter, implementationVersion);
			
			PipeReader.readBytes(requestPipe, fieldNameHeaders, activeWriter);
			HeaderUtil.writeHeaderEnding(activeWriter, true, (long) 0);
			
			DataOutputBlobWriter.closeHighLevelField(activeWriter, NetPayloadSchema.MSG_PLAIN_210_FIELD_PAYLOAD_204);
							                	
			PipeWriter.publishWrites(outputPipe);
	
	}

	private PipeUTF8MutableCharSquence mCharSequence = new PipeUTF8MutableCharSquence();

	/**
	 *
	 * @param requestPipe Pipe<ClientHTTPRequestSchema> arg used in PipeReader.peekMsg, .peekInt, .peekDataMeta, .peekDataLength, .peekLong, mCharSequence.setToField, Pipe.convertToPosition, pipe.byteBackingArray, Pipe.blobMask, PipeReader.tryReadFragment and PipeReader.releaseReadLock
	 * @param output Pipe<NetPayloadSchema> used in ClientCoordinator.openConnection and PipeWriter.hasRoomForWrite
	 * @param ccm ClientCoordinator arg used in ClientCoordinator.openConnection
	 * @param activePipe int arg used in decReleaseCount
	 * @return PipeWriter.hasRoomForWrite(output[activeConnection.requestPipeLineIdx()])
	 */
	//has side effect of storing the active connection as a member so it need not be looked up again later.
	public boolean hasOpenConnection(Pipe<ClientHTTPRequestSchema> requestPipe, 
									 Pipe<NetPayloadSchema>[] output,
									 ClientCoordinator ccm, int activePipe) {
		
		if (PipeReader.peekMsg(requestPipe, -1)) {
			return com.ociweb.pronghorn.network.http.HTTPClientRequestStage.hasRoomForEOF(output);
		}
		
		final int msgIdx = PipeReader.peekInt(requestPipe, 0);
		
		//these fields are assumed to be the same for all message types.
		int hostMeta = PipeReader.peekDataMeta(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
		int hostLen = PipeReader.peekDataLength(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_HOST_2);
		int port = PipeReader.peekInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_PORT_1);
		int sessionId = PipeReader.peekInt(requestPipe, ClientHTTPRequestSchema.MSG_HTTPGET_100_FIELD_SESSION_10);		
		
		
		PipeUTF8MutableCharSquence mCharSeq = mCharSequence.setToField(requestPipe, hostMeta, hostLen);	
		
		
		long connectionId;
		
		if (ClientHTTPRequestSchema.MSG_FASTHTTPGET_200 == msgIdx) {			
			connectionId = PipeReader.peekLong(requestPipe, ClientHTTPRequestSchema.MSG_FASTHTTPGET_200_FIELD_CONNECTIONID_20);
		} else if (ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201 == msgIdx) {			
			connectionId = PipeReader.peekLong(requestPipe, ClientHTTPRequestSchema.MSG_FASTHTTPPOST_201_FIELD_CONNECTIONID_20);
		} else {					
			int hostPos = Pipe.convertToPosition(hostMeta, requestPipe);
			byte[] backing = Pipe.byteBackingArray(hostMeta, requestPipe);
									
			connectionId = ccm.lookup(ClientCoordinator.lookupHostId(
					backing, hostPos, hostLen, Pipe.blobMask(requestPipe)
					), port, sessionId);			
		}
		
		ClientConnection activeConnection = ClientCoordinator.openConnection(
				ccm, mCharSeq, port, sessionId,
				output, connectionId, ccf);
				
		
		if (null != activeConnection) {
			
			if (ccm.isTLS) {
				
				//If this connection needs to complete a handshake first then do that and do not send the request content yet.
				HandshakeStatus handshakeStatus = activeConnection.getEngine().getHandshakeStatus();
				if (HandshakeStatus.FINISHED!=handshakeStatus && HandshakeStatus.NOT_HANDSHAKING!=handshakeStatus) {
					activeConnection = null;
					return false;
				}
	
			}
			
			if (activeConnection.isDisconnecting()) {
				if (ccm.isTLS) {
					logger.info("Double check the client side certificates");
				} else {
					logger.info("Double check the server port to ensure it is open");
				}
				
				if (PipeReader.tryReadFragment(requestPipe)) {
			    	//special case for connection which was closed, must abandon old data
			    	//and allow trafic cop get back ack and not hang the system.			    	
					PipeReader.releaseReadLock(requestPipe);
					decReleaseCount(activePipe);
				}
				return false;
			}
						
		} else {
			//not yet open, this is not an error just an attempt to try again soon.
			return false;
		}
		
		//this should be done AFTER any handshake logic
		return PipeWriter.hasRoomForWrite(output[activeConnection.requestPipeLineIdx()]);
	}


	
	

}
