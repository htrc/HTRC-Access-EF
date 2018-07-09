package org.hathitrust.extractedfeatures.lcc;

import java.util.ArrayList;  

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

public class LCCOutlineTreeNode 
{

	public String prefix; // **** is this needed?
	public String id;
	public ArrayList<LCCOutlineTreeNode> child_nodes;

	public double start;
	public double stop;
	public String subject;
	
	public LCCOutlineTreeNode(LCCOutlineHashRec lcc_outline_hash_rec)
	{
		prefix = lcc_outline_hash_rec.prefix;
		id = lcc_outline_hash_rec.id;
		
		child_nodes = null;
		
		start = lcc_outline_hash_rec.start;
		stop = lcc_outline_hash_rec.stop;
		
		subject = lcc_outline_hash_rec.subject;
	}
}

