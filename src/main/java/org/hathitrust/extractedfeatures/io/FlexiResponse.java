package org.hathitrust.extractedfeatures.io;

import java.io.IOException;
import java.io.OutputStream;

public interface FlexiResponse 
{
	public void setContentType(String content_type);
	public void setCharacterEncoding(String encoding);
	public void setHeader(String header_name, String header_value);
	
	public void sendError(int http_status_code, String message) throws IOException;
	public void sendRedirect(String location) throws IOException;
		
	public void append(String text);
	public void flush();

	public OutputStream getOutputStream() throws IOException;

	public void close();
	
}
