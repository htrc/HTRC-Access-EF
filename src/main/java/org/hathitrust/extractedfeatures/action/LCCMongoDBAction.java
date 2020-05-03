package org.hathitrust.extractedfeatures.action;

import static com.mongodb.client.model.Filters.eq;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;


import org.bson.Document;
import org.hathitrust.extractedfeatures.lcc.LCCOutlineHashRec;
import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;



public abstract class LCCMongoDBAction extends BaseAction
{
    protected static int VERBOSITY = 1;
    
	protected static Logger logger = Logger.getLogger(LCCMongoDBAction.class.getName());
	
	private final String LCCTreeMapJSONResource = "/WEB-INF/classes/lcc-outline-treemap.json";
	
	//protected static OperationMode CheckIDMode_ = OperationMode.OnlyHashmap;
	//protected static OperationMode CheckIDMode_ = OperationMode.HashmapTransition;
	//protected static CheckIDOperationMode CheckIDMode_ = CheckIDOperationMode.MongoDB;
	protected static StoreAccessOperationMode LCCLookupMode_ = null;
	
	//protected static HashSet<String> lcc_hashset_rawids_ = null;
	
	//protected static int HASHMAP_INIT_SIZE = 16000000; // ****
	protected static HashMap<String, LCCOutlineHashRec> lcc_hashmap_lookup_ = null;
	protected static HashSet<String> lcc_prefix_set_ = null;
	protected static HashSet<String> lcc_prefix_doubleup_ = null;
	
	protected static HashMap<String, LCCOutlineHashRec> lcc_prefix_tree_lookup_ = null;
	
	//protected static int TEST_LIMIT = 100000;
	//protected static boolean APPLY_TEST_LIMIT = true;
	//protected static boolean APPLY_TEST_LIMIT = false;
	
	protected static MongoCollection<Document> mongo_lcc_col_ = null;
	
	public class LCCOutlineHashRecSortByStart implements Comparator<LCCOutlineHashRec>
	{
	    public int compare(LCCOutlineHashRec a, LCCOutlineHashRec b)
	    {	
	        if (a.start < b.start) {
	        	return -1;
	        } 
	        else if (a.start > b.start) {
	        	return +1;
	        }
	        else {
	        	return 0;
	        }
	    }
	}
	
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

		
		if (parents_len>0) {
			// Non-trivial parent chain => hook up the most immediate parent to child
			// i.e. last entry in the parents array
			
			String parent_id = lcc_hash_rec.parents.get(parents_len-1);
				
			LCCOutlineHashRec parent_rec = lcc_hashmap_lookup_.get(parent_id);

			if (parent_rec == null) {
				// The parent's full entry has not been processed yet,
				// => create a stub
				parent_rec = new LCCOutlineHashRec(curr_hash_rec.prefix,parent_id,curr_hash_rec);
				lcc_hashmap_lookup_.put(parent_id,parent_rec);
			}
			else {
				// entry already exists
				// => link in the current child
				LCCOutlineHashRec.connect(parent_rec,curr_hash_rec);
			}
		}
				
		else { // parents_len == 0
			String curr_prefix = curr_hash_rec.prefix;
			String curr_id = curr_hash_rec.id;
			
			// Create/update Top-level entry for the given prefix
			// But watch out for rec entries that already describe to top-level prefix
			
			if (!curr_id.equals(curr_prefix)) {
				if (!lcc_hashmap_lookup_.containsKey(curr_prefix)) {
					// Brand new prefix entry
					// ****
					LCCOutlineHashRec prefix_rec = new LCCOutlineHashRec(curr_prefix,curr_prefix,curr_hash_rec); // use prefix as id also
					prefix_rec.start = curr_hash_rec.start;
					prefix_rec.start_str = curr_hash_rec.start_str;
					
					prefix_rec.stop  = curr_hash_rec.stop;
					prefix_rec.stop_str  = curr_hash_rec.stop_str;
					
					lcc_hashmap_lookup_.put(curr_prefix, prefix_rec);
					lcc_prefix_set_.add(curr_prefix);
				}
				else {
					// Looking to add a top-level prefix entry where one already exits
					// => merge with existing info
					LCCOutlineHashRec existing_prefix_hash_rec = lcc_hashmap_lookup_.get(curr_prefix);
					LCCOutlineHashRec.connect(existing_prefix_hash_rec,curr_hash_rec);
				}
			}
			else {
				// Have an rec entry on our hands that defines the top-level prefix
				lcc_prefix_set_.add(curr_prefix);
				lcc_prefix_doubleup_.add(curr_prefix);
			}
		}
		
	}

	protected int hashRecTraverseCount(LCCOutlineHashRec hash_rec) 
	{
		if (hash_rec.child_ids_hash==null) { //  || (hash_rec.child_ids.size() == 0)) { // ****
			// Hit a leaf node
			return 1;
		}
	
		int child_count = 0;
		Collection<LCCOutlineHashRec> child_hash_recs = hash_rec.child_ids_hash.values();
		Iterator<LCCOutlineHashRec> child_hash_rec_iterator = child_hash_recs.iterator();
		
		while (child_hash_rec_iterator.hasNext()) {
			LCCOutlineHashRec child_hash_rec = child_hash_rec_iterator.next();
			
			child_count += hashRecTraverseCount(child_hash_rec);
		}
		
		return 1+child_count; //  this node + child_count 
	}
	
	protected void hashRecTraverseConvert(LCCOutlineHashRec hash_rec) 
	{
		if (hash_rec.child_ids_hash==null) { //  || (hash_rec.child_ids.size() == 0)) { // ****
			// Hit a leaf node
			return;
		}
	
		
		Collection<LCCOutlineHashRec> child_hash_recs = hash_rec.child_ids_hash.values();
		Iterator<LCCOutlineHashRec> child_hash_rec_iterator = child_hash_recs.iterator();
	
		// Recursively convert children
		while (child_hash_rec_iterator.hasNext()) {
			LCCOutlineHashRec child_hash_rec = child_hash_rec_iterator.next();
			
			hashRecTraverseConvert(child_hash_rec);
		}
		
		// Now convert hashmap of this entry's child-recs into a sorted array list
		LCCOutlineHashRec[] child_ids_ordered = new LCCOutlineHashRec[child_hash_recs.size()];
		int i =0;
		child_hash_rec_iterator = child_hash_recs.iterator();
		while (child_hash_rec_iterator.hasNext()) {
			LCCOutlineHashRec child_hash_rec = child_hash_rec_iterator.next();
			child_ids_ordered[i] = child_hash_rec;
			i++;
		}
		Arrays.sort(child_ids_ordered, new LCCOutlineHashRecSortByStart());
		
		hash_rec.child_ids_ordered = child_ids_ordered;
		hash_rec.child_ids_hash = null;
			
	}
	
	protected void orderedNodeTraversePrint(LCCOutlineHashRec ordered_node_rec) 
	{
	        if (VERBOSITY >=2) {
		    System.err.print(ordered_node_rec.prefix);
		    System.err.print(" [" + String.format("%s", ordered_node_rec.start_str) 
						+ "," + String.format("%s", ordered_node_rec.stop_str) + "]");
		    if (ordered_node_rec != null) {
			System.err.print(" Subject: " + ordered_node_rec.subject);
		    }
		    System.err.println();
		}
		
		if (ordered_node_rec.child_ids_ordered==null) { 
			// Hit a leaf node
			//System.err.println("Subject: " + ordered_node_rec.subject);
			return;
		}
		
		//System.err.println("Subject: " + ordered_node_rec.subject + "[" + ordered_node_rec.start_str + "," + ordered_node_rec.stop_str + "]");
		for (LCCOutlineHashRec child_node_rec: ordered_node_rec.child_ids_ordered)
		{
			orderedNodeTraversePrint(child_node_rec);
		}	
	}
	
	
	/*
	protected void generate_rawids(JSONObject json_treemap)
	{
		lcc_hashset_rawids_ = new HashSet<String>(json_treemap.length()*2);

		Iterator<String> treemap_keys = json_treemap.keys();
		
		while (treemap_keys.hasNext()) {
			String prefix_key = treemap_keys.next();
					
			JSONArray prefix_key_entries = json_treemap.getJSONArray(prefix_key);
			for (int i=0; i<prefix_key_entries.length(); i++) {
				JSONObject lcc_entry_rec_json = prefix_key_entries.getJSONObject(i);

				String id = lcc_entry_rec_json.getString("id");
				lcc_hashset_rawids_.add(id);
				
			}
		}
	}
	*/
	
	protected JSONObject readLCCJSONTreemap(InputStream is)
	{
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

		return json_treemap;
	}
	
	protected void generateLCCTreemap(JSONObject json_treemap)
	{
		lcc_hashmap_lookup_ = new HashMap<String, LCCOutlineHashRec>(json_treemap.length()*2);
		lcc_prefix_set_ = new HashSet<String>();
		lcc_prefix_doubleup_ = new HashSet<String>();
			
		Iterator<String> treemap_keys = json_treemap.keys();
		
		while (treemap_keys.hasNext()) {
			String prefix_key = treemap_keys.next();
					
			JSONArray prefix_key_entries = json_treemap.getJSONArray(prefix_key);
			for (int i=0; i<prefix_key_entries.length(); i++) {
				JSONObject lcc_entry_rec_json = prefix_key_entries.getJSONObject(i);
				
				LCCOutlineHashRec lcc_hash_rec = new LCCOutlineHashRec(lcc_entry_rec_json);
				storeLCCHashRec(lcc_hash_rec);
				
			}
		}
	}
	
	protected void convertLCCTreemapToTree()
	{
		lcc_prefix_tree_lookup_ = new HashMap<String, LCCOutlineHashRec>();

		Iterator<String> prefix_iterator = lcc_prefix_set_.iterator();

		while (prefix_iterator.hasNext()) {			
			String curr_prefix = prefix_iterator.next();

			LCCOutlineHashRec prefix_rec = lcc_hashmap_lookup_.get(curr_prefix);

			hashRecTraverseConvert(prefix_rec);
			lcc_prefix_tree_lookup_.put(curr_prefix, prefix_rec);
		}
		
		// allow the hashmap to be garbase collected
		// (all the nodes in the tree are now linked through lcc_prefix_tree_lookup_)
		lcc_hashmap_lookup_ = null;
	}

	protected void printLCCTree()
	{
		Iterator<String> prefix_iterator = lcc_prefix_set_.iterator();

		while (prefix_iterator.hasNext()) {			
			String curr_prefix = prefix_iterator.next();

			LCCOutlineHashRec prefix_rec = lcc_prefix_tree_lookup_.get(curr_prefix);

			orderedNodeTraversePrint(prefix_rec);
		}	
	}
	
	protected void processTreemapResource(ServletContext context)
	{
		//String json_str = readJSONFile(LCCTreeMapJSONResource); // ****
		InputStream is = context.getResourceAsStream(LCCTreeMapJSONResource);
		JSONObject json_treemap =  readLCCJSONTreemap(is);
	
		//generate_rawids(json_treemap); // ****
		
		int json_prefix_count = json_treemap.length();
		generateLCCTreemap(json_treemap);
		
		// Run check to ensure that the number of recs accessible through lcc_prefix_root_range
		// is the same as the number of keys in lcc_hashmap_lookup
		
		int lcc_hashmap_lookup_len = lcc_hashmap_lookup_.size();
		
		Iterator<String> prefix_iterator = lcc_prefix_set_.iterator();
		
		int prefix_toplevel_count = 0;
		int lcc_prefix_root_recursive_count = 0;
		
		int json_count_total = 0;
		
		while (prefix_iterator.hasNext()) {			
			String curr_prefix = prefix_iterator.next();

			LCCOutlineHashRec prefix_rec = lcc_hashmap_lookup_.get(curr_prefix);
			
			int curr_prefix_count = hashRecTraverseCount(prefix_rec);
			curr_prefix_count--; // subtract out this top-level prefix node from the count
			
			int json_compare_count;
			if (lcc_prefix_doubleup_.contains(curr_prefix)) {
				// The JSON file has the top-level prefix, e.g. "AP", as a regular rec entry
				json_compare_count = curr_prefix_count+1;
			}
			else {
				// everything is as it should be for a comparison
				json_compare_count = curr_prefix_count;
			}
			
			//System.err.println("Count for root prefix '" + prefix_rec.prefix + "' = " + curr_prefix_count);
			
			int json_count = json_treemap.getJSONArray(curr_prefix).length();
			json_count_total += json_count;
			
			if (json_count != json_compare_count) {
				System.err.println("*** Prefix count comparison mismatch, prefix="+curr_prefix 
						+ " root count/json count = " + json_compare_count + "/" + json_count);
			}
			/*
			hashRecTraverseConvert(prefix_rec);
			lcc_prefix_tree_lookup_.put(curr_prefix, prefix_rec);
			*/
			
			lcc_prefix_root_recursive_count += curr_prefix_count;
			
			prefix_toplevel_count++;
		}
		
		convertLCCTreemapToTree();
		printLCCTree();
		
		System.err.println("******* Number of prefixes in JSON:          " + json_prefix_count);
		System.err.println("******* Number of prefixes in toplevel root: " + prefix_toplevel_count);
		
		System.err.println("******* JSON count total:                                    " + json_count_total);
		System.err.println("**** Number of entries in hashmap:                           " + lcc_hashmap_lookup_len);
		//System.err.println("**** Adjusted to eliminate explicit prefix entries:        " + (lcc_hashmap_lookup_len - lcc_prefix_doubleup_.size()));
		System.err.println("**** Number of entries in prefix root set recursive count:   " + lcc_prefix_root_recursive_count);
		System.err.println("**** Combined root set recursive count + Number of prefixes: " + (lcc_prefix_root_recursive_count + prefix_toplevel_count));
		
		
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
			return this.lcc_prefix_tree_lookup_ != null;
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

	
	public String getLCCSubjectRec(LCCOutlineHashRec ordered_node, double num)
	{
		if ((num < ordered_node.start) || (num > ordered_node.stop)) {
			return null;
		}
		
		// check to see if we can refine which node 'num' falls in any further through child nodes
		String subject = null;
		
		if (ordered_node.child_ids_ordered != null) {
			for (LCCOutlineHashRec child_ordered_node: ordered_node.child_ids_ordered) {
				if ((num >= child_ordered_node.start) && (num <= child_ordered_node.stop)) {
					subject = getLCCSubjectRec(child_ordered_node,num);
					break;
				}
			}
		}
		if (subject == null) {
			// no child noes, or no match found in child nodes
			// => return the subject entry for this current node
			subject = ordered_node.subject;
		}
		
		return subject;
	}
	
	public String getLCCSubject(String lcc_id) 
	{
		
		if (LCCLookupMode_ == StoreAccessOperationMode.MongoDB){
			MongoCursor<Document> cursor = mongo_lcc_col_.find(Filters.eq("_id",lcc_id)).iterator();
			return "not implemented yet"; // cursor.hasNext();
		}
		else {
			// divide into prefix and numeric value
			String subject = null;
		
			Pattern prefix_num_pattern = Pattern.compile("([A-Z]+)(\\d+(?:\\.\\d+)?)$"); 
			Matcher prefix_num_matcher = prefix_num_pattern.matcher(lcc_id);
			
			if (prefix_num_matcher.find()) {
				String prefix_alpha = prefix_num_matcher.group(1);
				String prefix_num_str = prefix_num_matcher.group(2);
				double prefix_num = Double.parseDouble(prefix_num_str);
				
				LCCOutlineHashRec prefix_rec = lcc_prefix_tree_lookup_.get(prefix_alpha);
				if (prefix_rec != null) {
					subject = getLCCSubjectRec(prefix_rec,prefix_num);
				}
			}
			
			return subject;
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
	

	/*
	public String lookupClassifiction(FlexiResponse flexi_response, String id) throws IOException
	{		
		String subject = getLCCSubject(id);
		if (subject == null) {
			// Error
			response.sendError(HttpServletResponse.SC_BAD_REQUEST,"The requested id '" + id + "' does not exist in the LCC hierarchy.");
		}
		
		return subject;
	}
	
	
	public String[] lookupClassifications(FlexiResponse flexi_response, String[] ids) throws IOException
	{
		int ids_len = ids.length;
		String[] subjects = new String[ids_len];
		
		for (int i=0; i<ids_len; i++) {

			String id = ids[i];
			String subject = lookupClassifiction(flexi_response,id);
			subjects[i] = subject;
		}
		
		return subjects;
	}
	*/
}
