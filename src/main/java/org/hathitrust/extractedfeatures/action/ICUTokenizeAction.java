package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.core.LowerCaseFilter;

public class ICUTokenizeAction extends BaseAction
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

    
	public ICUTokenizeAction(ServletContext context) 
	{
		super(context);
	}

	public void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException
	{
	    boolean lowercase_filter = false;
	    ArrayList<String> words = new ArrayList<String>();
	    
		String text_in = request.getParameter("text-in");
		
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
		    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'text-in' parameter to " + getHandle());
		}

		response.setContentType("application/json");
		PrintWriter pw = response.getWriter();
		
		pw.append("{");
		
		pw.append("\"text_out\": \"" + String.join(" ", words) + "\"");
		pw.append("}");
	}
}

