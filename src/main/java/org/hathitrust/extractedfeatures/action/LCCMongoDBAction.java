package org.hathitrust.extractedfeatures.action;

import static com.mongodb.client.model.Filters.eq;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bson.Document;
//import org.hathitrust.extractedfeatures.VolumeUtils;
//import org.hathitrust.extractedfeatures.VolumeUtils;
//import org.hathitrust.extractedfeatures.action.BaseAction.StoreAccessOperationMode;
//import org.hathitrust.extractedfeatures.io.FileUtils;
//import org.hathitrust.extractedfeatures.io.JSONFileManager;
import org.hathitrust.extractedfeatures.lcc.LCCOutlineHashRec;
import org.hathitrust.extractedfeatures.lcc.LCCOutlinePrefixRootToplevel;
import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.MongoClient;  


public abstract class LCCMongoDBAction extends BaseAction
{
	protected static Logger logger = Logger.getLogger(LCCMongoDBAction.class.getName());
	
	private final String LCCTreeMapJSONResource = "/WEB-INF/classes/lcc-outline-treemap.json";
	
	//protected static OperationMode CheckIDMode_ = OperationMode.OnlyHashmap;
	//protected static OperationMode CheckIDMode_ = OperationMode.HashmapTransition;
	//protected static CheckIDOperationMode CheckIDMode_ = CheckIDOperationMode.MongoDB;
	protected static StoreAccessOperationMode LCCLookupMode_ = null;
	
	//protected static int HASHMAP_INIT_SIZE = 16000000; // ****
	protected static HashMap<String, LCCOutlineHashRec> lcc_hashmap_lookup_ = null;
	protected static HashMap<String,LCCOutlinePrefixRootToplevel> lcc_prefix_root_toplevel_ = null;
	
	//protected static int TEST_LIMIT = 100000;
	//protected static boolean APPLY_TEST_LIMIT = true;
	//protected static boolean APPLY_TEST_LIMIT = false;
	
	protected static MongoCollection<Document> mongo_lcc_col_ = null;
	
	protected StoreAccessOperationMode checkMongoDBUpToDate(ServletContext servletContext)
	{
		System.err.println("***** checkMongoDBUpToDate() placeholder called.  Returning null");
		/*
		// If text file present => check how many lines it has 
		// If different to number of records in MongoDB, trigger Transition, otherwise become MongoDB mode 
		
		CheckIdOperationMode resulting_mode = CheckIdOperationMode.MongoDB;;
				
		InputStream is = servletContext.getResourceAsStream(IdListTextResource);
		BufferedInputStream bis = new BufferedInputStream(is);
		
		try {
			long num_txt_lines = FileUtils.countLines(bis);
			long num_mongo_exists_lines = mongo_exists_col_.count();

			if (num_txt_lines > num_mongo_exists_lines) {
				// => trigger transition
				resulting_mode = CheckIdOperationMode.HashmapTransition;
			}
			else  if (num_txt_lines < num_mongo_exists_lines) {
				logger.warning("The ID List Text Resource '"+IdListTextResource+"' has fewer entries than in the MongoDB");
			}
		}
		catch (IOException ioe) {
			// No text file to base comparison, so soldier on in MongoDB mode
			logger.info("Web resource text file '" + IdListTextResource + "' not present. Assuming MongoDB is up to date");
			CheckIdMode_ = CheckIdOperationMode.MongoDB;
		}
		
		return resulting_mode;
		*/
		return null;
	}
	
	protected void storeLCCHashRec(LCCOutlineHashRec lcc_hash_rec)
	{
		final String curr_key = lcc_hash_rec.id;
		int parents_len = lcc_hash_rec.parents.size();
		
		LCCOutlineHashRec curr_hash_rec = null;
		
		if (!lcc_hashmap_lookup_.containsKey(curr_key)) {
			// Brand new entry
			lcc_hashmap_lookup_.put(curr_key, lcc_hash_rec);
			curr_hash_rec = lcc_hash_rec;
		}
		else {
			// Looking to add the main entry to a location in the hashmap where some back-ref parent info already exists
			// => merge with existing info
			LCCOutlineHashRec existing_hash_rec = lcc_hashmap_lookup_.get(curr_key);
			LCCOutlineHashRec.merge(existing_hash_rec, lcc_hash_rec);

			curr_hash_rec = existing_hash_rec;
		}

		// Now follow the parent information, and make sure 'forward direction' child links are formed

		if (parents_len>0) {
			String parent_key = lcc_hash_rec.parents.get(parents_len-1);
			
			LCCOutlineHashRec parent_rec = lcc_hashmap_lookup_.get(parent_key);

			if (parent_rec == null) {
				// The parent's full entry has not been processed yet,
				// => create a stub
				parent_rec = new LCCOutlineHashRec(parent_key,curr_hash_rec);
				lcc_hashmap_lookup_.put(parent_key,parent_rec);
			}
			else {
				LCCOutlineHashRec.connect(parent_rec,curr_hash_rec);
			}
		}
				
		if (parents_len == 0) {
			// Top-level entry for the given prefix
			String curr_prefix = curr_hash_rec.prefix;
			
			LCCOutlinePrefixRootToplevel prefix_root_entry = lcc_prefix_root_toplevel_.get(curr_prefix);
			prefix_root_entry.addTopLevelRecEntry(curr_hash_rec);
		}
		
	}

	protected int countHashRecTraverse(LCCOutlineHashRec hash_rec) 
	{
		if (hash_rec.child_ids==null) { //  || (hash_rec.child_ids.size() == 0)) { // ****
			// Hit a leaf node
			return 1;
		}
	
		int child_count = 0;
		Collection<LCCOutlineHashRec> child_hash_recs = hash_rec.child_ids.values();
		Iterator<LCCOutlineHashRec> child_hash_rec_iterator = child_hash_recs.iterator();
		
		while (child_hash_rec_iterator.hasNext()) {
			LCCOutlineHashRec child_hash_rec = child_hash_rec_iterator.next();
			
			child_count += countHashRecTraverse(child_hash_rec);
		}
		
		return 1+child_count; //  this node + child_count 
	}
	
	protected int countPrefixRootToplevelTraverse(LCCOutlinePrefixRootToplevel prefix_root_toplevel) 
	{
		int count = 0;
		
		Collection<LCCOutlineHashRec> toplevel_hash_recs = prefix_root_toplevel.getTopLevelRecEntries();
		Iterator<LCCOutlineHashRec> toplevel_hash_rec_iterator = toplevel_hash_recs.iterator();
		while (toplevel_hash_rec_iterator.hasNext()) {
			LCCOutlineHashRec curr_toplevel_hash_rec = toplevel_hash_rec_iterator.next();
			int curr_toplevel_count = countHashRecTraverse(curr_toplevel_hash_rec);
			
			//System.err.println("  Count for Toplevel key/id' " + curr_toplevel_hash_rec.id + " = " + curr_toplevel_count);
			count += curr_toplevel_count;
		}
		
		return count;
	}
	

	protected void processTreemapResource(ServletContext context)
	{
		//String json_str = readJSONFile(LCCTreeMapJSONResource); // ****
		InputStream is = context.getResourceAsStream(LCCTreeMapJSONResource);

		StringBuilder json_sb = new StringBuilder();
		
		try {
			System.err.println("INFO: Loading in web resource LCC Outline Treemap: " + LCCTreeMapJSONResource);

			InputStreamReader isr = new InputStreamReader(is, "UTF-8");
			BufferedReader br = new BufferedReader(isr);

			String line;
			while ((line = br.readLine()) != null) {
				json_sb.append(line);	
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
			
		JSONObject json_treemap = new JSONObject(json_sb.toString());    
	    
		lcc_hashmap_lookup_ = new HashMap<String, LCCOutlineHashRec>(json_treemap.length()*2);
		lcc_prefix_root_toplevel_ = new HashMap<String, LCCOutlinePrefixRootToplevel>();
		
		Iterator<String> treemap_keys = json_treemap.keys();
		
		int json_prefix_count = 0;
		
		while (treemap_keys.hasNext()) {
			String prefix_key = treemap_keys.next();
			
			LCCOutlinePrefixRootToplevel prefix_root_range_stub = new  LCCOutlinePrefixRootToplevel(prefix_key);
			lcc_prefix_root_toplevel_.put(prefix_key, prefix_root_range_stub);
			
			JSONArray prefix_key_entries = json_treemap.getJSONArray(prefix_key);
			for (int i=0; i<prefix_key_entries.length(); i++) {
				JSONObject lcc_entry_rec_json = prefix_key_entries.getJSONObject(i);
				
				LCCOutlineHashRec lcc_hash_rec = new LCCOutlineHashRec(lcc_entry_rec_json);
				storeLCCHashRec(lcc_hash_rec);
				
			}
			
			json_prefix_count++;
		}
		
		// Run check to ensure that the number of recs accessible through lcc_prefix_root_range
		// is the same as the number of keys in lcc_hashmap_lookup
		
		int lcc_hashmap_lookup_len = lcc_hashmap_lookup_.size();
		
		Collection<LCCOutlinePrefixRootToplevel> prefix_root_toplevel_vals = lcc_prefix_root_toplevel_.values();
		Iterator<LCCOutlinePrefixRootToplevel> prtlv_iterator = prefix_root_toplevel_vals.iterator();
		
		int prefix_toplevel_count = 0;
		
		int lcc_prefix_root_recursive_count = 0;
		
		while (prtlv_iterator.hasNext()) {			
			LCCOutlinePrefixRootToplevel curr_prefix_root = prtlv_iterator.next();

			int curr_root_prefix_count = countPrefixRootToplevelTraverse(curr_prefix_root);
			System.err.println("Count for root prefix '" + curr_prefix_root.prefix + "' = " + curr_root_prefix_count);
			
			lcc_prefix_root_recursive_count += curr_root_prefix_count;
			
			prefix_toplevel_count++;
		}
		
		System.err.println("******* Number of prefixes in JSON:          " + json_prefix_count);
		System.err.println("******* Number of prefixes in toplevel root: " + prefix_toplevel_count);
		
		// Debug checking
		//Collection<LCCOutlinePrefixRootToplevel> prefix_root_toplevel_vals = lcc_prefix_root_toplevel_.values();
		Iterator<LCCOutlinePrefixRootToplevel> prefix_root_toplevel_iterator = prefix_root_toplevel_vals.iterator();
		while (prefix_root_toplevel_iterator.hasNext()) {
			LCCOutlinePrefixRootToplevel prefix_root_toplevel_entry = prefix_root_toplevel_iterator.next();
			String prefix = prefix_root_toplevel_entry.prefix;
			int count = countPrefixRootToplevelTraverse(prefix_root_toplevel_entry);
			
			int json_count = json_treemap.getJSONArray(prefix).length();
			if (json_count != count) {
				System.err.println("*** Prefix cout comparison mismatch, prefix="+prefix 
						+ " root count/json count = " + count + "/" + json_count);
			}
		}
		
		System.err.println("**** Number of entries in hashmap:                     " + lcc_hashmap_lookup_len);
		System.err.println("**** Number of entries in prefix root recursive count: " + lcc_prefix_root_recursive_count);
		
		/*
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
		*/
	}
	
	


	public LCCMongoDBAction(ServletContext context, ServletConfig config)
	{
		super(context,config);
		
		if (LCCLookupMode_ == null) {
			LCCLookupMode_ = getConfigCheckIDMode(config,"lccLookupMode");
		}
		
		// Need a mongoDB connection regardless of 'mode' we are in, as various actions rely on it
		if (mongo_state_ != MongoDBState.FailedStartup) {
			mongo_lcc_col_ = connectToMongoDB(config,mongo_lcc_col_,"treeMap");
			
			if (mongo_lcc_col_ == null) {
				// Failed to connect file collection for some reason
				// => If Auto mode, then fall back to reading in the txt file to generate the Hashmap
				if (LCCLookupMode_ == StoreAccessOperationMode.Auto) {
					// Switch mode to using OnlyHashmap and work off the text-file list of IDs
					System.err.println("Switching to OnlyHashmap mode for LCC Treemap Lookup.");
					LCCLookupMode_ = StoreAccessOperationMode.OnlyHashmap; 
				}
			}
		}
		
		if (LCCLookupMode_ == StoreAccessOperationMode.Auto) {
			if (mongo_state_ != MongoDBState.FailedStartup) {
				LCCLookupMode_ = checkMongoDBUpToDate(context);
			}
			else {
				LCCLookupMode_ = StoreAccessOperationMode.OnlyHashmap;
			}
		}

		// Past this point, if LCCLookupMode_ was initially 'Auto' it will now be fixed to one of the other modes
		
		if ((LCCLookupMode_ == StoreAccessOperationMode.OnlyHashmap) || (LCCLookupMode_ == StoreAccessOperationMode.HashmapTransition)) {
			processTreemapResource(context);
		}
	}

	public  boolean isOperational()
	{
		if (LCCLookupMode_ == StoreAccessOperationMode.MongoDB) {
			return mongo_client_ != null;
		}
		else {
			return lcc_hashmap_lookup_ != null; // ****
		}
	}
	
	protected void storeTreemapXXXX(BufferedReader br) 
	{
	/*
		try {
			long line_num = 1;
			String line;
			
			System.err.print("Loading hashmap: ");
			while ((line = br.readLine()) != null) {

				String full_json_filename = line;
				String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);
				String id = VolumeUtils.filename_tail_to_id(json_filename_tail);

				id_check_.put(id, true);

				if (CheckIdMode_ == CheckIdOperationMode.HashmapTransition) {
					
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
				if ((line_num % 40*100000) == 0) {
					System.err.println();
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
*/
		
	}

	public boolean exists(String lcc_id) // ****
	{
		if (LCCLookupMode_ == StoreAccessOperationMode.MongoDB){
			MongoCursor<Document> cursor = mongo_lcc_col_.find(Filters.eq("_id",lcc_id)).iterator();
			return cursor.hasNext();
		}
		else {
			return lcc_hashmap_lookup_.containsKey(lcc_id);
		}
	}
	
	public int size() {
		if (LCCLookupMode_ == StoreAccessOperationMode.MongoDB) {
			
			long col_count = 0;
			if (mongo_state_ == MongoDBState.Connected) {
				col_count = mongo_lcc_col_.count();
			}
			
			return (int)col_count;
		}
		else {
			return lcc_hashmap_lookup_.size();
		}
	}
	

	
	public boolean lookupClassifiction(HttpServletResponse response, String id) throws IOException
	{
		String volume_id = getVolumeID(id);
		
		boolean exists = exists(volume_id);
		if (!exists) {
			// Error
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,"The requested volume id '" + volume_id + "' does not exist.");
		}
		
		return exists;
	}
	
	
	public boolean lookupClassifications(HttpServletResponse response, String[] ids) throws IOException
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
			if (!lookupClassifiction(response,id)) {
				check = false;
				break;
			}
		}
		
		return check;
	}
	
}