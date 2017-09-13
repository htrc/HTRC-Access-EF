package org.hathitrust.extractedfeatures;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Servlet implementation class VolumeCheck
 */
public class AccessServlet extends HttpServlet 
{
	private static final long serialVersionUID = 1L;
	protected static final String ht_col_url = "https://babel.hathitrust.org/cgi/mb";

	protected static VolumeCheck vol_check_ = null;
	protected static JSONFileManager json_file_manager_ = null;
	
	protected final int DOWNLOAD_BUFFER_SIZE = 1024;

	public AccessServlet() {
	}


	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		if (vol_check_ == null) {
			ServletContext servletContext = getServletContext();
			vol_check_ = new VolumeCheck(servletContext);
		}
		
		if (json_file_manager_ == null) {
			json_file_manager_ = new JSONFileManager(config);
		}
		
		
	}
	
	protected void doCollectionToWorkset(HttpServletResponse response, String col_title,
			String c, String a, String format) throws IOException {
		String post_url_params = "c=" + c + "&a=" + a + "&format=" + format;

		byte[] post_data = post_url_params.getBytes(StandardCharsets.UTF_8);
		int post_data_len = post_data.length;

		try {

			URL post_url = new URL(ht_col_url);
			HttpURLConnection conn = (HttpURLConnection) post_url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("charset", "utf-8");
			conn.setRequestProperty("Content-Length", Integer.toString(post_data_len));
			conn.setUseCaches(false);

			try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
				dos.write(post_data);
			}
			// try-resource auto-closes stream

			InputStream is = conn.getInputStream();
			InputStreamReader isr = new InputStreamReader(is);
			BufferedReader reader = new BufferedReader(isr);

			StringBuilder workset_friendly_sb = new StringBuilder();
			StringBuilder workset_unfriendly_sb = new StringBuilder();

			String line = null;
			int ci = 0;
			while ((line = reader.readLine()) != null) {
				if (ci == 0) {
					workset_friendly_sb.append("#" + line + "\n");
				} else {
					int first_tab_pos = line.indexOf("\t");
					String id = (first_tab_pos > 0) ? line.substring(0, first_tab_pos) : line;

					if (vol_check_.exists(id)) {
						workset_friendly_sb.append(line + "\n");
					} else {
						workset_unfriendly_sb.append("#" + line + "\n");
					}
				}

				ci++;
			}

			String col_title_filename = col_title + ".txt";
			response.setContentType("text/plain");
			response
			.setHeader("Content-Disposition", "attachment; filename=\"" + col_title_filename + "\"");

			PrintWriter pw = response.getWriter();
			pw.append(workset_friendly_sb.toString());

			if (workset_unfriendly_sb.length() > 0) {
				pw.append(
						"## The following volumes were listed in the HT collection, but have been omitted as they are not in the HTRC Extracted Feature dataset\n");
				pw.append(workset_unfriendly_sb.toString());
			}
			pw.flush();
		} catch (Exception e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,
					"Failed to convert HT collection to HTRC workset");
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
				json_file_manager_.outputVolumes(response,download_ids);
			}
		} 
		else if (cgi_convert_col != null) {

			String cgi_col_title = request.getParameter("col-title");
			String cgi_a = request.getParameter("a");
			String cgi_format = request.getParameter("format");

			if ((cgi_a == null) || (cgi_format == null)) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST,
						"Malformed arguments.  Need 'a', and 'format'");
			} else {
				if (cgi_col_title == null) {
					cgi_col_title = "htrc-workset-" + cgi_convert_col;
				}

				doCollectionToWorkset(response, cgi_col_title, cgi_convert_col, cgi_a, cgi_format);
			}

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
