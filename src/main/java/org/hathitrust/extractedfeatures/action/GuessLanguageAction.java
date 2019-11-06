package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.hathitrust.extractedfeatures.io.FlexiResponse;

import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.Language;

public class GuessLanguageAction extends IdMongoDBAction
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

    
	public GuessLanguageAction(ServletContext context, ServletConfig config)
	{
		super(context,config);
		
		try {
			String lang_profiles_dir = context.getRealPath("/WEB-INF/classes/" + "language-detection-profiles");
			DetectorFactory.loadProfile(lang_profiles_dir);
		}
		catch (LangDetectException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isOperational() 
	{
		return true;
	}

	public String detect(String text) throws LangDetectException {
		Detector detector = DetectorFactory.create();
		detector.append(text);
		return detector.detect();
	}
	
	public ArrayList<Language> detectLangs(String text) throws LangDetectException {
		Detector detector = DetectorFactory.create();
		detector.append(text);
		return detector.getProbabilities();
	}
	    
	public void doAction(Map<String,List<String>> param_map, FlexiResponse flexi_response) 
			throws ServletException, IOException
	{
		ArrayList<Language> languages = null;

		String text_in = getParameter(param_map,"text-in");

		if (text_in != null) {
			try {
			languages = detectLangs(text_in);
			
			}
			catch (LangDetectException e) {
				e.printStackTrace();
				flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Language detection error occured while processing 'text-in' parameter '" + text_in + "'");
			}
		}
		else {
			flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'text-in' parameter to " + getHandle());
		}

		flexi_response.setContentType("application/json");

		flexi_response.append("[");

		int i = 0;
		
		for (Language l: languages) {
			if (i>0) {
				flexi_response.append(",");
			}
			flexi_response.append("{ \"lang\":\"" + l.lang + "\"," + "\"prob\":\"" + l.prob + "\"}" );
			i++;
		}
		flexi_response.append("]");
		
		// **** would this benefit from a \n to trigger a flush?
		// Or a close()?
	}
}

