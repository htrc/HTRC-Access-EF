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
public class JSONFileManager
{
	private static final long serialVersionUID = 1L;
	
	protected static final String rsync_base = "data.analytics.hathitrust.org::features/";
	
	protected File tmp_dir_;
	protected File local_pairtree_root_;

	protected final int DOWNLOAD_BUFFER_SIZE = 1024;

	public JSONFileManager(ServletConfig config) throws ServletException
	{	
		try {
			tmp_dir_ = Files.createTempDirectory("rsync").toFile();
		} catch (IOException e) {
			throw new ServletException("Could not create temp folder", e);
		}

		String ptRoot = config.getInitParameter("pairtreeRoot");
		if (ptRoot != null) {
			local_pairtree_root_ = new File(ptRoot);
			if (!local_pairtree_root_.exists()) {
				throw new ServletException(local_pairtree_root_ + " does not exist!");
			}
		}
	}

	public File doRsyncDownload(String full_json_filename) throws IOException 
	{
		String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);

		Runtime runtime = Runtime.getRuntime();
		String[] rsync_command = {"rsync", "-av", rsync_base + full_json_filename, tmp_dir_.getPath()};

		try {
			Process proc = runtime.exec(rsync_command);
			int retCode = proc.waitFor();

			if (retCode != 0) {
				throw new Exception("rsync command failed with code " + retCode);
			}

			return new File(tmp_dir_, json_filename_tail);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}


	public File fileOpen(String full_json_filename)
	{
		File file = null;
		if (local_pairtree_root_ != null) {
			// Access the file locally
			file = new File(local_pairtree_root_, full_json_filename);
		} else {
			// Work through the rsync server
			try {
				file = doRsyncDownload(full_json_filename);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return file;
	}

	public boolean usingRsync()
	{
		return local_pairtree_root_ == null;
	}

	public void outputVolumes(HttpServletResponse response, String[] download_ids) 
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
			File file = fileOpen(full_json_filename);

			if (file == null) {
				if (usingRsync()) {
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

				if (usingRsync()) {
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
