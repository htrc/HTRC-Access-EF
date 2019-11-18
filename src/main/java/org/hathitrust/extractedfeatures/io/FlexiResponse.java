package org.hathitrust.extractedfeatures.io;

import java.io.IOException;
import java.io.OutputStream;

public interface FlexiResponse 
{
	public boolean isAsync();
	
	public void setContentType(String content_type);
	public void setCharacterEncoding(String encoding);
	public void setContentLength(int len);
	public void setHeader(String header_name, String header_value);
	public void setContentDispositionAttachment(String filename);
	
	public void sendProgress(int numer, int denom);

	public void sendError(int http_status_code, String message) throws IOException;
	public void sendRedirect(String location) throws IOException;
		
	public void append(String text);
	public void flush(); // Currently used in CollectionToWorksetAction, but not clear if flush() actually needed now

	public OutputStream getOutputStream() throws IOException;

	public boolean isClosed();
	public void close();

	
}
