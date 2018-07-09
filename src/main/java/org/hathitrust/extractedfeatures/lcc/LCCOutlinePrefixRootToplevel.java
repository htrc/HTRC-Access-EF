package org.hathitrust.extractedfeatures.lcc;

import java.util.Collection;
import java.util.HashMap;

public class LCCOutlinePrefixRootToplevel 
{
	public final String prefix; 
	//public Double min_start = null;
	//public Double max_stop  = null;
	//public String min_start_str = null;
	//public String max_stop_str  = null;
	
	protected HashMap<String,LCCOutlineHashRec> toplevel_ids_ = null;
	
	public LCCOutlinePrefixRootToplevel(String prefix_key) 
	{
		prefix = prefix_key;	
		toplevel_ids_ = new HashMap<String,LCCOutlineHashRec>();
	}	
	
	public void addTopLevelRecEntry(LCCOutlineHashRec hash_rec)
	{
		toplevel_ids_.put(hash_rec.id, hash_rec);
	}
	
	public Collection<LCCOutlineHashRec> getTopLevelRecEntries()
	{
		return toplevel_ids_.values();
	}
	
}
