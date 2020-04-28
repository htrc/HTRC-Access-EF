package org.hathitrust.extractedfeatures.action;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.hathitrust.extractedfeatures.VolumeUtils;
import org.hathitrust.extractedfeatures.io.FlexiResponse;
import org.hathitrust.extractedfeatures.io.HttpResponse;
import org.hathitrust.extractedfeatures.io.RsyncEFFileManager;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * Servlet implementation class VolumeCheck
 */
public class DownloadJSONAction extends URLShortenerAction
{
	public enum OutputFormat { JSON, ZIP, CSV, TSV };
	protected static String[] OutputFormatFieldSeparator_ = new String[] { null, null, ",", "\t" };

	protected enum JsonExtractMode { Volume, Metadata, Seq };

	protected final int DOWNLOAD_BUFFER_SIZE = 1024;

	protected RsyncEFFileManager rsyncef_file_manager_;

	public String getHandle() 
	{
		return "download-ids";
	}

	public String[] getDescription()
	{
		String[]  mess =
			{ "Download HTRC Extracted Features JSON files for the given ID(s) or key.",
					"Required parameter: 'id', 'ids' or 'key'\n"
							+"Optional parameter: 'output=json|zip|csv|tsv (defaults to 'json')"
							+"                    'output-filename-root=<filename-root> to control the output name used for the download",
							"Returns:            Uncompressed JSON Extracted Feature file content for given id(s);\n"
									+ "                    or a zipped up version, when output=zipfile."
									+ "                  To return just the volume level metadata specify 'id' in the form mdp.123456789-metadata"
									+ "                  To return just the page level JSON specify 'id' in the form mdp.123456789-seq-000000"
			};

		return mess;
	}

	public DownloadJSONAction(ServletContext context, ServletConfig config)
	{	
		super(context,config);
		rsyncef_file_manager_ = RsyncEFFileManager.getInstance(config);
	}

	protected static class VolumeMetadataByLookup {

		// Extracted Feature JSON file format originally had:
		//   htBibUrl,lastUpdateDate,isbn,imprint,accessProfile,language,typeOfResource,title,lccn,dateCreated,enumerationChronology,genre,pubPlace,hathitrustRecordNumber,schemaVersion,sourceInstitutionRecordNumber,volumeIdentifier,rightsAttributes,classification,pubDate,governmentDocument,sourceInstitution,bibliographicFormat,names,issn,handleUrl,oclc,issuance

		// But has now been extended to:
		//   htBibUrl,lastUpdateDate,imprint,isbn,accessProfile,language,typeOfResource,title,lccn,dateCreated,enumerationChronology,genre,pubPlace,hathitrustRecordNumber,subjectName,schemaVersion,sourceInstitutionRecordNumber,volumeIdentifier,rightsAttributes,classification,pubDate,governmentDocument,subjectTemporal,bibliographicFormat,sourceInstitution,names,issn,handleUrl,issuance,oclc,subjectGenre,subjectTopic,subjectTitleInfo,subjectGeographic,subjectOccupation,subjectCartographics

		// **Both types of files are in the archive **
		// Next 2 methods make use of the following list of metadata names
		protected static final String[] volume_metadata_lookup = new String[]
				{ "htBibUrl", "schemaVersion", "volumeIdentifier", "rightsAttributes", "title", "genre",
						"pubDate", "pubPlace", "typeOfResource", "bibliographicFormat", "language",
						"dateCreated", "lastUpdateDate", "imprint", "isbn", "issn", "oclc", "lccn", "classification", 
						"handleUrl", "hathitrustRecordNumber", "sourceInstitutionRecordNumber", "sourceInstitution",
						"accessProfile", "enumerationChronology", "governmentDocument", "names", "issuance", 
						"subjectGenre", "subjectTopic", "subjectName", "subjectTitleInfo", "subjectTemporal",
						"subjectGeographic", "subjectOccupation","subjectCartographics" };

		public static String jsonToFieldSeparatedFileKeys(JSONObject json_obj_unused, String sep)
		{
			// As a patch, this method was changed to use the static 'lookup' data-field above
			// (rather then the json_obj passed in) to ensure that all records that are output 
			// have exactly the same metadata fields in them.
			//
			// Originally all the JSON-EF objects on the rsync server had the same metadata field, 
			// but then a second batch of files was added in, and these had some additional fields added
			// leading to the problem in this code of CSV and TSV files being generated where some
			// of the rows of data didn't line up with the headings
			
			StringBuilder sb = new StringBuilder();

			for (int i=0; i<volume_metadata_lookup.length; i++) {
				if (i>0) {
					sb.append(sep);
				}

				String key = volume_metadata_lookup[i];
				sb.append(key);

			}

			sb.append("\n");

			return sb.toString();
		}

		public static String jsonToFieldSeparatedFileValues(JSONObject json_obj, String sep)
		{
			// Only called if outputting CSV or TSV 
			// Uses 'sep' == "," to detect CSV,
			//   escapes correctly by always wrapping up items in "..."  having escaped any literal quotes in value 
			// ****
			// But nothing done for TSV 
			//   => can tab turn up in values?
		
			StringBuilder sb = new StringBuilder();

			for (int i=0; i<volume_metadata_lookup.length; i++) {
				if (i>0) {
					sb.append(sep);
				}

				String key = volume_metadata_lookup[i];

				if (json_obj.has(key)) {
					Object val_obj = json_obj.get(key);

					String val_str = null;

					if (val_obj instanceof JSONObject) {
						JSONObject val_json_obj = (JSONObject)val_obj;
						val_str = val_json_obj.toString();
					}
					else if (val_obj instanceof JSONArray) {
						JSONArray val_json_array = (JSONArray)val_obj;
						val_str = val_json_array.toString();
					}
					else {
						// primitive type
						val_str = val_obj.toString(); //json_obj.getString(key);
					}
					if (sep.equals(",")) {
						// ensure the string is escaped: enclose in double quotes, change any existing " to ""
						val_str = val_str.replace("\"","\"\"");
						val_str = "\"" + val_str + "\"";
					}
					sb.append(val_str);
				}
			}

			sb.append("\n");

			return sb.toString();
		}
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
		// Only called if outputting CSV or TSV 
		// Uses 'sep' == "," to detect CSV,
		//   escapes correctly by always wrapping up items in "..."  having escaped any literal quotes in value 
		// ****
		// But nothing done for TSV 
		//   => can tab turn up in values?
		
		StringBuilder sb = new StringBuilder();

		Iterator<String> key_iterator = json_obj.keys();
		while (key_iterator.hasNext()) {
			String key = key_iterator.next();

			Object val_obj = json_obj.get(key);
			String val_str = null;

			if (val_obj instanceof JSONObject) {
				JSONObject val_json_obj = (JSONObject)val_obj;
				val_str = val_json_obj.toString();
			}
			else if (val_obj instanceof JSONArray) {
				JSONArray val_json_array = (JSONArray)val_obj;
				val_str = val_json_array.toString();
			}
			else {
				// primitive type
				val_str = val_obj.toString(); //json_obj.getString(key);
			}
			if (sep.equals(",")) {
				// ensure the string is escaped: enclose in double quotes, change any existing " to ""
				val_str = val_str.replace("\"","\"\"");
				val_str = "\"" + val_str + "\"";
			}
			sb.append(val_str);

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
				json_content_str_out = VolumeMetadataByLookup.jsonToFieldSeparatedFileKeys(json_ef_metadata,field_sep);
			}
			else {
				json_content_str_out = "";
			}

			json_content_str_out += VolumeMetadataByLookup.jsonToFieldSeparatedFileValues(json_ef_metadata,field_sep);
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


	
	protected String getDownloadFilename(String filename_root, String opt_cgi_key, String opt_file_ext)
	{
		String output_filename = filename_root;
		if (opt_cgi_key != null) {
			output_filename += "-" + opt_cgi_key;
		}

		if (opt_file_ext != null) {
			output_filename += opt_file_ext;
		}

		return output_filename;
	}

	protected void setHeaderDownloadFilename(FlexiResponse flexi_response, String output_filename)
	{
		flexi_response.setContentDispositionAttachment(output_filename);
	}
	protected void streamExistingVolumesFile(FlexiResponse flexi_response, File input_file)
			throws ServletException, IOException
	{
		//System.err.println("**** Streaming existing volumes file:" + input_file.getAbsolutePath());
		
		OutputStream ros = flexi_response.getOutputStream();
		BufferedOutputStream bros = new BufferedOutputStream(ros);

		FileInputStream fis = new FileInputStream(input_file);
		BufferedInputStream bis = new BufferedInputStream(fis);

		int input_filesize = (int) input_file.length();
		flexi_response.setContentLength(input_filesize);

		byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];

		while (true) {
			int num_bytes = bis.read(buf);
			if (num_bytes == -1) {
				break;
			}
			bros.write(buf, 0, num_bytes);
		}

		bis.close();	    
		bros.close();
	}


	public void concatAndStreamVolumes(FlexiResponse flexi_response, String[] download_ids, OutputFormat output_format) 
			throws ServletException, IOException
	{
		boolean concat_up_interrupted = false;
		
		int download_ids_len = download_ids.length;

		if (download_ids_len > 1) {
			if (output_format == OutputFormat.JSON) {
				if (!flexi_response.isClosed()) {
					flexi_response.append("[");
				}
				else {
					System.err.println("DownloadJSONAction::concatAndStreamVolumes() Failed to output any data");
					return;
				}
			}
		}

		boolean first_entry = true;

		for (int i=0; i<download_ids_len; i++) {
			flexi_response.sendProgress(i,download_ids_len);

			String download_id = download_ids[i];

			String volume_id = download_id;
			boolean has_seq_num = false;
			boolean has_metadata = false;

			String seq_num_str = null;
			int seq_num = 0;

			Matcher seq_matcher = IdentiferRegExp.SeqPattern.matcher(download_id);
			if (seq_matcher.matches()) {
				has_seq_num = true;
				volume_id = seq_matcher.group(1);
				seq_num_str = seq_matcher.group(2);
				seq_num = Integer.parseInt(seq_num_str);
			}
			else {
				Matcher md_matcher = IdentiferRegExp.MetadataPattern.matcher(download_id);
				if (md_matcher.matches()) {
					volume_id = md_matcher.group(1);
					has_metadata = true;
				}
			}


			// ****
			//String json_content_str = rsyncef_file_manager_.getVolumeContent(volume_id);
			String pairtree_or_stubby_full_json_filename_bz = VolumeUtils.idToRsyncFilename(volume_id);
			File file_bz = rsyncef_file_manager_.fileOpen(pairtree_or_stubby_full_json_filename_bz); 
			
			if (file_bz == null) {
				if (rsyncef_file_manager_.usingRsync()) {
					flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Rsync failed");
				}
				else {
					flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "File failed");
				}
				
				if (i > 0) {
					System.err.println("Error occurred partway through generating JSON files.  Concatenated content will be incomplete");
					if (flexi_response.isAsync()) {
						concat_up_interrupted = true;
					}
				}
			}
			else {
				String json_content_str = rsyncef_file_manager_.readCompressedTextFile(file_bz);
				
				if (json_content_str.equals("")) {
					rsyncef_file_manager_.fileClose(pairtree_or_stubby_full_json_filename_bz); 
					throw new IOException("Error: Reading compressed file: " + file_bz.getAbsolutePath() + " returned empty string");
				}
				
				if (has_seq_num) {
					// consider having a page-level cache // ****
					json_content_str = outputExtractPage(json_content_str, seq_num, output_format, first_entry);

				}			
				else if (has_metadata) {
					// consider having a metadata cache // ****
					json_content_str = this.outputExtractVolumeMetadata(json_content_str,output_format,first_entry);
				}
				
				// Otherwise, leave full volume JSON content alone
				
				if (!flexi_response.isClosed()) {
					flexi_response.append(json_content_str);
				}
				else {
					concat_up_interrupted = true;
				}
				
				if ((download_ids_len > 1) && ((i+1) < download_ids_len)) {
					if (output_format == OutputFormat.JSON) {
						if (!flexi_response.isClosed()) {
							flexi_response.append(",");
						}
						else {
							concat_up_interrupted = true;
						}
					}
				}
			}
			
			rsyncef_file_manager_.fileClose(pairtree_or_stubby_full_json_filename_bz); 
			
			if (concat_up_interrupted) {
				break;
			}
			
			first_entry = false;
		}

		if (download_ids_len > 1) {
			if (output_format == OutputFormat.JSON)  {
				if (!flexi_response.isClosed()) {
					flexi_response.append("]");
				}
			}
		}
		
		if (!concat_up_interrupted) {
			// Everything completed satisfactorily
			flexi_response.sendProgress(download_ids_len,download_ids_len); // 100 %
		}
	}

	public void outputVolumes(FlexiResponse flexi_response, String[] download_ids, OutputFormat output_format,
			String opt_cgi_key, String cgi_output, String output_filename) 
					throws ServletException, IOException
	{
		if (output_format == OutputFormat.JSON) {
			flexi_response.setContentType("application/json");
		}
		else {
			flexi_response.setContentType("text/plain");
		}

		flexi_response.setCharacterEncoding("UTF-8");

		setHeaderDownloadFilename(flexi_response,output_filename);

		File input_file = rsyncef_file_manager_.getForDownloadFile(output_filename);
		//System.err.println("*** Testing for existence of: " + input_file.getAbsolutePath());
				
		if (input_file.exists()) {
			if (flexi_response.isAsync()) {
				// Nothing to do => mark progress as 100% 
				// This triggers WS close from client, and then client initiates browser download action
				flexi_response.sendProgress(100,100);
			}
			else {
				// Synchronous case => stream over file
				streamExistingVolumesFile(flexi_response, input_file);
			}
		}
		else {
			concatAndStreamVolumes(flexi_response, download_ids, output_format);
		}
	}

	/*
	protected void outputZippedVolumesAdaptive(HttpResponse http_flexi_response, String[] download_ids, String opt_cgi_key) 
			throws ServletException, IOException
	{
		// ****
		// This method is no longer needed by the CGI API front-end
		// If bringing back into the front-line (through a CGI API action)
		// then it will need to be converted to supporting WebSocketReponse
		// For now the flexi_reponse parameter has been change to HttpResponse to refelct it
		// is does not have the async behaviour needed by a web-socket call
				
		// This version adaptively works out if it can download a single file or else needs
		// to zip things up
		
		int download_ids_len = download_ids.length;

		ZipOutputStream zbros = null;
		OutputStream download_os = null;

		boolean output_as_zip = (download_ids_len > 1);

		if (output_as_zip) {
			// Output needs to be zipped up
			http_flexi_response.setContentType("application/zip");
			// **** Rework this methods params to pass in the export root filename, like its outputZippedVolumes counterpart
			String output_zip_filename = getDownloadFilename("htrc-ef-export",opt_cgi_key,".zip");
			setHeaderDownloadFilename(http_flexi_response,output_zip_filename);

			OutputStream ros = http_flexi_response.getOutputStream();
			BufferedOutputStream bros = new BufferedOutputStream(ros);
			zbros = new ZipOutputStream(bros);

			download_os = zbros;
		}

		for (int i=0; i<download_ids_len; i++) {

			http_flexi_response.sendProgress(i+1,download_ids_len);

			String download_id = download_ids[i];

			// rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
			String pairtree_full_json_filename_bz = VolumeUtils.idToPairtreeFilename(download_id);
			File file = rsyncef_file_manager_.fileOpen(pairtree_full_json_filename_bz); 

			if (file == null) {
				if (rsyncef_file_manager_.usingRsync()) {
					http_flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Rsync failed");
				}
				else {
					http_flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "File failed");
				}
				break;
			}
			else {
				FileInputStream fis = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fis);
				String json_filename_tail = VolumeUtils.full_filename_to_tail(pairtree_full_json_filename_bz);

				if (output_as_zip) {
					ZipEntry zipentry = new ZipEntry(json_filename_tail);
					zbros.putNextEntry(zipentry);
				}
				else {
					http_flexi_response.setContentType("application/x-bzip2");
					setHeaderDownloadFilename(http_flexi_response,json_filename_tail); 

					OutputStream ros = http_flexi_response.getOutputStream();
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

				rsyncef_file_manager_.fileClose(pairtree_full_json_filename_bz);
				
			}
		}

		if (output_as_zip) {
			download_os.close();
		}
	}
*/
	

	public void zipUpAndStreamVolumes(FlexiResponse flexi_response, String[] download_ids, String opt_cgi_key) 
			throws ServletException, IOException
	{
		boolean zip_up_interrupted = false;
		
		int download_ids_len = download_ids.length;

		OutputStream ros = flexi_response.getOutputStream();
		BufferedOutputStream bros = new BufferedOutputStream(ros);
		ZipOutputStream zbros = new ZipOutputStream(bros);
		OutputStream download_os = zbros;

		for (int i=0; i<download_ids_len; i++) {

			flexi_response.sendProgress(i,download_ids_len);

			String download_id = download_ids[i];

			// rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
			String pairtree_or_stubby_full_json_filename_bz = VolumeUtils.idToRsyncFilename(download_id);
			File file = rsyncef_file_manager_.fileOpen(pairtree_or_stubby_full_json_filename_bz);

			if (file == null) {
				if (rsyncef_file_manager_.usingRsync()) {
					flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Rsync failed");
				}
				else {
					flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "File failed");
				}
		
				if (i > 0) {
					System.err.println("Error occurred partway through zipping up JSON files.  Zipped up content will be incomplete");
					if (flexi_response.isAsync()) {
						download_os.close();
						zip_up_interrupted = true;
					}
				}

				rsyncef_file_manager_.fileClose(pairtree_or_stubby_full_json_filename_bz);
				break;
			}
			else {
				FileInputStream fis = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fis);
				String json_filename_tail = VolumeUtils.full_filename_to_tail(pairtree_or_stubby_full_json_filename_bz);

				ZipEntry zipentry = new ZipEntry(json_filename_tail);
				if (!flexi_response.isClosed()) {
					zbros.putNextEntry(zipentry);
				}
				else {
					// This could happen with a WebSocket, for example, when the user has navigated
					// away from the SolrEF result-set page	
					zip_up_interrupted = true;
				}
				
				if (!zip_up_interrupted) {
					// Fill out the zip entry
					byte[] buf = new byte[DOWNLOAD_BUFFER_SIZE];

					while (true) {
						int num_bytes = bis.read(buf);
						if (num_bytes == -1) {
							break;
						}

						if (!flexi_response.isClosed()) {
							download_os.write(buf, 0, num_bytes);
						}
						else {
							// as for zbros above
							zip_up_interrupted = true;
							break;
						}
					}
				}
				
				bis.close();	    
				
				if (!flexi_response.isClosed()) {
					zbros.closeEntry();
				}
				else {
					zip_up_interrupted = true;
				}
				
				rsyncef_file_manager_.fileClose(pairtree_or_stubby_full_json_filename_bz);
					
				if (zip_up_interrupted) {
					break;
				}
			}
		}
		
		if (!zip_up_interrupted) {
			download_os.close();
			flexi_response.sendProgress(download_ids_len,download_ids_len);
		}
	}


	public void outputZippedVolumes(FlexiResponse flexi_response, String[] download_ids, String opt_cgi_key, 
			String output_zip_filename) throws ServletException, IOException
	{
		flexi_response.setContentType("application/zip");
		setHeaderDownloadFilename(flexi_response,output_zip_filename);

		File input_zip_file = rsyncef_file_manager_.getForDownloadFile(output_zip_filename);

		if (input_zip_file.exists()) {
			if (flexi_response.isAsync()) {
				// Nothing to do => mark progress as 100% => triggers close from client
				flexi_response.sendProgress(100,100);
			}
			else {
				streamExistingVolumesFile(flexi_response, input_zip_file);
			}
		}
		else {
			zipUpAndStreamVolumes(flexi_response, download_ids, opt_cgi_key);
		}
	}

	public void doAction(Map<String,List<String>> param_map, FlexiResponse flexi_response) 
			throws ServletException, IOException
	{
		String cgi_key = getParameter(param_map,"key");
		String cgi_download_id = getParameter(param_map,"id");
		String cgi_download_ids = getParameter(param_map,"ids");
		String cgi_output = getParameter(param_map,"output");
		String cgi_output_filename_root = getParameter(param_map,"output-filename-root");

		if (cgi_output == null) {
			cgi_output = "json";
		}

		if (cgi_key != null) {
			// Retrieve IDs from MongoDB
			cgi_download_ids = expandKey(cgi_key);
		}

		String[] download_ids = null;

		if (cgi_download_ids != null) {
			download_ids = cgi_download_ids.split(",");
		}
		else {
			if (cgi_download_id != null) {
				download_ids = new String[] {cgi_download_id};
			}
		}

		if (download_ids != null) {
			String [] valid_download_ids = validityCheckIDs(flexi_response, download_ids);

			if (valid_download_ids != null) {

				if (valid_download_ids.length == download_ids.length) {
					if (cgi_output.equals("zip")) {
						String output_zip_filename;
						if (cgi_output_filename_root != null) {
							output_zip_filename = cgi_output_filename_root + ".zip";
						}
						else {
							output_zip_filename = getDownloadFilename("htrc-ef-export",cgi_key,".zip");
						}
						
						outputZippedVolumes(flexi_response,valid_download_ids,cgi_key,output_zip_filename);
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
						
						String file_ext = "."+cgi_output;
						
						String output_filename;
						if (cgi_output_filename_root != null) {
							output_filename = cgi_output_filename_root + file_ext;
						}
						else {
							output_filename = getDownloadFilename("htrc-metadata-export",cgi_key,file_ext);
						}
						outputVolumes(flexi_response,valid_download_ids,output_format,cgi_key,cgi_output,output_filename);
					}
					else {
						flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unrecognized parameter value to action '" + getHandle()
						+"' -- 'output' parameter must be 'json' or 'zip'.");	
					}
				}
				else {
					flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable to retrieve id(s) '" + getHandle()
					+"' -- either parameter 'id' does not exist or is invalid, or parameter 'ids' sepcifies one or more value that does not exist or is invalid.");

				}
			}
			else {
				if (download_ids.length == 1) {
					flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable to retrieve id '" + getHandle()
					+"' -- specified id does not exist or is invalid.");
				}
				else {
					flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable to retrieve id(s) '" + getHandle()
					+"' -- either parameter 'id' does not exist or is invalid, or all the ids specified in parameter 'ids' do not exist or are invalid.");

				}
			}
		}
		else {
			flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter to action '" + getHandle()
			+"' -- either parameter 'id' or 'ids' must be specified.");
		}
	}
}
