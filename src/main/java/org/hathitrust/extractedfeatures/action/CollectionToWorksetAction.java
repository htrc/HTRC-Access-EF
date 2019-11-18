package org.hathitrust.extractedfeatures.action;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.hathitrust.extractedfeatures.io.FlexiResponse;

public class CollectionToWorksetAction extends IdMongoDBAction
{
	protected static final String ht_col_url = "https://babel.hathitrust.org/cgi/mb";
	
	public String getHandle() 
	{
		return "convert-col";
	}
	public String[] getDescription() 
	{
		String[] mess = { 
				"Convert a HathiTrust collection to an HTRC workset",
				"Required parameter: 'collection'",
				"Returns:            a tab-separated text file, each line containing id and metadata for those\n"
				+ "                        collection items also aviailable as an HTRC Extracted Feature file."
		};
		
		return mess;
	}
	
	public CollectionToWorksetAction(ServletContext context, ServletConfig config) 
	{
		super(context,config);
	}
	
	public void outputWorkset(FlexiResponse flexi_response, String cgi_convert_col, String cgi_col_title,
				String cgi_a, String cgi_format) throws IOException
	{
		if ((cgi_a == null) || (cgi_format == null)) {
			flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST,
					"Malformed arguments.  Need 'a', and 'format'");
		} else {
			
			doCollectionToWorkset(flexi_response, cgi_col_title, cgi_convert_col, cgi_a, cgi_format);
		}
		
	}
	
	protected void doCollectionToWorkset(FlexiResponse flexi_response, String col_title,
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

					if (exists(id)) {
						workset_friendly_sb.append(line + "\n");
					} else {
						workset_unfriendly_sb.append("#" + line + "\n");
					}
				}

				ci++;
			}

			String col_title_filename = col_title + ".txt";
			flexi_response.setContentType("text/plain");
			flexi_response.setContentDispositionAttachment(col_title_filename);

			flexi_response.append(workset_friendly_sb.toString());

			if (workset_unfriendly_sb.length() > 0) {
				flexi_response.append("## The following volumes were listed in the HT collection, but have been omitted "
						+ "as they are not in the HTRC Extracted Feature dataset\n");
				flexi_response.append(workset_unfriendly_sb.toString());
			}
			
			flexi_response.flush();
		} 
		catch (Exception e) {
			e.printStackTrace();
			flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST,
					"Failed to convert HT collection to HTRC workset");
		}
	}
	
	public void doAction(Map<String,List<String>> param_map, FlexiResponse flexi_response) 
			throws ServletException, IOException
	{
		String cgi_convert_col = getParameter(param_map,"convert-col");
		if (cgi_convert_col != null) {
			String cgi_col_title = getParameter(param_map,"col-title");
			if (cgi_col_title == null) {
				cgi_col_title = "htrc-workset-" + cgi_convert_col;
			}
			String cgi_a = getParameter(param_map,"a");
			String cgi_format = getParameter(param_map,"format");
			
			outputWorkset(flexi_response, cgi_convert_col, cgi_col_title, cgi_a, cgi_format);
				
		}
		else {
			flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter to action '" + getHandle()
			+"' -- parameter 'collection' must be specified.");	
		}		
	}
	
	
}
