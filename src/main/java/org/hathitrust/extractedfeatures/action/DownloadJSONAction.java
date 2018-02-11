package org.hathitrust.extractedfeatures.action;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
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
	public enum OutputFormat { JSON, ZIP, CSV, TSV };
	protected static String[] OutputFormatFieldSeparator_ = new String[] { null, null, ",", "\t" };

	protected enum JsonExtractMode { Volume, Metadata, Seq };
	
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
				   +"Optional parameter: 'output=json|zip|csv|tsv (defaults to 'json')",
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

	protected String jsonToFieldSeparatedFileKeys(JSONObject json_obj, String sep)
	{
		StringBuilder sb = new StringBuilder();
		
		Iterator<String> key_iterator = json_obj.keys();
		while (key_iterator.hasNext()) {
			String key = key_iterator.next();
			sb.append(key);
			
			if (key_iterator.hasNext()) {
				sb.append(sep);
			}
		}
		sb.append("\n");
		return sb.toString();
	}
	
	protected String jsonToFieldSeparatedFileValues(JSONObject json_obj, String sep)
	{
		// Needs escaping added in! // ****
		
		StringBuilder sb = new StringBuilder();
		
		Iterator<String> key_iterator = json_obj.keys();
		while (key_iterator.hasNext()) {
			String key = key_iterator.next();
			
			Object val_obj = json_obj.get(key);
			
			if (val_obj instanceof JSONObject) {
				JSONObject val_json_obj = (JSONObject)val_obj;
				sb.append(val_json_obj.toString());
			}
			else if (val_obj instanceof JSONArray) {
				JSONArray val_json_array = (JSONArray)val_obj;
				sb.append(val_json_array.toString());
			}
			else {
				// primitive type
				String val_str = val_obj.toString(); //json_obj.getString(key);
				sb.append(val_str);
			}
			
			if (key_iterator.hasNext()) {
				sb.append(sep);
			}
		}
		sb.append("\n");
		return sb.toString();
	}
	
	protected String outputExtractVolumeMetadata(String json_content_str_in, OutputFormat output_format, boolean first_entry)
	{
		String json_content_str_out = null;
		
		JSONObject json_ef = new JSONObject(json_content_str_in);
		JSONObject json_ef_metadata = json_ef.getJSONObject("metadata");
		
		if (output_format == OutputFormat.CSV || output_format == OutputFormat.TSV) {
			String field_sep = OutputFormatFieldSeparator_[output_format.ordinal()];

			if (first_entry) {
				json_content_str_out = jsonToFieldSeparatedFileKeys(json_ef_metadata,field_sep);
			}
			else {
				json_content_str_out = "";
			}
		
			json_content_str_out += jsonToFieldSeparatedFileValues(json_ef_metadata,field_sep);
		}
		else {
			json_content_str_out = json_ef_metadata.toString();
		}
		return json_content_str_out;
	}
	
	protected String outputExtractPage(String json_content_str_in, int seq_num, OutputFormat output_format, boolean first_entry)
	{
		String json_content_str_out = null;
		
		JSONObject json_ef = new JSONObject(json_content_str_in);
		JSONObject json_ef_features = json_ef.getJSONObject("features");
		JSONArray json_ef_pages = json_ef_features.getJSONArray("pages");

		int index_pos = seq_num -1; // sequence numbers start at 1, but indexes don't!!
		if ((index_pos>=0) && (index_pos < json_ef_pages.length())) {
			JSONObject json_ef_page = json_ef_pages.getJSONObject(index_pos);

			if (output_format == OutputFormat.CSV || output_format == OutputFormat.TSV) {
				
				String field_sep = OutputFormatFieldSeparator_[output_format.ordinal()];
				
				if (first_entry) {
					json_content_str_out = jsonToFieldSeparatedFileKeys(json_ef_page,field_sep);
				}
				else {
					json_content_str_out = "";
				}
				json_content_str_out += jsonToFieldSeparatedFileValues(json_ef_page,field_sep);
			}
			else {
				json_content_str_out = json_ef_page.toString();
			}
		}
		else {
			json_content_str_out = "{ error: \"Seq number '" + seq_num + "' out of bounds\"}";
		}
		
		return json_content_str_out;
	}
	
	
	public void outputVolume(HttpServletResponse response, String[] download_ids, OutputFormat output_format) 
			throws ServletException, IOException
	{	
		response.setContentType("text/plain");
		response.setCharacterEncoding("UTF-8");
		
		PrintWriter pw = response.getWriter();
		
		int download_ids_len = download_ids.length;
		if (download_ids_len > 1) {
			if (output_format.equals("json")) {
				pw.append("[");
			}
		}
		
		boolean first_entry = true;
		
		for (int i=0; i<download_ids_len; i++) {
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
					json_content_str = outputExtractPage(json_content_str, seq_num, output_format, first_entry);
				
				}			
				else if (has_metadata) {
					// consider having a metadata cache
					json_content_str = this.outputExtractVolumeMetadata(json_content_str,output_format,first_entry);
				}
				// Otherwise, leave full volume JSON content alone
				

				pw.append(json_content_str);
				
				if ((download_ids_len > 1) && ((i+1) < download_ids_len)) {
					if (output_format.equals("json")) {
						pw.append(",");
					}
				}
			}
			
			first_entry = false;
		}
		
		if (download_ids_len > 1) {
			if (output_format.equals("json")) {
				pw.append("]");
			}
		}
	}
	
	protected void outputZippedVolumesAdaptive(HttpServletResponse response, String[] download_ids) 
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
	
	public void outputZippedVolumes(HttpServletResponse response, String[] download_ids) 
			throws ServletException, IOException
	{
		int download_ids_len = download_ids.length;

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition","attachment; filename=htrc-ef-export.zip");

		OutputStream ros = response.getOutputStream();
		BufferedOutputStream bros = new BufferedOutputStream(ros);
		ZipOutputStream zbros = new ZipOutputStream(bros);
		OutputStream download_os = zbros;
		
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

				ZipEntry zipentry = new ZipEntry(json_filename_tail);
				zbros.putNextEntry(zipentry);

				byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];

				while (true) {
					int num_bytes = bis.read(buf);
					if (num_bytes == -1) {
						break;
					}
					download_os.write(buf, 0, num_bytes);
				}

				bis.close();	    
				zbros.closeEntry();

				if (json_file_manager_.usingRsync()) {
					// remove file retrieved over rsync
					file.delete(); // ****
				}
			}
		}

		download_os.close();
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
			else if (cgi_output.equals("json") || cgi_output.equals("csv") || cgi_output.equals("tsv")) {
				OutputFormat output_format = null;
				if (cgi_output.equals("json") ) {
					output_format = OutputFormat.JSON;
				}
				else if (cgi_output.equals("csv") ) {
					output_format = OutputFormat.CSV;
				}
				if (cgi_output.equals("tsv") ) {
					output_format = OutputFormat.TSV;
				}
				outputVolume(response,download_ids,output_format);
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
