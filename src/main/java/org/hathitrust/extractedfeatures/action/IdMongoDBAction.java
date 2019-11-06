package org.hathitrust.extractedfeatures.action;

import static com.mongodb.client.model.Filters.eq;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bson.Document;
import org.hathitrust.extractedfeatures.VolumeUtils;
import org.hathitrust.extractedfeatures.action.BaseAction.StoreAccessOperationMode;
import org.hathitrust.extractedfeatures.io.FileUtils;
import org.hathitrust.extractedfeatures.io.JSONFileManager;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.MongoClient;  

public abstract class IdMongoDBAction extends BaseAction
{
	protected static Logger logger = Logger.getLogger(IdMongoDBAction.class.getName());
	
	private final String IdListTextResource = "/WEB-INF/classes/htrc-ef-all-files.txt";
	
	//protected static OperationMode CheckIDMode_ = OperationMode.OnlyHashmap;
	//protected static OperationMode CheckIDMode_ = OperationMode.HashmapTransition;
	//protected static CheckIDOperationMode CheckIDMode_ = CheckIDOperationMode.MongoDB;
	protected static StoreAccessOperationMode CheckIdMode_ = null;
	
	protected static int HASHMAP_INIT_SIZE = 16000000;
	protected static HashMap<String, Boolean> id_check_ = null;

	protected static int TEST_LIMIT = 100000;
	//protected static boolean APPLY_TEST_LIMIT = true;
	protected static boolean APPLY_TEST_LIMIT = false;
	
	protected static MongoCollection<Document> mongo_exists_col_ = null;
	
	protected StoreAccessOperationMode checkMongoDBUpToDate(ServletContext servletContext)
	{
		// If text file present => check how many lines it has 
		// If different to number of records in MongoDB, trigger Transition, otherwise become MongoDB mode 
		
		StoreAccessOperationMode resulting_mode = StoreAccessOperationMode.MongoDB;;
				
		InputStream is = servletContext.getResourceAsStream(IdListTextResource);
		BufferedInputStream bis = new BufferedInputStream(is);
		
		try {
			long num_txt_lines = FileUtils.countLines(bis);
			long num_mongo_exists_lines = mongo_exists_col_.count();

			if (num_txt_lines > num_mongo_exists_lines) {
				// => trigger transition
				resulting_mode = StoreAccessOperationMode.HashmapTransition;
			}
			else  if (num_txt_lines < num_mongo_exists_lines) {
				logger.warning("The ID List Text Resource '"+IdListTextResource+"' has fewer entries than in the MongoDB");
			}
		}
		catch (IOException ioe) {
			// No text file to base comparison, so soldier on in MongoDB mode
			logger.info("Web resource text file '" + IdListTextResource + "' not present. Assuming MongoDB is up to date");
			CheckIdMode_ = StoreAccessOperationMode.MongoDB;
		}
		
		return resulting_mode;
	}
	
	protected void processIDListResource(ServletContext context)
	{
		if (id_check_ == null) {
			id_check_ = new HashMap<String, Boolean>(HASHMAP_INIT_SIZE);

			InputStream is = context.getResourceAsStream(IdListTextResource);

			try {
				System.err.println("INFO: Loading in web resource volume IDS: " + IdListTextResource);

				InputStreamReader isr = new InputStreamReader(is, "UTF-8");
				BufferedReader br = new BufferedReader(isr);

				storeIDs(br);
				br.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public IdMongoDBAction(ServletContext context, ServletConfig config)
	{
		super(context,config);
		
		if (CheckIdMode_ == null) {
			CheckIdMode_ = getConfigCheckIDMode(config,"checkIDMode");
		}
		
		// Need a mongoDB connection regardless of 'mode' we are in, as various actions rely on it
		if (mongo_state_ != MongoDBState.FailedStartup) {
			mongo_exists_col_ = connectToMongoDB(config,mongo_exists_col_,"idExists");
			
			if (mongo_exists_col_ == null) {
				// Failed to connect file collection for some reason
				// => If Auto mode, then fall back to reading in the txt file to generate the Hashmap
				if (CheckIdMode_ == StoreAccessOperationMode.Auto) {
					// Switch mode to using OnlyHashmap and work off the text-file list of IDs
					System.err.println("Switching to OnlyHashmap mode for Ids.");
					CheckIdMode_ = StoreAccessOperationMode.OnlyHashmap; 
				}
			}
		}
		
		if ((mongo_state_ != MongoDBState.FailedStartup) && (CheckIdMode_ == StoreAccessOperationMode.Auto)) {
			CheckIdMode_ = checkMongoDBUpToDate(context);
		}
		// Past this point, if CheckIDMode_ was initially 'Auto' it will now be fixed to one of the other modes
		
		if ((CheckIdMode_ == StoreAccessOperationMode.OnlyHashmap) || (CheckIdMode_ == StoreAccessOperationMode.HashmapTransition)) {
			processIDListResource(context);
		}
	}

	public  boolean isOperational()
	{
		if (CheckIdMode_ == StoreAccessOperationMode.MongoDB) {
			return mongo_client_ != null;
		}
		else {
			return id_check_ != null;
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

				if (CheckIdMode_ == StoreAccessOperationMode.HashmapTransition) {
					
					if (mongo_state_ == MongoDBState.Connected) {
						Document doc = new Document("_id", id);
						
						Document prev_doc = mongo_exists_col_.find(eq("_id", id)).first();
						if (prev_doc == null) {
							mongo_exists_col_.insertOne(doc);
						}
					}
				}
				
				if ((line_num % 100000) == 0) {
					System.err.print(".");
				}
				if ((line_num % (40*100000)) == 0) {
					System.err.println(" [Processed " + line_num + " entries]");
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
		if (CheckIdMode_ == StoreAccessOperationMode.MongoDB){
			MongoCursor<Document> cursor = mongo_exists_col_.find(Filters.eq("_id",id)).iterator();
			return cursor.hasNext();
		}
		else {
			return id_check_.containsKey(id);
		}
	}
	
	public int size() {
		if (CheckIdMode_ == StoreAccessOperationMode.MongoDB) {
			
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
	
	
	public boolean validityCheckIDOptimistic(HttpServletResponse response, String id) throws IOException
	{
		return true;
	}
	
	public boolean validityCheckID(HttpServletResponse response, String id) throws IOException
	{
		String volume_id = getVolumeID(id);
		
		boolean exists = exists(volume_id);
		
		if (!exists && volume_id.matches(".*[,+=].*")) {
		    // See if there are any 'special/reserved' chars in the ID

		    volume_id = volume_id.replaceAll(",", ".").replaceAll("\\+", ":").replaceAll("=", "/");
		    exists = exists(volume_id);

		    //		    if (!exists) {
		    //			// a '+' might have ending up as a ' '
		    //			volume_id = volume_id.replaceAll(" ", ":");
		    //			exists = exists(volume_id);
		    //		    }
		}
		
		if (!exists) {
			// Error
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,"The requested volume id '" + volume_id + "' does not exist.");
		}
		
		return exists;
	}
	
	public boolean validityCheckIDsOptimistic(HttpServletResponse response, String[] ids) throws IOException
	{
		return true;	
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
