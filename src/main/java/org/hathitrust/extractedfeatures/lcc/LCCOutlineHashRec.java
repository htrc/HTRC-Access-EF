package org.hathitrust.extractedfeatures.lcc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;  

/* Example record:
{
  "id": "AC9-195",
  "parents": [
      "AC1-195"
  ],
  "prefix": "AC",
  "start": 9.0,
  "stop": 195.0,
  "subject": "Other languages"
}
*/
public class LCCOutlineHashRec 
{	
	public final String prefix;
	public final String id;
	public String start_str = null;
	public String stop_str = null;
	public Double start = null;
	public Double stop  = null;
	public String subject;
	
	public ArrayList<String> parents = null;		
	public HashMap<String,LCCOutlineHashRec> child_ids_hash = null;
	public LCCOutlineHashRec [] child_ids_ordered = null;

	public LCCOutlineHashRec(String stub_prefix, String stub_id, LCCOutlineHashRec child_rec) {
		prefix = stub_prefix;
		id = stub_id;
		child_ids_hash = new HashMap<String,LCCOutlineHashRec>();
		child_ids_hash.put(child_rec.id, child_rec);
	}
	
	public LCCOutlineHashRec(JSONObject lcc_outline_rec) {
		prefix = lcc_outline_rec.getString("prefix");
		id = lcc_outline_rec.getString("id");

		start = lcc_outline_rec.getDouble("start");
		stop  = lcc_outline_rec.getDouble("stop");

		
		if (id.matches("[A-Z]+")) {
			start_str = start.toString();
			stop_str  = stop.toString();
		}
		else {
			// Need to go back to the 'id' to correctly work out what the string version of start and stop are
			// Example of form to parse:  
			//    (i) AC1-195, 
			//   (ii) BF692-692.5
			//  (iii) JC328.6-.65
			
			// Take on (i) and (ii) here
			Pattern start_stop_pattern = Pattern.compile("[A-Z]+(\\d+(?:\\.\\d+)?)(?:-(\\d+(?:\\.\\d+)?))?$"); 
			Matcher start_stop_matcher = start_stop_pattern.matcher(id);
			if (start_stop_matcher.find()) {
				start_str = start_stop_matcher.group(1);
				stop_str = start_stop_matcher.group(2);
				if (stop_str==null) {
					// number range is in fact a 'singularity'
					stop_str = start_str;
				}
			}
			else {
				
				// Take on (iii) here
				Pattern start_frac_stop_pattern = Pattern.compile("[A-Z]+(\\d+)\\.(\\d+)-\\.(\\d+)$"); 
				Matcher start_frac_stop_matcher = start_frac_stop_pattern.matcher(id);
				if (start_frac_stop_matcher.find()) {
					String whole_start_str = start_frac_stop_matcher.group(1);
					String frac_start_str  = start_frac_stop_matcher.group(2);
					String frac_stop_str   = start_frac_stop_matcher.group(3);
					
					start_str = whole_start_str+"."+frac_start_str;
					stop_str  = whole_start_str+"."+frac_stop_str; // tack on frac_stop with whole_start
					
					System.err.println("Warning: id " + id + " expresses stopping value only fractional part.  Parent tree in JSON file suspect");
				}
				else {
					System.err.println("Error: failed to match 'start' and 'stop' on id=" + id);
				}
			}
		}
		
		subject = lcc_outline_rec.getString("subject");
		
		JSONArray parents_jsonarray = lcc_outline_rec.getJSONArray("parents");			
		if (parents_jsonarray != null) { 
			parents = new ArrayList<String>();
			for (int i=0;i<parents_jsonarray.length();i++){ 
				parents.add(parents_jsonarray.getString(i));
			} 
		}
	}
	
	public static void merge(LCCOutlineHashRec existing_rec, LCCOutlineHashRec new_rec)
	{
		// Used to merge a partially completed exiting node with more complete info
		
		// Consider asserting the following:
		//   new_rec.id == this.id
		//   existing_rec.start and existing_rec.stop are null
		//   existing_rec.subject is null
		//existing_rec.prefix = new_rec.prefix; // Now already set in stub constructor // ****
		
		existing_rec.start = new_rec.start;
		existing_rec.start_str = new_rec.start_str;
		
		existing_rec.stop  = new_rec.stop;
		existing_rec.stop_str = new_rec.stop_str;
		
		existing_rec.subject = new_rec.subject;
		
		existing_rec.parents = new_rec.parents; // shared ref OK
		
	}
	
	public static boolean connect(LCCOutlineHashRec parent_rec, LCCOutlineHashRec child_rec)
	{
		boolean new_connection_made = false;
		
		if (parent_rec.child_ids_hash == null) {
			// first case of a child node being connected up to this parent node
			// => create empty hashmap for children
			parent_rec.child_ids_hash = new HashMap<String,LCCOutlineHashRec>();
		}
		
		String child_key = child_rec.id;
		if (!parent_rec.child_ids_hash.containsKey(child_key)) {
			parent_rec.child_ids_hash.put(child_key, child_rec);
			new_connection_made = true;
		}
		
		// Update start and stop values
		if (parent_rec.start == null) {
			parent_rec.start = child_rec.start;
			parent_rec.start_str = child_rec.start_str;
		}
		else {
			if (child_rec.start < parent_rec.start) {
				parent_rec.start = child_rec.start;
				parent_rec.start_str = child_rec.start_str;
			}
		}
		
		if (parent_rec.stop == null) {
			parent_rec.stop = child_rec.stop;
			parent_rec.stop_str = child_rec.stop_str;
		}
		else {
			if (child_rec.stop > parent_rec.stop) {
				parent_rec.stop = child_rec.stop;
				parent_rec.stop_str = child_rec.stop_str;
			}
		}
		
		return new_connection_made;
	}
		
}

