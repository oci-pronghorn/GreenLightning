package com.ociweb.gl.api;

import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.http.HeaderWriter;

public class OAuth2Util {

	public final String requestBearerPayload = "grant_type=client_credentials";
	
	
	public static void buildBearerRequestHeader(HeaderWriter writer) {
		
		BasicAuthorization ba = new BasicAuthorization("username", "password");
		
		writer.write(HTTPHeaderDefaults.AUTHORIZATION, ba);		
		writer.write(HTTPHeaderDefaults.CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8");
		
	}
	
	//TODO: add TLS round trip test for ping
	//      double check the MQTT data
	
	public enum BearerField{
		ACCESS_TOKEN, TOKEN_TYPE, EXPIRES_IN, REFRESH_TOKEN, SCOPE
	}
	
	public static void buildBearerExtractor() {
		//TODO: add enum for these fields
		//TODO: how do we send the header for future requests??
		
		JSONExtractor ex = new JSONExtractor();
		
		JSONExtractor ready = ex.begin()
				.stringField("access_token", BearerField.ACCESS_TOKEN) //MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3
				.stringField("token_type", BearerField.TOKEN_TYPE) // token_type Expected = "bearer";
				.integerField("expires_in", BearerField.EXPIRES_IN)	//3600	
				.stringField("refresh_token", BearerField.REFRESH_TOKEN) //MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3
				.stringField("scope", BearerField.SCOPE) //create
		.finish();
		
		
//		HTTP/1.1 200 OK
//		Content-Type: application/json
//		Cache-Control: no-store
//		Pragma: no-cache
//		 
//		{
//		  "access_token":"MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3",
//		  "token_type":"bearer",
//		  "expires_in":3600,
//		  "refresh_token":"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk",
//		  "scope":"create"
//		}
		
	}
	
}
