package org.hathitrust.extractedfeatures;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hathitrust.extractedfeatures.action.CollectionToWorksetAction;
import org.hathitrust.extractedfeatures.action.DownloadJSONAction;
import org.hathitrust.extractedfeatures.action.VolumeCheckAction;


/**
 * Servlet implementation class VolumeCheck
 */
public class AccessServlet extends HttpServlet 
{
	private static final long serialVersionUID = 1L;


	protected static VolumeCheckAction vol_check_ = null;
	protected static DownloadJSONAction download_json_ = null;
	protected static CollectionToWorksetAction c2w_action_ = null;
	
	public AccessServlet() {
	}

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		if (vol_check_ == null) {
			ServletContext servletContext = getServletContext();
			vol_check_ = new VolumeCheckAction(servletContext);
		}
		
		if (download_json_ == null) {
			download_json_ = new DownloadJSONAction(config);
		}
		
		if (c2w_action_ == null) {
			c2w_action_ = new CollectionToWorksetAction(vol_check_);
		}
	}
	
	
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String cgi_ids = request.getParameter("ids");
		String cgi_id = request.getParameter("id");
		String cgi_download_id = request.getParameter("download-id");
		String cgi_download_ids = request.getParameter("download-ids");
		String cgi_convert_col = request.getParameter("convert-col");

		// if cgi_id in play then upgrade to cgi_ids (one item in it) to simplify later code
		if (cgi_ids == null) {
			if (cgi_id != null) {
				cgi_ids = cgi_id;
			}
		}

		// if cgi_id in play then upgrade to cgi_ids (one item in it) to simplify later code
		if (cgi_download_ids == null) {
			if (cgi_download_id != null) {
				cgi_download_ids = cgi_download_id;
			}
		}

		if (cgi_ids != null) {
			String[] ids = cgi_ids.split(",");
			vol_check_.outputJSON(response,ids);
		}
		else if (cgi_download_ids != null) {
			String[] download_ids = cgi_download_ids.split(",");
			
			if (vol_check_.validityCheckIDs(response, download_ids)) {
				download_json_.outputCompressedVolumes(response,download_ids);
			}
		} 
		else if (cgi_convert_col != null) {

			String cgi_col_title = request.getParameter("col-title");
			if (cgi_col_title == null) {
				cgi_col_title = "htrc-workset-" + cgi_convert_col;
			}
			String cgi_a = request.getParameter("a");
			String cgi_format = request.getParameter("format");
			
			c2w_action_.outputWorkset(response, cgi_convert_col, cgi_col_title, cgi_a, cgi_format);

		} 
		else {
			PrintWriter pw = response.getWriter();

			pw.append("General Info: Number of HTRC Volumes in check-list = " + vol_check_.size());

		}
		//pw.close();

	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		doGet(request, response);
	}

}
