package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
import java.util.HashMap;
import java.util.Random;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// The following class based on details at:
//   https://gist.github.com/rakeshsingh/64918583972dd5a08012

public class URLShortener extends BaseAction
{
	// storage for generated keys
	private HashMap<String, String> key_map_;   // key-url map
	private HashMap<String, String> value_map_; // url-key map to quickly check whether an url is
	
	private String domain_; 
	private char char_to_num_[];      // This array is used for character to number mapping
	private Random random_generator_; // Random object used to generate random integers
	private int key_length_;          // the key length in URL defaults to 8


	public String getHandle() 
	{
		return "url-shortener";
	}
	
	public String[] getDescription() 
	{
		String[] mess = 
			{ "Generate a short URL for the 'content' argument",
					"Required parameter: 'long-url'"
			};
		
		return mess;
	}
	
	public URLShortener(ServletContext context, int length, String domain) 
	{
		super(context);
		
		//For example,
		//   length = 8
		//   domain = "http://solr1.ischool.illinois.edu/htrc-ef-access/get/";
		
		key_length_ = length;
		domain_ = domain;
		
		key_map_ = new HashMap<String, String>();
		value_map_ = new HashMap<String, String>();
		
		random_generator_ = new Random();
		
		char_to_num_ = new char[62];
		for (int i = 0; i < 62; i++) {
			int j = 0;
			if (i < 10) {
				j = i + 48;
			} else if (i > 9 && i <= 35) {
				j = i + 55;
			} else {
				j = i + 61;
			}
			char_to_num_[i] = (char) j;
		}	
	}

	private String getKey(String longURL) {
		String key;
		key = generateKey();
		key_map_.put(key, longURL);
		value_map_.put(longURL, key);
		return key;
	}

	// generateKey
	private String generateKey() {
		String key = "";
		boolean flag = true;
		while (flag) {
			key = "";
			for (int i = 0; i <= key_length_; i++) {
				key += char_to_num_[random_generator_.nextInt(62)];
			}
			// System.out.println("Iteration: "+ counter + "Key: "+ key);
			if (!key_map_.containsKey(key)) {
				flag = false;
			}
		}
		return key;
	}

	
	// shortenURL
	// the public method which can be called to shorten a given URL
	public String shortenURL(String long_url) 
	{
		String short_url = "";
		if (value_map_.containsKey(long_url)) {
			short_url = domain_ + "/" + value_map_.get(long_url);
		} else {
			short_url = domain_ + "/" + getKey(long_url);
		}
		
		return short_url;
	}

	// expandURL
	// public method which returns back the original URL given the shortened url
	public String expandURL(String short_url) 
	{
		String long_url = "";
		String key = short_url.substring(domain_.length() + 1);
		long_url = key_map_.get(key);
		return long_url;
	}

	public void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException
	{
		String long_url = request.getParameter("long-url");
		
		if (long_url != null) {
		
			String short_url = shortenURL(long_url);
			
			//outputText(response,short_url);
		}
		else {
			
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'long-url' parameter to " + getHandle());
		
		}
	}

	// test the code
	public static void main(String args[]) {
		URLShortener u = new URLShortener(null, 5, "www.tinyurl.com/");
		String urls[] = { "www.google.com/", "www.google.com",
				"http://www.yahoo.com", "www.yahoo.com/", "www.amazon.com",
				"www.amazon.com/page1.php", "www.amazon.com/page2.php",
				"www.flipkart.in", "www.rediff.com", "www.techmeme.com",
				"www.techcrunch.com", "www.lifehacker.com", "www.icicibank.com" };

		for (int i = 0; i < urls.length; i++) {
			System.out.println("URL:" + urls[i] + "\tTiny: "
					+ u.shortenURL(urls[i]) + "\tExpanded: "
					+ u.expandURL(u.shortenURL(urls[i])));
		}
	}
}

