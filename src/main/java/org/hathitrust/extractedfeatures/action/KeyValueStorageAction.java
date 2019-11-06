package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.hathitrust.extractedfeatures.io.FlexiResponse;
import static com.mongodb.client.model.Filters.*;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Indexes;

// The following class based loosely on details at:
//   https://gist.github.com/rakeshsingh/64918583972dd5a08012

public class KeyValueStorageAction extends IdMongoDBAction
{
	// In-memory storage by key
	protected HashMap<String, String> key_map_;   // key-value map
	
	protected static MongoCollection<Document> mongo_key_and_val_col_ = null;
	
	public String getHandle() 
	{
		return "key-value-storage";
	}
	
	public String[] getDescription() 
	{
		String[] mess = 
			{ "Store and retrieve a value by key",
					"Required parameter: 'key'",
					"    If provided 'value' then the action stores the 'value' under the given key\n"
				   +"    If no value is provided (i.e. just a key) then action returns the stored value"
			};
		
		return mess;
	}
	
	public KeyValueStorageAction(ServletContext context, ServletConfig config) 
	{
		super(context,config);
	
		key_map_ = new HashMap<String, String>();
		
		if (mongo_key_and_val_col_ == null) {
			if (mongo_state_ == MongoDBState.Connected) {
				// The following will create the collection if it didn't already exist
				mongo_key_and_val_col_ = mongo_db_.getCollection("storageKeyValue");
				mongo_key_and_val_col_.createIndex(Indexes.ascending("key"));
			}
		}
	}

	public  boolean isOperational()
	{
		return mongo_key_and_val_col_ != null;
	}
	
	
	
	protected void setValue(String key, String value) 
	{
		key_map_.put(key, value);
				
		Bson id_bson = eq("_id", new ObjectId(key));
		Document doc = new Document("_id", key);
		doc.put("value",value);

		if (mongo_key_and_val_col_.find(eq("_id", key)).first() == null) {
			// first time
			mongo_key_and_val_col_.insertOne(doc);
		}
		else {

			//mongo_key_and_val_col_.updateOne(id_bson, set("value",value));
			mongo_key_and_val_col_.replaceOne(id_bson, doc);
		}
	}
	
	protected String getValue(String key) 
	{
		String value = key_map_.get(key);
		
		if (value == null) {
			
			Document doc = mongo_key_and_val_col_.find(eq("_id", key)).first();

			if (doc != null) {
				// Found it in mongoDB
				value = doc.getString("value");
				// Re-populate the hashmaps
				key_map_.put(key, value);
			}
		}
		
		return value;
	}
	
	public void doAction(Map<String,List<String>> param_map, FlexiResponse flexi_response) 
			throws ServletException, IOException
	{
		flexi_response.setContentType("text/plain");
		flexi_response.setCharacterEncoding("UTF-8");
		
		String value = getParameter(param_map,"value");
		String key   = getParameter(param_map,"key");
		
		if ((key != null) && (value != null)) {
			value = URLDecoder.decode(value,"UTF-8");
			
			setValue(key,value);
			
			flexi_response.append("Stored: "+ key + "='" + value+"'");
		}
		else if (key != null) {
			value = getValue(key);
		
			flexi_response.append(value);

		}
		else {
			
			flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'key' (and potentially 'value') parameter to " + getHandle());
		
		}
	}
}

