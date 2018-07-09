package org.hathitrust.extractedfeatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.bson.Document;
import org.hathitrust.extractedfeatures.action.IdMongoDBAction;
import org.hathitrust.extractedfeatures.action.IdentiferRegExp;
import org.hathitrust.extractedfeatures.action.ShoppingcartAction;

public class CartContent {
	protected static Logger logger = Logger.getLogger(ShoppingcartAction.class.getName()); 
	
	final int VolIdPairPos = 0;
	final int SeqIdPairPos = 1;
	
	private HashMap<String, Integer> vol_ids_;
	private HashMap<String, Integer> seq_ids_;
	
	public CartContent(String ids_str) 
	{
		vol_ids_ = new HashMap<String,Integer>();
		seq_ids_ = new HashMap<String,Integer>();
		
		appendToCart(ids_str);
	}
	
	public CartContent(List<String> vol_ids, List<String> seq_ids) 
	{
		vol_ids_ = new HashMap<String,Integer>();
		seq_ids_ = new HashMap<String,Integer>();
		
		appendToCart(vol_ids,seq_ids);
	}
	
	protected ArrayList<String>[] separateCartIds(String ids_str)
	{
		ArrayList<String> vol_ids = new ArrayList<String>();
		ArrayList<String> seq_ids = new ArrayList<String>();
		
		String [] ids = ids_str.split(",");
		
		for (String id: ids) {
			Matcher seq_matcher = IdentiferRegExp.SeqPattern.matcher(id);

			if (seq_matcher.matches()) {
				// abc.12345678-seq-1234 format
				seq_ids.add(id);
			}
			else {
				// abc.12345678 format
				vol_ids.add(id);
			}
		}
		
		return new ArrayList[] {vol_ids, seq_ids};
	}
	
	protected void appendToCart(List<String> vol_ids, List<String> seq_ids) 
	{
		for (String vol_id: vol_ids) {
				if (vol_ids_.containsKey(vol_id)) {
					int num_vols = vol_ids_.get(vol_id);
					num_vols++;
					vol_ids_.put(vol_id,num_vols);
				}
				else {
					vol_ids_.put(vol_id, 1);
				}
		}
		
		for (String seq_id: seq_ids) {
			seq_ids_.put(seq_id, 1);
		}
	}
	
	
	public void appendToCart(String ids_str)
	{
		ArrayList<String>[] ids_pair = separateCartIds(ids_str);
		
		ArrayList<String> vol_ids = ids_pair[VolIdPairPos];
		ArrayList<String> seq_ids = ids_pair[SeqIdPairPos];
				
		appendToCart(vol_ids, seq_ids);
	}
	
	protected void removeFromCart(List<String> vol_ids, List<String> seq_ids) 
	{
		for (String vol_id: vol_ids) {
				if (vol_ids_.containsKey(vol_id)) {
					vol_ids_.remove(vol_id);
				}
				else {
					logger.warning("removeFromCart(): id '" + vol_id + "does not exist");
				}
		}
		
		for (String seq_id: seq_ids) {
			if (seq_ids_.containsKey(seq_id)) {
				seq_ids_.remove(seq_id);
			}
			else {
				logger.warning("removeFromCart(): id '" + seq_id + "does not exist");
			}
		}
	}
	
	public void removeFromCart(String ids_str)
	{
		ArrayList<String>[] ids_pair = separateCartIds(ids_str);
		
		ArrayList<String> vol_ids = ids_pair[VolIdPairPos];
		ArrayList<String> seq_ids = ids_pair[SeqIdPairPos];
				
		removeFromCart(vol_ids, seq_ids);
	}
	
	protected String setToJSONArray(Set<String> set) {
		String json_str = set.isEmpty() ? "[]" : "[ \"" + String.join("\", \"", set) + "\"]";
		return json_str;
	}
	
	public String toJSON(String key) 
	{	
		Set<String> vol_ids_set = vol_ids_.keySet();
		Set<String> seq_ids_set = seq_ids_.keySet();
		
		String cart_json_str = "{ \"vol_ids_\": " + setToJSONArray(vol_ids_set)
							 + ", \"seq_ids_\": " + setToJSONArray(seq_ids_set) + "}";
		
		String doc_str = "{ \"_id\": \"" + key +"\" , \"cart\":" + cart_json_str + "}";
		
		//System.err.println("**** doc str = " + doc_str);
		
		return doc_str;
	}

	public Document toDocument(String key) 
	{	
		String doc_str = toJSON(key);	
		Document doc = Document.parse(doc_str);
		
		return doc;
	}
	
	public static CartContent DocumentToCart(Document doc)
	{
		Document cart_doc = (Document) doc.get("cart");
		List<String> vol_ids = (List<String>)cart_doc.get("vol_ids_");
		List<String> seq_ids = (List<String>)cart_doc.get("seq_ids_");
		CartContent cart = new CartContent(vol_ids,seq_ids);
		
		return cart;
	}
	
}