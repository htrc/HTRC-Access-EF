package org.hathitrust.extractedfeatures.action;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import org.hathitrust.extractedfeatures.VolumeUtils;

public class VolumeCheckAction 
{
	//private static final long serialVersionUID = 1L;

	protected static int HASHMAP_INIT_SIZE = 13800000;
	protected HashMap<String, Boolean> id_check_ = null;

	public VolumeCheckAction(ServletContext servletContext ) 
	{
		//System.err.println(servletContext);
	
		String htrc_list_fname = "htrc-ef-all-files.txt";
		InputStream is = servletContext.getResourceAsStream("/WEB-INF/classes/" + htrc_list_fname);

		id_check_ = new HashMap<String, Boolean>(HASHMAP_INIT_SIZE);
		
		try {
			System.err.println("INFO: Loading in volume IDS: " + htrc_list_fname);

			InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			BufferedReader br = new BufferedReader(isr);

			storeIDs(br);
			br.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void storeIDs(BufferedReader br) {
		long line_num = 1;
		String line;
		
		//final int test_limit = 10000;
		try {

			System.err.print("Loading hashmap: ");
			while ((line = br.readLine()) != null) {

				String full_json_filename = line;
				String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);
				String id = VolumeUtils.filename_tail_to_id(json_filename_tail);

				id_check_.put(id, true);

				if ((line_num % 100000) == 0) {
					System.err.print(".");
				}
				line_num++;
				
				/*if (line_num>test_limit)
				{ 
					System.err.println("TEST MODE: Loading of IDs capped to " + test_limit);
					break;
				
				}*/
			}
			System.err.println(" => done.");
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public boolean exists(String id)
	{
		return id_check_.containsKey(id);
	}
	
	public int size() {
		return id_check_.size();
	}
	
	public boolean validityCheckIDs(HttpServletResponse response, String[] ids) throws IOException
	{
		int ids_len = ids.length;
	
		boolean check = true;
		
		for (int i=0; i<ids_len; i++) {

			String download_id = ids[i];
			boolean exists = exists(download_id);
			if (!exists) {
				// Error
				check = false;
				response.sendError(HttpServletResponse.SC_BAD_REQUEST,"The requested volume id does not exist.");
				break;	  
			}
		}
		
		return check;
	}
	
	public void outputJSON(HttpServletResponse response, String[] ids) throws IOException
	{
		response.setContentType("application/json");
		PrintWriter pw = response.getWriter();
		
		int ids_len = ids.length;

		pw.append("{");

		for (int i = 0; i < ids_len; i++) {
			String id = ids[i];

			boolean exists = exists(id);

			if (i > 0) {
				pw.append(",");
			}
			pw.append("\"" + id + "\":" + exists);
		}
		pw.append("}");
	}
}
