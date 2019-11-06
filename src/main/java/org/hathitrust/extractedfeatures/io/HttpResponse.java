package org.hathitrust.extractedfeatures.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletResponse;

public class HttpResponse implements FlexiResponse 
{
	protected HttpServletResponse http_response_;
	
	public HttpResponse(HttpServletResponse http_response)
	{
		http_response_ = http_response;
	}
	
	public void setContentType(String content_type)
	{
		http_response_.setContentType(content_type);
	}
	
	/*
	public PrintWriter getWriter()
	{
		PrintWriter pw = null;

		try {
			pw = http_response_.getWriter();
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return pw;
	}
*/
	
	public void setCharacterEncoding(String encoding)
	{
		http_response_.setCharacterEncoding(encoding);
	}
	
	public void setHeader(String header_name, String header_value)
	{
		http_response_.setHeader(header_name,header_value);
	}

	public void sendProgress(double percentage)
	{
		String percentage_formatted = String.format("%.2f", percentage);
		
		String mess = "Thread: " + Thread.currentThread().getName() + ", progress " + percentage_formatted + "%";
		System.out.println(mess);
	}
	
	public void sendError(int http_status_code, String message) throws IOException
	{
		http_response_.sendError(http_status_code, message);
	}
	
	public void sendRedirect(String location) throws IOException
	{
		http_response_.sendRedirect(location);
	}
	
	
	public void append(String text)
	{
		try {
			PrintWriter pw = http_response_.getWriter();
			pw.append(text);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void flush()
	{
		try {
			PrintWriter pw = http_response_.getWriter();
			pw.flush();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public OutputStream getOutputStream() throws IOException
	{
		 OutputStream ros = http_response_.getOutputStream();

		 return ros;
	}

	public void close()
	{
		try {
			PrintWriter pw = http_response_.getWriter();
			pw.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
