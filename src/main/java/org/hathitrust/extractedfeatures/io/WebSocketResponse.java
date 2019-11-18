package org.hathitrust.extractedfeatures.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

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

	protected String output_filename_             = null;
	protected File   output_file_                 = null;
	protected File   generated_for_download_file_ = null;
	
	protected OutputStream os_             = null;
	protected OutputStreamWriter osw_      = null;
	
	protected static RsyncEFFileManager rsyncef_file_manager_ = null;
	
	public static void setJSONFileManager(RsyncEFFileManager rsyncef_file_manager)
	{
		rsyncef_file_manager_ = rsyncef_file_manager;
	}
	
	public WebSocketResponse(Session websocket_session)
	{
	    ws_session_ = websocket_session;
		ws_endpoint_ = websocket_session.getRemote();
	}
	
	public boolean isAsync() 
	{
		return true;
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
			ws_endpoint_.sendString(response_json_str); // java.io.IOException: Connection output is closed

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
		JSONObject response_json = generateOKMessageTemplate("header");
		response_json.put("header-name",header_name);	
		response_json.put("header-value",header_value);	

		sendMessage(response_json);
	}
	
	public void setContentDispositionAttachment(String filename)
	{
		if (output_filename_ != null) {
			String mess = "Error: WebSocketResponse.setContentDispositionAttachment()";
			if (os_ == null) {
				mess += " pre-existing file attachment already exists: " + output_filename_;
			
			}
			else {
				mess += " already streaming to output file: " + output_filename_;
			}
			System.err.println(mess);
		
			// pseudo sendError()
			JSONObject response_json = generateErrorMessageTemplate(HttpServletResponse.SC_BAD_REQUEST,mess);		
			sendMessage(response_json);
		}
		else {
			output_filename_ = filename;
			setHeader("Content-Disposition","attachment; filename=\""+filename+"\"");
		}
	}
	
	
	public void sendProgress(int numer, int denom)
	{
		double percentage = 100 * numer / (double)denom;
		
		String percentage_formatted;
		if (denom<150) {
			// when number of items around 100, nicer to show integer rounded percentages
			percentage_formatted = String.format("%d", Math.round(percentage));
		}
		else {
			percentage_formatted = String.format("%.2f", percentage);
		}
		
		String mess = "Thread: " + Thread.currentThread().getName() + ", progress " + percentage_formatted + "%";
		
		JSONObject response_json = generateOKMessageTemplate("progress");
		response_json.put("percentage",percentage);	
		response_json.put("percentage-formatted",percentage_formatted);	
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
		System.err.println("WebSocketReponse.snedRedirect(): " + mess);
	
		JSONObject response_json = generateOKMessageTemplate("redirect");
		response_json.put("location",location);	
		sendMessage(response_json);
	}
	
	public void append(String text)
	{
		try {
			OutputStreamWriter osw = getOutputStreamWriter();
			osw.append(text);
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/* Currently used in CollectionToWorksetAction, but not clear if flush() actually needed now */
	public void flush()
	{	
		if (osw_ != null) {
			try {
				// os_ is wrapped up inside osw_
				System.err.println("WebSocketResponse.flush(): Flushing internal file OS stream writer");
				osw_.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if (os_ != null) {
			try {
				System.err.println("WebSocketResponse.flush(): Flushing internal file OS stream");
				os_.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		System.err.println("WebSocketResponse.flush(): Is it possible to flush socket stream? (not currently implemented)");
	}
	
	
	public OutputStream getOutputStream() throws IOException
	{
		// **** As a result of this requirement, the file_manager is no longer
		// just about JSON files.  Also, concerning the WebSocketResponse, this
		// doesn't even need to be about Zip files, so even the name of the
		// method isn't the best
		// => candidate for refactoring!
		
		if (os_ == null) {
			output_file_ = rsyncef_file_manager_.getForDownloadFile(output_filename_);

			os_ = new FileOutputStream(output_file_);
		}
		
		return os_;
	}
	
	protected OutputStreamWriter getOutputStreamWriter() throws IOException
	{
		if (osw_ == null) {
			OutputStream os = getOutputStream();
			osw_ = new OutputStreamWriter(os,StandardCharsets.UTF_8);
		}
		
		return osw_;
	}
	
	synchronized public boolean isClosed()
	{
		return ws_session_ == null;
	}
	
	synchronized public void close()
	{
		if (osw_ != null) {
			// os_ is wrapped up inside osw_
			try {
				System.err.println("WebSocketResponse.close(): closing internal file OS stream writer");
				osw_.close();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else if (os_ != null) {
			try {
				System.err.println("WebSocketResponse.close(): closing internal file OS stream");
				os_.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		os_  = null;
		osw_ = null;
		
		// store the name of the file prepared for download for later use,
		// such as if it needs to delete it, if the preparation was exited early
		// by the user browsing to a different page
		generated_for_download_file_ = output_file_;
		output_filename_ = null;
		output_file_     = null;
		
		System.err.println("WebSocketResponse.close(): Closing WebSocket session");
		ws_session_.close();
		
		ws_session_ = null;
		
	}
	
	synchronized public void cleanupPartialForDownloadFile() 
	{	
		if (generated_for_download_file_ != null) {
			String full_output_filename_ = generated_for_download_file_.getAbsolutePath(); 
			
			if (generated_for_download_file_.exists()) {
				boolean removed_file = generated_for_download_file_.delete();
				if (!removed_file) {
					System.err.println("Error: failed to remove for-download file " + full_output_filename_ + " in WebSocketResponse::cleanupPartialForDownloadFile()");
				}
			}
			generated_for_download_file_ = null;
		}
		else {
			System.err.println("Warning: specified file to delete in WebSocketResponse::cleanupPartialForDownloadFile() is null");
		}
	}
}

