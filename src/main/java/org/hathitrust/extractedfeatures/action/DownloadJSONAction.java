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
					"Required parameter: 'id' or 'ids'",
					"Returns:            a JSON Extracted Feature file for a single ID;\n"
				    + "                        or a zipped up file of JSON files when multiple IDs requested."
			};
		
		return mess;
	}
	
	public DownloadJSONAction(ServletContext context, ServletConfig config)
	{	super(context);
		json_file_manager_ = JSONFileManager.getInstance(config);
	}

	public void outputVolume(HttpServletResponse response, String download_id) 
			throws ServletException, IOException
	{
		OutputStream download_os = null;
		
		String volume_id = download_id;
		String page_num_str = null;
		int page_num = 0;
		
		Pattern page_patt = Pattern.compile("^(.*)\\.page-(\\d+)$");
		
		Matcher matcher = page_patt.matcher(download_id);
		if (matcher.matches()) {
		  volume_id = matcher.group(1);
		  page_num_str =matcher.group(2);
		  page_num = Integer.parseInt(page_num_str);
		}
		
		// rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
		String full_json_filename = VolumeUtils.idToPairtreeFilename(volume_id);
		File file = json_file_manager_.fileOpen(full_json_filename);

		if (file == null) {
			if (json_file_manager_.usingRsync()) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Rsync failed");
			}
			else {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "File failed");
			}
		}
		else {
			String json_content_str = json_file_manager_.readCompressedTextFile(file);
			
		
			
			//String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);
			//response.setContentType("application/json");
			response.setContentType("text/plain");
			//response.setHeader("Content-Disposition","attachment; filename=\"" + json_filename_tail + "\"");

			response.setCharacterEncoding("UTF-8");
		
			if (page_num_str != null) {
				JSONObject json_ef = new JSONObject(json_content_str);
				JSONObject json_ef_features = json_ef.getJSONObject("features");
				JSONArray json_ef_pages = json_ef_features.getJSONArray("pages");
				int index_pos = page_num -1;
				if ((index_pos>0) && (index_pos < json_ef_pages.length())) {
					JSONObject json_ef_page = json_ef_pages.getJSONObject(index_pos);
					json_content_str = json_ef_page.toString();
				}
				else {
					json_content_str = "{ error: \"Page number '" + page_num_str + "' out of bounds\"}";
				}
			}			
			
			PrintWriter pw = response.getWriter();
			pw.append(json_content_str);
			/*
			
			response.setContentType("application/x-bzip2");
			response.setHeader("Content-Disposition",
					"attachment; filename=\"" + json_filename_tail + "\"");

			OutputStream ros = response.getOutputStream();
			download_os = new BufferedOutputStream(ros);


			byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];

			while (true) {
				int num_bytes = bis.read(buf);
				if (num_bytes == -1) {
					break;
				}
				download_os.write(buf, 0, num_bytes);
			}

			bis.close();	    

			download_os.close();
*/
			
			if (json_file_manager_.usingRsync()) {
				// remove file retrieved over rsync
				//file.delete(); // ****
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
					// file.delete(); ****
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
		
		if (cgi_download_ids != null) {
			String[] download_ids = cgi_download_ids.split(",");
		
			if (validityCheckIDs(response, download_ids)) {
				outputZippedVolumes(response,download_ids);
			}
		}
		else if (cgi_download_id != null) {
			if (validityCheckID(response, cgi_download_id)) {
				outputVolume(response,cgi_download_id);
			}
		}
		else {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter to action '" + getHandle()
			+"' -- either parameter 'id' or 'ids' must be specified.");
		}
	}
}
