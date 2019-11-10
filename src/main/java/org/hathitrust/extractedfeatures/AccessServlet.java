package org.hathitrust.extractedfeatures;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.JSONObject;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;

import org.hathitrust.extractedfeatures.action.IdMongoDBAction;
import org.hathitrust.extractedfeatures.action.KeyValueStorageAction;
import org.hathitrust.extractedfeatures.action.LCCLookupAction;
import org.hathitrust.extractedfeatures.action.BaseAction;
import org.hathitrust.extractedfeatures.action.CheckExistsAction;
import org.hathitrust.extractedfeatures.action.CollectionToWorksetAction;
import org.hathitrust.extractedfeatures.action.GuessLanguageAction;
import org.hathitrust.extractedfeatures.action.DownloadJSONAction;
import org.hathitrust.extractedfeatures.action.ICUTokenizeAction;
import org.hathitrust.extractedfeatures.action.ShoppingcartAction;
import org.hathitrust.extractedfeatures.action.URLShortenerAction;
import org.hathitrust.extractedfeatures.io.FlexiResponse;
import org.hathitrust.extractedfeatures.io.HttpResponse;
import org.hathitrust.extractedfeatures.io.RsyncEFFileManager;
import org.hathitrust.extractedfeatures.io.WebSocketResponse;


/**
 * Servlet implementation class VolumeCheck
 */
@WebSocket
public class AccessServlet extends WebSocketServlet 
{
	private static final long serialVersionUID = 1L;

	protected static CheckExistsAction check_exists_ = null;
	protected static DownloadJSONAction download_json_ = null;
	protected static LCCLookupAction lcc_lookup_ = null;
	
	protected static CollectionToWorksetAction col2workset_ = null;
	protected static ICUTokenizeAction icu_tokenize_ = null;
	protected static GuessLanguageAction guess_language_ = null;
	protected static URLShortenerAction url_shortener_ = null;
	protected static KeyValueStorageAction key_value_storage_ = null;
	protected static ShoppingcartAction shoppingcart_ = null;
	
	protected static ArrayList<BaseAction> action_list_ = null;

	public AccessServlet() {
	}

	/*
	protected static Map<String,String[]> convertParamMapListToMapArray(Map<String,List<String>> map_list)
	{
		Map<String,String[]> map_array = new HashMap<String,String[]>();
		
		for (String key: map_list.keySet()) {
			String [] val_array = (String[]) map_list.get(key).toArray();
			map_array.put(key, val_array );
		}
	
		return map_array;
	}
	*/
	
	protected static Map<String,List<String>> convertParamMapArrayToMapList(Map<String,String []> map_array)
	{
		Map<String,List<String>> map_list = new HashMap<String,List<String>>();
		
		for (String key: map_array.keySet()) {
			List<String> val_list = Arrays.asList(map_array.get(key));
			
			map_list.put(key, val_list);
		}
	
		return map_list;
	}
    
	
    
	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		RsyncEFFileManager rsyncef_file_manager = RsyncEFFileManager.getInstance(config);
		WebSocketResponse.setJSONFileManager(rsyncef_file_manager);
		
		ServletContext context = getServletContext();

		if (check_exists_ == null) {	
			check_exists_ = new CheckExistsAction(context,config);
		}

		if (download_json_ == null) {
			download_json_ = new DownloadJSONAction(context,config);
		}
		
		if (lcc_lookup_ == null) {
			lcc_lookup_ = new LCCLookupAction(context,config);
		}

		if (col2workset_ == null) {
			col2workset_ = new CollectionToWorksetAction(context,config);
		}

		if (icu_tokenize_ == null) {
			icu_tokenize_ = new ICUTokenizeAction(context,config);
		}

		if (guess_language_ == null) {
			guess_language_ = new GuessLanguageAction(context,config);
		}
		
		if (url_shortener_ == null) {
			url_shortener_ = new URLShortenerAction(context,config);
		}
		if (key_value_storage_ == null) {
			key_value_storage_ = new KeyValueStorageAction(context,config);
		}
		
		if (shoppingcart_ == null) {
			shoppingcart_ = new ShoppingcartAction(context,config);
		}
		
		if (action_list_ == null) {
			action_list_ = new ArrayList<BaseAction>();
			if (check_exists_.isOperational()) {
				action_list_.add(check_exists_);
			}
			if (download_json_.isOperational()) {
				action_list_.add(download_json_);
			}
			if (lcc_lookup_.isOperational()) {
				action_list_.add(lcc_lookup_);
			}
			if (col2workset_.isOperational()) {
				action_list_.add(col2workset_);
			}
			if (icu_tokenize_.isOperational()) {
				action_list_.add(icu_tokenize_);
			}
			if (guess_language_.isOperational()) {
				action_list_.add(guess_language_);
			}
			if (url_shortener_.isOperational()) {
				action_list_.add(url_shortener_);
			}
			if (key_value_storage_.isOperational()) {
				action_list_.add(key_value_storage_);
			}
			if (shoppingcart_.isOperational()) {
				action_list_.add(shoppingcart_);
			}
		}		
	}	

	protected void doFlexiGetLegacy(Map<String,List<String>> param_map, FlexiResponse flexi_response)
			throws ServletException, IOException 
	{

		String cgi_ids = BaseAction.getParameter(param_map,"check-ids");
		if (cgi_ids == null) {
			cgi_ids = BaseAction.getParameter(param_map,"ids");
		}
		String cgi_id = BaseAction.getParameter(param_map,"check-id");
		if (cgi_id == null) {
			cgi_id = BaseAction.getParameter(param_map,"id");
		}
		String cgi_download_id = BaseAction.getParameter(param_map,"download-id");
		String cgi_download_ids = BaseAction.getParameter(param_map,"download-ids");
		String cgi_convert_col = BaseAction.getParameter(param_map,"convert-col");

		// if cgi_id in play then upgrade to cgi_ids (one item in it) to simplify later code
		if (cgi_ids == null) {
			if (cgi_id != null) {
				cgi_ids = cgi_id;
			}
		}

		if (cgi_ids != null) {
			String[] ids = cgi_ids.split(",");
			check_exists_.outputJSON(flexi_response,ids);
		}
		else if (cgi_download_id != null) {

			String valid_cgi_download_id = check_exists_.validityCheckID(flexi_response, cgi_download_id);
			
			if (valid_cgi_download_id != null) {
				String [] valid_download_ids = new String[] {valid_cgi_download_id};
				download_json_.outputVolumes(flexi_response,valid_download_ids,DownloadJSONAction.OutputFormat.JSON,null,"json");
			}
		} 
		else if (cgi_download_ids != null) {
			String[] download_ids = cgi_download_ids.split(",");
			
			String [] valid_download_ids = check_exists_.validityCheckIDs(flexi_response, download_ids);
					
			if (valid_download_ids != null) {
			    download_json_.outputZippedVolumes(flexi_response,valid_download_ids,null);
			}
		} 
		else if (cgi_convert_col != null) {

			String cgi_col_title = BaseAction.getParameter(param_map,"col-title");
			if (cgi_col_title == null) {
				cgi_col_title = "htrc-workset-" + cgi_convert_col;
			}
			String cgi_a = BaseAction.getParameter(param_map,"a");
			String cgi_format = BaseAction.getParameter(param_map,"format");

			col2workset_.outputWorkset(flexi_response, cgi_convert_col, cgi_col_title, cgi_a, cgi_format);

		} 
		else {
			flexi_response.append("General Info: Number of HTRC Volumes in check-list = " + check_exists_.size() + "\n");
			flexi_response.append("Add '?action=' to URL get usage");
		}
	}
	
	protected void doGetLegacy(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		//HttpSession http_session = request.getSession();

		Map<String, String[]> param_map_array = request.getParameterMap();
		Map<String, List<String>> param_map_list = convertParamMapArrayToMapList(param_map_array);
		
		HttpResponse http_flexi_response = new HttpResponse(response);
		
		doFlexiGetLegacy(param_map_list,http_flexi_response);
		
		//pw.close();

	}

	protected void displayUsage(FlexiResponse flexi_response)
	{
		flexi_response.append("General Info: Number of HTRC Volumes in check-list = " + check_exists_.size() + "\n");
		flexi_response.append("\nSample id: mdp.39076000484811\n");
		flexi_response.append("====\n\n");

		flexi_response.append("Usage:\n");

		for (BaseAction action: action_list_) {

			flexi_response.append("  action=" + action.getHandle() + "\n");
			String[] mess = action.getDescription();
			for (String sm: mess) {
				flexi_response.append("    " + sm + "\n");
			}
			flexi_response.append("\n");
		}
		flexi_response.close();
	}

	protected void doFlexiGet(Map<String,List<String>> param_map_list, FlexiResponse flexi_response)
			throws ServletException, IOException 
	{	
		String action_handle = BaseAction.getParameter(param_map_list,"action");
		if (action_handle == null) {
			doFlexiGetLegacy(param_map_list,flexi_response);
			return;
		}
		
		boolean action_match = false;

		for (BaseAction action: action_list_) {
			if (action.getHandle().equals(action_handle)) {
				action_match = true;
				
				action.doAction(param_map_list,flexi_response);
				break;
			}
		}

		if (!action_match) {
			// No action given => generate usage statement
			flexi_response.setContentType("text/plain");
			displayUsage(flexi_response);
		}

	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException 
	{
		Map<String, String[]> param_map_array = request.getParameterMap();
		Map<String, List<String>> param_map_list = convertParamMapArrayToMapList(param_map_array);
		
		HttpResponse http_flexi_response = new HttpResponse(response);
		doFlexiGet(param_map_list,http_flexi_response);
	}


	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}


	/* WebSocket Section */
	
	@Override
	public void configure(WebSocketServletFactory factory) {
	    // Useful reference:
	    //   https://examples.javacodegeeks.com/enterprise-java/jetty/jetty-websocket-example/
	    factory.getPolicy().setIdleTimeout(10000);
	    factory.register(AccessServlet.class);
	    
	}

    // Some useful background, but ultimately none of the following was needed
	// https://stackoverflow.com/questions/26910094/is-using-system-identityhashcodeobj-reliable-to-return-unique-id/26910329#26910329
        // https://stackoverflow.com/questions/27671162/accessing-httpsession-inside-an-annotated-websocket-class-on-embedded-jetty-9
    
	private WebSocketResponse ws_flexi_response_ = null;
	
	@OnWebSocketConnect
	public void onConnect(Session session) throws IOException {
		System.out.println(session.getRemoteAddress().getHostString() + " connected!");
		
		UpgradeRequest upgrade_request = session.getUpgradeRequest();
		Map<String,List<String>> param_map =upgrade_request.getParameterMap();

		ws_flexi_response_ = new WebSocketResponse(session);
		
		String remote_host = session.getRemoteAddress().getHostString();						    
		System.out.println("WebSocket AccessServet.onConnect() from " + remote_host + " for: " + param_map.toString());

		//String thread_name = Thread.currentThread().getName();
		//System.out.println("WebSocket AccessServet.onConnect(): thread_name = " + thread_name);
		//System.out.println("WebSocket AccessServet.onConnect(): session = " + session);
	}

	@OnWebSocketMessage
	public void onText(Session session, String command) throws IOException {
		System.out.println("WebSocket AccessServet.onText() Message received:" + command);
		//String thread_name = Thread.currentThread().getName();
		//System.out.println("WebSocket AccessServet.onText(): thread_name = " + thread_name);
		//System.out.println("WebSocket AccessServet.onText(): session = " + session);
	
		if (session.isOpen()) {

			UpgradeRequest upgrade_request = session.getUpgradeRequest();
			Map<String,List<String>> param_map =upgrade_request.getParameterMap();
			
			if (command.equals("start-download")) {
				try {
					doFlexiGet(param_map, ws_flexi_response_);
								
					JSONObject json_response = ws_flexi_response_.generateOKMessageTemplate("download-complete");
					ws_flexi_response_.sendMessage(json_response);
				}
				catch (Exception e) {
					e.printStackTrace();
					ws_flexi_response_.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
				}
			}
			else {
				ws_flexi_response_.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Failed to recognize command: " + command);
			}

			
			/*
			String key = param_map.get("key").get(0);

			// Using synchronized block, get current 'progress' value from websocket_flexi_reponse
			
			JSONObject response_json = new JSONObject();
			if (command.equals("monitor-status")) {
				response_json.put("status",200);		    
				response_json.put("key",key);
			}
			else {
				response_json.put("status",200);
				response_json.put("message","Failed to recognize in-coming command: " + command);
			}
			
			
			String response = response_json.toString();
			session.getRemote().sendString(response);
			*/

		}
	}

	@OnWebSocketClose
	public void onClose(Session session, int status, String reason) {
		String remote_host = session.getRemoteAddress().getHostString();						    
		System.err.println("WebSocket AccessServet.onClose() from " + remote_host);
		ws_flexi_response_.close();
	}
}
