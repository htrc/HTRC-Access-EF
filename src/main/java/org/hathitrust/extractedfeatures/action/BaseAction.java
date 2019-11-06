package org.hathitrust.extractedfeatures.action;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.apache.commons.io.FileUtils;
import org.bson.Document;
//import org.hathitrust.extractedfeatures.VolumeUtils;
//import org.hathitrust.extractedfeatures.io.FileUtils;
//import org.hathitrust.extractedfeatures.io.JSONFileManager;
import org.hathitrust.extractedfeatures.io.FlexiResponse;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.MongoClient;  

public abstract class BaseAction
{
	enum StoreAccessOperationMode { OnlyHashmap, HashmapTransition, MongoDB, Auto };
	
	enum MongoDBState { Unconnected, FailedStartup, Connected, ForceUnused };
	
	protected static Logger logger = Logger.getLogger(BaseAction.class.getName());
	
	private final String  DefaultMongoDBHost = "localhost";
	private final Integer DefaultMongoDbPort = 27017;
	private final StoreAccessOperationMode  DefaultCheckIDMode = StoreAccessOperationMode.Auto;
		
	protected static MongoDBState mongo_state_  = MongoDBState.Unconnected;
	//protected static MongoDBState mongo_state_  = MongoDBState.ForceUnused;
	protected static MongoClient mongo_client_  = null;
	protected static MongoDatabase mongo_db_    = null;
	//protected static MongoCollection<Document> mongo_exists_col_ = null;
	
	
	public static String getParameter(Map<String,List<String>> param_map, String param)
	{
	        String last_val = null;

		List<String> param_vals = param_map.get(param);
		if (param_vals != null) {
		    int param_len = param_vals.size();
		
		    // In the event the parameter has been specified multiple times as a (CGI) param
		    // return the last occurrence

		    if (param_len>0) {
			last_val = param_vals.get(param_len-1);
		    }
		}
		
		return last_val;
	}
	
	protected static String readJSONFile(String filename) 
	{
		String json_str = null;	    
	    File file = new File(filename);
	    
		try {
			json_str = FileUtils.readFileToString(file, "utf-8");
		} catch (IOException ioe) {
			System.err.println("Failed to open file: " + filename);
			ioe.printStackTrace();
		}
	    
	   return json_str;
	}
	
	protected StoreAccessOperationMode getConfigCheckIDMode(ServletConfig config, String param_name)
	{
		StoreAccessOperationMode check_id_mode;
		
		// determine its state from the config init-param
		String check_id_mode_str = config.getInitParameter(param_name); // e.g. checkIDMode
		if ((check_id_mode_str == null) || (check_id_mode_str.equals(""))) {
			logger.info("checkIDMode defaulting to: " + DefaultCheckIDMode);
			check_id_mode_str = DefaultCheckIDMode.name();	
		}

		if (check_id_mode_str.equals("Auto")) {
			check_id_mode = StoreAccessOperationMode.Auto;
		}
		else if (check_id_mode_str.equals("OnlyHashmap")) {
			check_id_mode = StoreAccessOperationMode.OnlyHashmap;
		}
		else if (check_id_mode_str.equals("HashmapTransition")) {
			check_id_mode = StoreAccessOperationMode.HashmapTransition;
		}
		else if (check_id_mode_str.equals("MongoDB")) {
			check_id_mode = StoreAccessOperationMode.MongoDB;
		}
		else {
			logger.info("checkIDMode '" + check_id_mode_str + "' not recognized, defaulting to: " + DefaultCheckIDMode);
			check_id_mode = DefaultCheckIDMode; 	
		}
		
		return check_id_mode;
	}
	
	protected MongoCollection<Document> connectToMongoDB(ServletConfig config, 
							MongoCollection<Document> mongo_exists_col, String col_name)
	{
		if (mongo_state_ == MongoDBState.ForceUnused) {
			mongo_state_ = MongoDBState.FailedStartup;
			return null;
		}
		
		if (mongo_client_ == null) {
			
			String mongo_host = config.getInitParameter("mongodbHost");
			if ((mongo_host == null) || (mongo_host.equals(""))) {
				mongo_host = DefaultMongoDBHost; 
			}
			String mongo_port_str = config.getInitParameter("mongodbPort");
			if ((mongo_port_str == null) || (mongo_port_str.equals(""))) {
				mongo_port_str = DefaultMongoDbPort.toString(); 
			}

			int mongo_port;
			try {
				mongo_port = Integer.parseInt(mongo_port_str);
			}
			catch (Exception e) {
				e.printStackTrace();;
				mongo_port = DefaultMongoDbPort;
			}
		
			mongo_client_ = new MongoClient(mongo_host,mongo_port); 

			try {
				// Check connection opened OK
				mongo_client_.getAddress(); // throws exception if not connected
				mongo_state_ = MongoDBState.Connected;

				if (mongo_db_ == null) {
					mongo_db_     = mongo_client_.getDatabase("solrEF");
				}
				if (mongo_exists_col == null) {
					mongo_exists_col = mongo_db_.getCollection(col_name); // e.g. idExists
				}
			}
			catch (Exception e) {
				System.err.println("Unable to open connection to MongoDB on " + mongo_host + ":" + mongo_port 
						+ ".  Is it running?");
				mongo_state_ = MongoDBState.FailedStartup;
				mongo_client_.close();
				mongo_client_ = null;
			}
		}
		
		return mongo_exists_col;
	}
	
	
	public BaseAction(ServletContext context, ServletConfig config) {}
	
	public abstract String getHandle();
	public abstract String[] getDescription();
	
	public abstract void doAction(Map<String,List<String>> param_map, FlexiResponse flexi_response) 
			throws ServletException, IOException;
	
	protected String getVolumeID(String id)
	{
		String volume_id = id;
		
		Matcher seq_matcher = IdentiferRegExp.SeqPattern.matcher(volume_id);
		if (seq_matcher.matches()) {
		  volume_id = seq_matcher.group(1);
		}
		else {
			Matcher metadata_matcher = IdentiferRegExp.MetadataPattern.matcher(volume_id);
			if (metadata_matcher.matches()) {
			  volume_id = metadata_matcher.group(1);
			}
		}
		
		return volume_id;
	}
	
}
