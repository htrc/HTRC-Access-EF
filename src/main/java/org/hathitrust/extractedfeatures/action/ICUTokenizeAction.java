package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.hathitrust.extractedfeatures.io.FlexiResponse;
import org.apache.lucene.analysis.core.LowerCaseFilter;

public class ICUTokenizeAction extends IdMongoDBAction
{
	public String getHandle() 
	{
		return "icu-tokenize";
	}
	
	public String[] getDescription() 
	{
		String[] mess = 
			{ "International Components for Unicode (ICU) Tokenize",
					"Required parameter: 'text-in'"
			};
		
		return mess;
	}

    
	public ICUTokenizeAction(ServletContext context, ServletConfig config) 
	{
		super(context,config);
	}

	public boolean isOperational() 
	{
		return true;
	}
	
	public void doAction(Map<String,List<String>> param_map, FlexiResponse flexi_response) 
			throws ServletException, IOException
	{
	    boolean lowercase_filter = false;
	    ArrayList<String> words = new ArrayList<String>();
	    
		String text_in = getParameter(param_map,"text-in");
		
		if (text_in != null) {
		    Reader reader = new StringReader(text_in);
		    
		    ICUTokenizer icu_tokenizer = new ICUTokenizer();
		    icu_tokenizer.setReader(reader);
		    
		    CharTermAttribute charTermAttribute = icu_tokenizer.addAttribute(CharTermAttribute.class);
		    
		    TokenStream token_stream = null;
		    
		    if (lowercase_filter) {
			token_stream = new LowerCaseFilter(icu_tokenizer);
		    }
		    else {
			token_stream = icu_tokenizer;
		    }
		    
		    try {
			token_stream.reset();
			
			while (token_stream.incrementToken()) {
			    String term = charTermAttribute.toString();
			    words.add(term);
			}
			
			token_stream.end();
			token_stream.close();
		    }
		    catch (IOException e) {
			e.printStackTrace();
		    }
		    
		}
		else {
			flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'text-in' parameter to " + getHandle());
		}

		flexi_response.setContentType("application/json");
		
		flexi_response.append("{");
		
		flexi_response.append("\"text_out\": \"" + String.join(" ", words) + "\"");
		flexi_response.append("}");
		
		// ****
		// \n to flush(), or close() ??
	}
}

