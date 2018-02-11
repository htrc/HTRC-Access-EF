package org.hathitrust.extractedfeatures.action;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bson.Document;
import org.hathitrust.extractedfeatures.VolumeUtils;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.MongoClient;  

public abstract class BaseAction
{
	//private static final long serialVersionUID = 1L;

	enum CheckIDOperationMode { OnlyHashmap, HashmapTransition, MongoDB };
	
	enum MongoDBState { Unconnected, FailedStartup, Connected };
	
	//protected static OperationMode CheckIDMode_ = OperationMode.OnlyHashmap;
	//protected static OperationMode CheckIDMode_ = OperationMode.HashmapTransition;
	protected static CheckIDOperationMode CheckIDMode_ = CheckIDOperationMode.MongoDB;
	
	protected static int HASHMAP_INIT_SIZE = 16000000;
	protected static HashMap<String, Boolean> id_check_ = null;

	protected static int TEST_LIMIT = 100000;
	//protected static boolean APPLY_TEST_LIMIT = true;
	protected static boolean APPLY_TEST_LIMIT = false;
	
	protected static MongoDBState mongo_state_  = MongoDBState.Unconnected;
	protected static MongoClient mongo_client_  = null;
	protected static MongoDatabase mongo_db_    = null;
	protected static MongoCollection<Document> mongo_exists_col_ = null;
	
	//Pattern static page_patt_ = Pattern.compile("^(.*)\\.page-(\\d+)$");
	protected static Pattern seq_patt_ = Pattern.compile("^(.*)-seq-(\\d+)$");	
	protected static Pattern metadata_patt_ = Pattern.compile("^(.*)-metadata$");	
	
	public BaseAction(ServletContext servletContext ) 
	{
		
		// Set up mongoDB connection regardless of 'mode' we are in as other actions reply on it
		if (mongo_state_ != MongoDBState.FailedStartup) {

			if (mongo_client_ == null) {
				mongo_client_ = new MongoClient("localhost",27017);
			}

			try {
				// Check connection opened OK
				mongo_client_.getAddress(); // throws exception is not connected
				mongo_state_ = MongoDBState.Connected;

				if (mongo_db_ == null) {
					mongo_db_     = mongo_client_.getDatabase("solrEF");
				}
				if (mongo_exists_col_ == null) {
					mongo_exists_col_    = mongo_db_.getCollection("idExists");
				}
			}
			catch (Exception e) {
				System.err.println("Unable to open connection to MongoDB on port 27017.  Is it running?");
				mongo_state_ = MongoDBState.FailedStartup;
				mongo_client_.close();
				mongo_client_ = null;
			}
		}
		
		if (CheckIDMode_ == CheckIDOperationMode.OnlyHashmap || CheckIDMode_ == CheckIDOperationMode.HashmapTransition) {
			if (id_check_ == null) {
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
	}

	public abstract String getHandle();
	public abstract String[] getDescription();
	
	public abstract void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException;
	
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

				if (CheckIDMode_ == CheckIDOperationMode.HashmapTransition) {
					Document doc = new Document("_id", id);
					if (mongo_state_ == MongoDBState.Connected) {
						mongo_exists_col_.insertOne(doc);
					}
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
		//return true; // ****
	
		if (CheckIDMode_ == CheckIDOperationMode.MongoDB){
			MongoCursor<Document> cursor = mongo_exists_col_.find(Filters.eq("_id",id)).iterator();
			return cursor.hasNext();
		}
		else {
			return id_check_.containsKey(id);
		}
		
	}
	
	public int size() {
		if (CheckIDMode_ == CheckIDOperationMode.MongoDB) {
			
			long col_count = 0;
			if (mongo_state_ == MongoDBState.Connected) {
				col_count = mongo_exists_col_.count();
			}
			
			return (int)col_count;
		}
		else {
			return id_check_.size();
		}
	}
	
	public String getVolumeID(String id)
	{
		String volume_id = id;
		
		Matcher seq_matcher = seq_patt_.matcher(volume_id);
		if (seq_matcher.matches()) {
		  volume_id = seq_matcher.group(1);
		}
		else {
			Matcher metadata_matcher = metadata_patt_.matcher(volume_id);
			if (metadata_matcher.matches()) {
			  volume_id = metadata_matcher.group(1);
			}
		}
		
		return volume_id;
	}
	
	public boolean validityCheckID(HttpServletResponse response, String id) throws IOException
	{
		String volume_id = getVolumeID(id);
		
		boolean exists = exists(volume_id);
		if (!exists) {
			// Error
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,"The requested volume id '" + volume_id + "' does not exist.");
		}
		
		return exists;
	}
	
	public boolean validityCheckIDs(HttpServletResponse response, String[] ids) throws IOException
	{
		int ids_len = ids.length;
	
		boolean check = true;
		
		// If backed by MongoDB, the following (with appropriate find expression)
		// might provide more efficient way to check
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
	
}