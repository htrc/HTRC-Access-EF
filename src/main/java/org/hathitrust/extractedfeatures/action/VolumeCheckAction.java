package org.hathitrust.extractedfeatures.action;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;

import org.bson.Document;
import org.hathitrust.extractedfeatures.VolumeUtils;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.MongoClient;  

public class VolumeCheckAction 
{
	//private static final long serialVersionUID = 1L;

	enum OperationMode { OnlyHashmap, HashmapTransition, MongoDB };
	
	//protected OperationMode mode_ = OperationMode.OnlyHashmap;
	protected OperationMode mode_ = OperationMode.MongoDB;
	
	
	protected static int HASHMAP_INIT_SIZE = 13800000;
	protected HashMap<String, Boolean> id_check_ = null;

	protected static int TEST_LIMIT = 100000;
	protected static boolean APPLY_TEST_LIMIT = true;
	
	protected MongoClient mongo_client_  = null;
	protected MongoDatabase mongo_db_    = null;
	protected MongoCollection<Document> mongo_col_ = null;
	
	public VolumeCheckAction(ServletContext servletContext ) 
	{
		if ((mode_ == OperationMode.HashmapTransition) || (mode_ == OperationMode.MongoDB)) {
			mongo_client_ = new MongoClient("localhost",27017);
			mongo_db_     = mongo_client_.getDatabase("solrEF");
			mongo_col_    = mongo_db_.getCollection("idExists");
		}
		
		if (mode_ == OperationMode.OnlyHashmap || mode_ == OperationMode.HashmapTransition) {
			id_check_ = new HashMap<String, Boolean>(HASHMAP_INIT_SIZE);
		
			String htrc_list_fname = "htrc-ef-all-files.txt";
			InputStream is = servletContext.getResourceAsStream("/WEB-INF/classes/" + htrc_list_fname);
		
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
	}

	protected void storeIDs(BufferedReader br) {
	
		try {
			long line_num = 1;
			String line;
			
			System.err.print("Loading hashmap: ");
			while ((line = br.readLine()) != null) {

				String full_json_filename = line;
				String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);
				String id = VolumeUtils.filename_tail_to_id(json_filename_tail);

				id_check_.put(id, true);

				if (mode_ == OperationMode.HashmapTransition) {
					Document doc = new Document("_id", id);
					mongo_col_.insertOne(doc);
				}
				
				if ((line_num % 100000) == 0) {
					System.err.print(".");
				}
				line_num++;
				
				if ((APPLY_TEST_LIMIT) && (line_num > TEST_LIMIT))
				{ 
					System.err.println("TEST MODE: Loading of IDs capped to " + TEST_LIMIT);
					break;
				}
			}
			System.err.println(" => done.");
		} 
		catch (Exception e) {
			e.printStackTrace();
		}

	}

	public boolean exists(String id)
	{
		if (mode_ == OperationMode.MongoDB){
			MongoCursor<Document> cursor = mongo_col_.find(Filters.eq("_id",id)).iterator();
			return cursor.hasNext();
		}
		else {
			return id_check_.containsKey(id);
		}
	}
	
	public int size() {
		if (mode_ == OperationMode.MongoDB){
			long col_count = mongo_col_.count();
			return (int)col_count;
		}
		else {
			return id_check_.size();
		}
	}
	
	public boolean validityCheckID(HttpServletResponse response, String id) throws IOException
	{
	
		boolean exists = exists(id);
		if (!exists) {
			// Error
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,"The requested volume id '" + id + "' does not exist.");
		}
		
		return exists;
	}
	public boolean validityCheckIDs(HttpServletResponse response, String[] ids) throws IOException
	{
		int ids_len = ids.length;
	
		boolean check = true;
		/*
		MongoCursor<Document> cursor = collection.find().iterator();
		try {
		    while (cursor.hasNext()) {
		        System.out.println(cursor.next().toJson());
		    }
		} finally {
		    cursor.close();
		}
		*/
		
		for (int i=0; i<ids_len; i++) {

			String id = ids[i];
			if (!validityCheckID(response,id)) {
				check = false;
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
