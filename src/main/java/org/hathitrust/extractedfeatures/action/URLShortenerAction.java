package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Random;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// The following class based loosely on details at:
//   https://gist.github.com/rakeshsingh/64918583972dd5a08012

public class URLShortenerAction extends BaseAction
{
	// Storage for generated keys
	protected HashMap<String, String> key_map_;   // key-value map
	protected HashMap<String, String> value_map_; // value-key map to quickly check whether value already exists
	
	protected final char output_alphabet_[];
	protected final int output_alphabet_length_;
	
	protected Random random_generator_;   
	protected final int key_length_ = 32; // The length of the output keys generated

	public String getHandle() 
	{
		return "url-shortener";
	}
	
	public String[] getDescription() 
	{
		String[] mess = 
			{ "Shorten or Expand the provided argument",
					"Required parameter: 'value' or 'key'",
					"    If provided 'value' then returned shorten key\n"
				   +"    If provided 'key' then returns expand value\n"
				   +"    Add additional argument 'redirect=true' to 'key' for the output\n"
				   +"      generated to be redirect to the key interpreted as a URL"
			};
		
		return mess;
	}
	
	public URLShortenerAction(ServletContext context) 
	{
		super(context);
	
		key_map_ = new HashMap<String, String>();
		value_map_ = new HashMap<String, String>();
		
		random_generator_ = new Random();
		
		output_alphabet_ = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
										'A', 'B', 'C', 'D', 'E', 'F' };
		output_alphabet_length_ = output_alphabet_.length;
	}

	protected String generateKey() 
	{
		String key = null;
		
		while (key == null) {
			String pot_key = "";
			for (int i=0; i<=key_length_; i++) {
				int rand_oa_char = random_generator_.nextInt(output_alphabet_length_);
						
				pot_key += output_alphabet_[rand_oa_char];
			}
			
			if (!key_map_.containsKey(pot_key)) {
				key = pot_key;
			}
		}
		return key;
	}
	
	protected String getKey(String value) 
	{
		String key = generateKey();
		key_map_.put(key, value);
		value_map_.put(value, key);
		
		return key;
	}

	
	protected String shortenValue(String value) 
	{
		String key = null;
		
		if (value_map_.containsKey(value)) {
			key = value_map_.get(value);
		} else {
			key= getKey(value);
		}
		
		return key;
	}
	
	protected String expandKey(String key) 
	{
		return key_map_.get(key);
	}
	
	public void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException
	{
		String value = request.getParameter("value");
		String key   = request.getParameter("key");
		
		if (value != null) {
			key = shortenValue(value);
			
			PrintWriter pw = response.getWriter();
			pw.append(key);
		}
		else if (key != null) {
			value = expandKey(key);
			
			String redirect = request.getParameter("redirect");
			if ((redirect != null) && (redirect.equals("true") || redirect.equals("1"))) {
				response.sendRedirect(value);	
			}
			else {
				PrintWriter pw = response.getWriter();
				pw.append(value);
			}
		}
		else {
			
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'value' or 'key' parameter to " + getHandle());
		
		}
	}
}

