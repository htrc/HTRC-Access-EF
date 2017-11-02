package org.hathitrust.extractedfeatures;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.hathitrust.extractedfeatures.action.BaseAction;
import org.hathitrust.extractedfeatures.action.CheckExistsAction;
import org.hathitrust.extractedfeatures.action.CollectionToWorksetAction;
import org.hathitrust.extractedfeatures.action.DownloadJSONAction;
import org.hathitrust.extractedfeatures.action.ICUTokenizeAction;


/**
 * Servlet implementation class VolumeCheck
 */
public class AccessServlet extends HttpServlet 
{
	private static final long serialVersionUID = 1L;

	protected static CheckExistsAction check_exists_ = null;
	protected static DownloadJSONAction download_json_ = null;
	protected static CollectionToWorksetAction col2workset_ = null;
    	protected static ICUTokenizeAction icu_tokenize_ = null;
	
	protected static ArrayList<BaseAction> action_list_ = null;
	
	public AccessServlet() {
	}

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		ServletContext context = getServletContext();
		
		if (check_exists_ == null) {	
			check_exists_ = new CheckExistsAction(context);
		}
		
		if (download_json_ == null) {
			download_json_ = new DownloadJSONAction(context,config);
		}
		
		if (col2workset_ == null) {
			col2workset_ = new CollectionToWorksetAction(context);
		}

		if (icu_tokenize_ == null) {
			icu_tokenize_ = new ICUTokenizeAction(context);
		}

		if (action_list_ == null) {
			action_list_ = new ArrayList<BaseAction>();
			action_list_.add(check_exists_);
			action_list_.add(download_json_);
			action_list_.add(col2workset_);
			action_list_.add(icu_tokenize_);
		}		
	}	
	
	protected void doGetLegacy(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		
		HttpSession http_session = request.getSession();
		
		String cgi_ids = request.getParameter("check-ids");
		if (cgi_ids == null) {
			cgi_ids = request.getParameter("ids");
		}
		String cgi_id = request.getParameter("check-id");
		if (cgi_id == null) {
			cgi_id = request.getParameter("id");
		}
		String cgi_download_id = request.getParameter("download-id");
		String cgi_download_ids = request.getParameter("download-ids");
		String cgi_convert_col = request.getParameter("convert-col");

		// if cgi_id in play then upgrade to cgi_ids (one item in it) to simplify later code
		if (cgi_ids == null) {
			if (cgi_id != null) {
				cgi_ids = cgi_id;
			}
		}

		
		if (cgi_ids != null) {
			String[] ids = cgi_ids.split(",");
			check_exists_.outputJSON(response,ids);
		}
		else if (cgi_download_id != null) {
			
			if (check_exists_.validityCheckID(response, cgi_download_id)) {
				download_json_.outputVolume(response,cgi_download_id);
			}
		} 
		else if (cgi_download_ids != null) {
			String[] download_ids = cgi_download_ids.split(",");
			
			if (check_exists_.validityCheckIDs(response, download_ids)) {
				download_json_.outputZippedVolumes(response,download_ids);
			}
		} 
		else if (cgi_convert_col != null) {

			String cgi_col_title = request.getParameter("col-title");
			if (cgi_col_title == null) {
				cgi_col_title = "htrc-workset-" + cgi_convert_col;
			}
			String cgi_a = request.getParameter("a");
			String cgi_format = request.getParameter("format");
			
			col2workset_.outputWorkset(response, cgi_convert_col, cgi_col_title, cgi_a, cgi_format);

		} 
		else {
			PrintWriter pw = response.getWriter();

			pw.append("General Info: Number of HTRC Volumes in check-list = " + check_exists_.size());

		}
		//pw.close();

	}

	protected void displayUsage(PrintWriter pw)
	{
		pw.append("General Info: Number of HTRC Volumes in check-list = " + check_exists_.size() + "\n");
		pw.append("====\n\n");
		
		pw.append("Usage:\n");
		
		for (BaseAction action: action_list_) {
		
			pw.append("  action=" + action.getHandle() + "\n");
			String[] mess = action.getDescription();
			for (String sm: mess) {
				pw.append("    " + sm + "\n");
			}
			pw.append("\n");
		}
		pw.close();
		
	}
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException 
	{
		String action_handle = request.getParameter("action");
		if (action_handle == null) {
			doGetLegacy(request,response);
			return;
		}
		
		boolean action_match = false;
		
		for (BaseAction action: action_list_) {
			if (action.getHandle().equals(action_handle)) {
				action_match = true;
				action.doAction(request,response);
				break;
			}
		}
		
		if (!action_match) {
			response.setContentType("text/plain");
			PrintWriter pw = response.getWriter();
			displayUsage(pw);
		}
		
	}
	
		
	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}

}
