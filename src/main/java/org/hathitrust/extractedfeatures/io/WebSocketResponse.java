package org.hathitrust.extractedfeatures.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.json.JSONObject;

public class WebSocketResponse implements FlexiResponse 
{
	protected Session ws_session_;
	protected RemoteEndpoint ws_endpoint_;

	protected String output_filename_ = null;
	protected String full_output_filename_ = null;
	
	protected static JSONFileManager json_file_manager_ = null;
	
	public static void setJSONFileManager(JSONFileManager json_file_manager)
	{
		json_file_manager_ = json_file_manager;
	}
	
	public WebSocketResponse(Session websocket_session)
	{
	    ws_session_ = websocket_session;
		ws_endpoint_ = websocket_session.getRemote();
	}
	
	
	public JSONObject generateOKMessageTemplate(String action)
	{
		JSONObject response_json = new JSONObject();
		
		response_json.put("status",HttpServletResponse.SC_OK);
		response_json.put("action",action);
		
		return response_json;
	}
	
	protected JSONObject generateErrorMessageTemplate(int http_status_code, String message)
	{
		JSONObject response_json = new JSONObject();
		
		response_json.put("status",http_status_code);
		response_json.put("action","error");
		response_json.put("message",message);
		
		return response_json;
	}
	
	public void sendMessage(JSONObject response_json)
	{
		String response_json_str = response_json.toString();

		try {
			ws_endpoint_.sendString(response_json_str);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void setContentType(String content_type)
	{
		JSONObject response_json = generateOKMessageTemplate("content-type");
		response_json.put("content-type",content_type);	
		
		sendMessage(response_json);
	}

	public void setCharacterEncoding(String encoding)
	{
		if (!encoding.toLowerCase().equals("utf-8")) {
			String mess ="Warning cannot set socket resonse stream to '"+encoding+"' => assuming UTF-8";
			System.err.println("WebSocketReponse.setCharacterEncoding(): " + mess);
		}
		// But if we did want to find a way to support different char encodings, it might
		// go something like the following and/or rely on something like encodeURIComponent
		//JSONObject response_json = generateOKMessageTemplate("header");
		//response_json.put("header-name","character-encoding");	
		//response_json.put("header-value",encoding);	

	}
	
	public void setContentLength(int len)
	{
		JSONObject response_json = generateOKMessageTemplate("content-length");
		response_json.put("length",len);
		
		sendMessage(response_json);
	}
	
	public void setHeader(String header_name, String header_value)
	{
		if (header_name.equals("Content-Disposition")) {
			output_filename_ = header_value;
		}
		
		JSONObject response_json = generateOKMessageTemplate("header");
		response_json.put("header-name",header_name);	
		response_json.put("header-value",header_value);	

		sendMessage(response_json);
	}
	
	
	public void sendProgress(double percentage)
	{
		String percentage_formatted = String.format("%.2f", percentage);
		
		String mess = "Thread: " + Thread.currentThread().getName() + ", progress " + percentage_formatted + "%";
		
		JSONObject response_json = generateOKMessageTemplate("progress");
		response_json.put("percentage",percentage);	
		response_json.put("message",mess);	
		
		sendMessage(response_json);
	}
	

	public void sendError(int http_status_code, String message) throws IOException
	{
		JSONObject response_json = generateErrorMessageTemplate(http_status_code,message);		
		sendMessage(response_json);
	}

	public void sendRedirect(String location) throws IOException
	{
		String mess ="Redirect to location '" + location +" not currently implemented on the JS side";
		System.err.println("WebSocketReponse.snedRedirectc(): " + mess);
	
		JSONObject response_json = generateOKMessageTemplate("redirect");
		response_json.put("location",location);	
		sendMessage(response_json);
	}
	
	public void append(String text)
	{
		try {
			ws_endpoint_.sendString(text);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void flush()
	{
		System.err.println("**** WebSocketResponse.flush(): Not implemented (No known equivalent)");
	}
	
	public OutputStream getOutputStream() throws IOException
	{
		// **** As a result of this requirement, the file_manager is no longer
		// just about JSON files.  Also, concerning the WebSocketResponse, this
		// doesn't even need to be about Zip files, so even the name of the
		// method isn't the best
		// => candidate for refactoring!
		
		File output_file = json_file_manager_.getFullZipFilename(output_filename_);
		full_output_filename_ = output_file.getAbsolutePath(); // not currently used, delete? // ****
		
		OutputStream os = new FileOutputStream(output_file);

		return os;
	}
	
	public String getFullOutputFilename()
	{
		return full_output_filename_;	
	}
	
	public void close()
	{
		System.err.println("**** WebSocketResponse.close(): Closing WebSocket session");
		ws_session_.close();
		
	}
	
}

