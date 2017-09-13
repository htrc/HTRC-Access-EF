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

	protected static VolumeExists vol_exists_ = null;

	protected static File tmpDir;
	protected static File localPairtreeRoot;

	protected final int BUFFER_SIZE = 1024;

	public AccessServlet() {
	}


	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		if (vol_exists_ == null) {
			String htrc_list_file = "htrc-ef-all-files.txt";
			ServletContext servletContext = getServletContext();
			System.err.println(servletContext);
			InputStream is = servletContext.getResourceAsStream("/WEB-INF/classes/" + htrc_list_file);

			vol_exists_ = new VolumeExists(htrc_list_file, is);
		}

		if (tmpDir == null) {
			try {
				tmpDir = Files.createTempDirectory("rsync").toFile();
			} catch (IOException e) {
				throw new ServletException("Could not create temp folder", e);
			}

		}

		String ptRoot = config.getInitParameter("pairtreeRoot");
		if (ptRoot != null) {
			localPairtreeRoot = new File(ptRoot);
			if (!localPairtreeRoot.exists()) {
				throw new ServletException(localPairtreeRoot + " does not exist!");
			}
		}
	}

	protected File doRsyncDownload(String full_json_filename) throws IOException {
		String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);

		Runtime runtime = Runtime.getRuntime();
		String[] rsync_command = {"rsync", "-av",
				"data.analytics.hathitrust.org::features/" + full_json_filename, tmpDir.getPath()};

		try {
			Process proc = runtime.exec(rsync_command);
			int retCode = proc.waitFor();

			if (retCode != 0) {
				throw new Exception("rsync command failed with code " + retCode);
			}

			return new File(tmpDir, json_filename_tail);
		} catch (Exception e) {
			throw new IOException(e);
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

					if (vol_exists_.exists(id)) {
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

	protected File json_pairtree_as_local_file(String full_json_filename)
	{
		File file = null;
		if (localPairtreeRoot != null) {
			// Access the file locally
			file = new File(localPairtreeRoot, full_json_filename);
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
			response.setContentType("application/json");
			PrintWriter pw = response.getWriter();

			String[] ids = cgi_ids.split(",");
			int ids_len = ids.length;

			pw.append("{");

			for (int i = 0; i < ids_len; i++) {
				String id = ids[i];

				boolean exists = vol_exists_.exists(id);

				if (i > 0) {
					pw.append(",");
				}
				pw.append("\"" + id + "\":" + exists);
			}
			pw.append("}");

		}
		else if (cgi_download_ids != null) {

			String[] download_ids = cgi_download_ids.split(",");
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
				boolean exists = vol_exists_.exists(download_id);
				if (!exists) {
					// Error
					response.sendError(HttpServletResponse.SC_BAD_REQUEST,
							"The requested volume id does not exist.");
					break;	  
				}
				else {

					// rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
					String full_json_filename = VolumeUtils.id_to_pairtree_filename(download_id);
					File file = json_pairtree_as_local_file(full_json_filename);

					if (file == null) {
						response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Rsync failed");
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


						byte[] buf = new byte[1024];

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

						if (localPairtreeRoot == null) {
							// remove file retrieved over rsync
							file.delete();
						}
					}
				}
			}

			if (output_as_zip) {
				download_os.close();
			}

		} else if (cgi_convert_col != null) {

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

		} else {
			PrintWriter pw = response.getWriter();

			pw.append("General Info: Number of HTRC Volumes in check-list = " + vol_exists_.size());

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
