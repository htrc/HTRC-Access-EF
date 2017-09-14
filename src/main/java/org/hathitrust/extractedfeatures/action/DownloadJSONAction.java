package org.hathitrust.extractedfeatures.action;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.jcs.JCS;
import org.apache.commons.jcs.access.exception.CacheException;
import org.hathitrust.extractedfeatures.VolumeUtils;
import org.hathitrust.extractedfeatures.io.JSONFileManager;
import org.apache.commons.jcs.access.CacheAccess;


/**
 * Servlet implementation class VolumeCheck
 */
public class DownloadJSONAction
{
	//private static final long serialVersionUID = 1L;
	
	protected final int DOWNLOAD_BUFFER_SIZE = 1024;

	protected JSONFileManager json_file_manager_;
	
	public DownloadJSONAction(ServletConfig config)
	{	
		json_file_manager_ = JSONFileManager.getInstance(config);
	}

	public void outputVolume(HttpServletResponse response, String download_id) 
			throws ServletException, IOException
	{
		OutputStream download_os = null;

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
		}
		else {
			String json_content = json_file_manager_.readCompressedTextFile(file);
			
			//String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);
			response.setContentType("application/json");
			//response.setHeader("Content-Disposition","attachment; filename=\"" + json_filename_tail + "\"");
			
			PrintWriter pw = response.getWriter();
			pw.append(json_content);
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
				file.delete();
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
					file.delete();
				}
			}
		}

		if (output_as_zip) {
			download_os.close();
		}
	}
}
