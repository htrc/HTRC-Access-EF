package org.hathitrust.extractedfeatures.action;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hathitrust.extractedfeatures.VolumeUtils;
import org.hathitrust.extractedfeatures.io.JSONFileManager;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * Servlet implementation class VolumeCheck
 */
public class DownloadJSONAction extends BaseAction
{
	//private static final long serialVersionUID = 1L;
	
	protected final int DOWNLOAD_BUFFER_SIZE = 1024;

	protected JSONFileManager json_file_manager_;
	
	public String getHandle() 
	{
		return "download-ids";
	}
	
	public String[] getDescription()
	{
		String[]  mess =
			{ "Download HTRC Extracted Features JSON files for the given IDs.",
					"Required parameter: 'id' or 'ids'\n"
				   +"Optional parameter: 'output=json|zip (defaults to 'json')",
					"Returns:            Uncompressed JSON Extracted Feature file content for given id(s);\n"
				    + "                    or a zipped up version, when output=zipfile."
					+ "                  To return just the volume level metadata specify 'id' in the form mdp.123456789-metata"
					+ "                  To return just the page level JSON specify 'id' in the form mdp.123456789-seq-000000"
							};
		
		return mess;
	}
	
	public DownloadJSONAction(ServletContext context, ServletConfig config)
	{	super(context);
		json_file_manager_ = JSONFileManager.getInstance(config);
	}

	public void outputVolume(HttpServletResponse response, String[] download_ids) 
			throws ServletException, IOException
	{	
		response.setContentType("text/plain");
		response.setCharacterEncoding("UTF-8");
		
		for (int i=0; i<download_ids.length; i++) {
			String download_id = download_ids[i];

			String volume_id = download_id;
			boolean has_seq_num = false;
			boolean has_metadata = false;

			String seq_num_str = null;
			int seq_num = 0;

			Matcher seq_matcher = seq_patt_.matcher(download_id);
			if (seq_matcher.matches()) {
				has_seq_num = true;
				volume_id = seq_matcher.group(1);
				seq_num_str = seq_matcher.group(2);
				seq_num = Integer.parseInt(seq_num_str);
			}
			else {
				Matcher md_matcher = metadata_patt_.matcher(download_id);
				if (md_matcher.matches()) {
					volume_id = md_matcher.group(1);
					has_metadata = true;
				}
			}

			String json_content_str = json_file_manager_.getVolumeContent(volume_id);

			if (json_content_str == null) {
				if (json_file_manager_.usingRsync()) {
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Rsync failed");
					break;
				}
				else {
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "File failed");
					break;
				}
			}
			else {
				if (has_seq_num) {
					// consider having a page-level cache
					JSONObject json_ef = new JSONObject(json_content_str);
					JSONObject json_ef_features = json_ef.getJSONObject("features");
					JSONArray json_ef_pages = json_ef_features.getJSONArray("pages");

					int index_pos = seq_num -1; // sequence numbers start at 1, but indexes don't!!
					if ((index_pos>=0) && (index_pos < json_ef_pages.length())) {
						JSONObject json_ef_page = json_ef_pages.getJSONObject(index_pos);
						json_content_str = json_ef_page.toString();
					}
					else {
						json_content_str = "{ error: \"Seq number '" + seq_num_str + "' out of bounds\"}";
					}
				}			
				else if (has_metadata) {
					// consider having a metadata cache
					JSONObject json_ef = new JSONObject(json_content_str);
					JSONObject json_ef_metadata = json_ef.getJSONObject("metadata");
					json_content_str = json_ef_metadata.toString();
				}
				// Otherwise, leave full volume JSON content alone
				

				PrintWriter pw = response.getWriter();
				pw.append(json_content_str);
			}
		}
		
	}
	
	public void outputZippedVolumes(HttpServletResponse response, String[] download_ids) 
			throws ServletException, IOException
	{
		int download_ids_len = download_ids.length;

		ZipOutputStream zbros = null;
		OutputStream download_os = null;

		boolean output_as_zip = (download_ids_len > 1);

		if (output_as_zip) {
			// Output needs to be zipped up
			response.setContentType("application/zip");
			response.setHeader("Content-Disposition","attachment; filename=htrc-ef-export.zip");

			OutputStream ros = response.getOutputStream();
			BufferedOutputStream bros = new BufferedOutputStream(ros);
			zbros = new ZipOutputStream(bros);

			download_os = zbros;
		}

		for (int i=0; i<download_ids_len; i++) {

			String download_id = download_ids[i];
		
			// rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
			String full_json_filename = VolumeUtils.idToPairtreeFilename(download_id);
			File file = json_file_manager_.fileOpen(full_json_filename);

			if (file == null) {
				if (json_file_manager_.usingRsync()) {
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Rsync failed");
				}
				else {
					response.sendError(HttpServletResponse.SC_BAD_REQUEST, "File failed");
				}
				break;
			}
			else {
				FileInputStream fis = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fis);
				String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);

				if (output_as_zip) {
					ZipEntry zipentry = new ZipEntry(json_filename_tail);
					zbros.putNextEntry(zipentry);
				}
				else {
					response.setContentType("application/x-bzip2");
					response.setHeader("Content-Disposition",
							"attachment; filename=\"" + json_filename_tail + "\"");

					OutputStream ros = response.getOutputStream();
					download_os = new BufferedOutputStream(ros);
				}		

				byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];

				while (true) {
					int num_bytes = bis.read(buf);
					if (num_bytes == -1) {
						break;
					}
					download_os.write(buf, 0, num_bytes);
				}

				bis.close();	    
				if (output_as_zip) {
					zbros.closeEntry();
				}
				else {
					download_os.close();
				}

				if (json_file_manager_.usingRsync()) {
					// remove file retrieved over rsync
					file.delete(); // ****
				}
			}
		}

		if (output_as_zip) {
			download_os.close();
		}
	}
	
	public void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException
	{
		String cgi_download_id = request.getParameter("id");
		String cgi_download_ids = request.getParameter("ids");
		String cgi_output = request.getParameter("output");
		
		if (cgi_output == null) {
			cgi_output = "json";
		}
		
		String[] download_ids = null;
		
		if (cgi_download_ids != null) {
			download_ids = cgi_download_ids.split(",");
		}
		else {
			download_ids = new String[] {cgi_download_id};
		}
			

		if (validityCheckIDs(response, download_ids)) {
			
			if (cgi_output.equals("zip")) {
				outputZippedVolumes(response,download_ids);
			}
			else if (cgi_output.equals("json")) {
				outputVolume(response,download_ids);
			}
			else {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unrecognized parameter value to action '" + getHandle()
				+"' -- 'output' parameter must be 'json' or 'zip'.");	
			}
		}
		else {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter to action '" + getHandle()
			+"' -- either parameter 'id' or 'ids' must be specified.");
		}
	}
}
