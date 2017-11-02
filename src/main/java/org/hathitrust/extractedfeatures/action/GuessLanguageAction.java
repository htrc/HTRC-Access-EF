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



import org.apache.tika.exception.TikaException;
import org.apache.tika.language.LanguageIdentifier;

public class GuessLanguageAction extends BaseAction
{
	public String getHandle() 
	{
		return "guess-language";
	}
	
	public String[] getDescription() 
	{
		String[] mess = 
			{ "Guess language of given text",
					"Required parameter: 'text-in'"
			};
		
		return mess;
	}

    
	public GuessLanguageAction(ServletContext context) 
	{
		super(context);
		
		
	}

	public void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException
	{
		String language = "en"; // default

		String text_in = request.getParameter("text-in");

		if (text_in != null) {

			LanguageIdentifier identifier = new LanguageIdentifier(text_in);
			language = identifier.getLanguage();

		}
		else {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'text-in' parameter to " + getHandle());
		}

		response.setContentType("application/json");
		PrintWriter pw = response.getWriter();

		pw.append("{");

		pw.append("\"lang\":" + String.join(" ", language));
		pw.append("}");
	}
}

