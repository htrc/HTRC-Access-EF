package org.hathitrust.extractedfeatures.action;

import java.util.regex.Pattern;

public class IdentiferRegExp 
{
	public static Pattern SeqPattern = Pattern.compile("^(.*)-seq-(\\d+)$");	
	public static Pattern MetadataPattern = Pattern.compile("^(.*)-metadata$");	
}
