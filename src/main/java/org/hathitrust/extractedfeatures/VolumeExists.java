package org.hathitrust.extractedfeatures;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;



public class VolumeExists 
{
	private static final long serialVersionUID = 1L;

	protected static int HASHMAP_INIT_SIZE = 13800000;
	protected HashMap<String, Boolean> id_check_ = null;

	protected final int BUFFER_SIZE = 1024;

	public VolumeExists(String htrc_list_filename, InputStream is) 
	{
		id_check_ = new HashMap<String, Boolean>(HASHMAP_INIT_SIZE);

		try {
			System.err.println("INFO: Loading in volume IDS: " + htrc_list_filename);

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
}
