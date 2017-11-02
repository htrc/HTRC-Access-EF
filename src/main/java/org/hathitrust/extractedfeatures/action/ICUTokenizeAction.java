package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
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
		String text_in = request.getParameter("text-in");
		
		if (text_in != null) {

		}
		else {
		    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'text-in' parameter to " + getHandle());
		}
	}
}

