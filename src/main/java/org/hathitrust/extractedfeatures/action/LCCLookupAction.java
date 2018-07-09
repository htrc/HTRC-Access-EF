package org.hathitrust.extractedfeatures.action;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;  

public class LCCLookupAction extends LCCMongoDBAction
{	
	public String getHandle() 
	{
		return "lcc-lookup";
	}
	
	public String[] getDescription() 
	{
		String[] mess = 
			{ "Provides the text description for the specified Library of Congress Classification (LCC) code(s).",
					"Required parameter: 'code' or 'codes'",
					"Returns:            a JSON record giving text description for each code given."
			};
		
		return mess;
	}
	
	public LCCLookupAction(ServletContext context, ServletConfig config) 
	{
		super(context,config);
	}
	
	public void outputJSON(HttpServletResponse response, String[] codes) throws IOException
	{
		response.setContentType("application/json");
		PrintWriter pw = response.getWriter();
		
		int codes_len = codes.length;

		pw.append("{");

		for (int i = 0; i < codes_len; i++) {
			String code = codes[i];

			//boolean description = exists(id);

			if (i > 0) {
				pw.append(",");
			}
			pw.append("\"" + code + "\":" + "fake desc for now");
		}
		pw.append("}");
	}
	
	public void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException
	{
		String cgi_codes = request.getParameter("codes");

		if (cgi_codes == null) {
			String cgi_code = request.getParameter("code");
			if (cgi_code != null) {
				// if cgi_id in play then upgrade to cgi_ids (one item in it) to simplify later code
				cgi_codes = cgi_code;
			}
		}

		if (cgi_codes != null) {
			String[] codes = cgi_codes.split(",");
			outputJSON(response,codes);
		}
		else {
			
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter to action '" + getHandle()
				+"' -- either parameter 'id' or 'ids' must be specified.");
		}
	}
}
