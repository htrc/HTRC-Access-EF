package org.hathitrust.extractedfeatures.action;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletResponse;

public class CollectionToWorksetAction 
{
	protected static final String ht_col_url = "https://babel.hathitrust.org/cgi/mb";
	
	VolumeCheckAction vol_check_;
	
	public CollectionToWorksetAction(VolumeCheckAction vol_check)
	{
		vol_check_ = vol_check;
	}
	
	public void outputWorkset(HttpServletResponse response, String cgi_convert_col, String cgi_col_title,
				String cgi_a, String cgi_format) throws IOException
	{
		if ((cgi_a == null) || (cgi_format == null)) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,
					"Malformed arguments.  Need 'a', and 'format'");
		} else {
			
			doCollectionToWorkset(response, cgi_col_title, cgi_convert_col, cgi_a, cgi_format);
		}
		
	}
	
	protected void doCollectionToWorkset(HttpServletResponse response, String col_title,
			String c, String a, String format) throws IOException 
	{
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
				pw.append("## The following volumes were listed in the HT collection, but have been omitted "
						+ "as they are not in the HTRC Extracted Feature dataset\n");
				pw.append(workset_unfriendly_sb.toString());
			}
			pw.flush();
		} catch (Exception e) {
			e.printStackTrace();
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,
					"Failed to convert HT collection to HTRC workset");
		}
	}
}
