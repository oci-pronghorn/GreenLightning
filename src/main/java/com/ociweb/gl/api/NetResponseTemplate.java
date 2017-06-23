package com.ociweb.gl.api;

public class NetResponseTemplate<T> {

	NetResponseTemplateData[] script;
	int count;
	
	public NetResponseTemplate() {
		script = new NetResponseTemplateData[8];
	}
	
	private void append(NetResponseTemplateData<T> fetchData) {
		
		if (count==script.length) {			
			NetResponseTemplateData[] newScript = new NetResponseTemplateData[script.length*2];
			System.arraycopy(script, 0, newScript, 0, script.length);
			script = newScript;
		}
		script[count++] = fetchData;
	}
	
	
	public NetResponseTemplate<T> add(String text) {
		
		final byte[] byteData = text.getBytes();
		
		append(
				new NetResponseTemplateData<T>() {
					@Override
					public void fetch(NetResponseWriter writer, T source) {
						writer.write(byteData);				
					}					
				}
				);
		
		return this;
		
	}
	
    public NetResponseTemplate<T> add(NetResponseTemplateData<T> data) {		
    	append(data);    	
		return this;
	}
	
    public void render(NetResponseWriter writer, T source) {
    	
    	for(int i=0;i<count;i++) {
    		script[i].fetch(writer,source);
    	}
    	
    }
	
}
