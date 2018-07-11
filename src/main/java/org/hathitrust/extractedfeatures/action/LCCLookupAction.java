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
			{ "Provides the text description for the specified Library of Congress Classification (LCC) id(s).",
					"Required parameter: 'id' or 'ids'",
					"Returns:            a JSON record giving text description for each id given."
			};
		
		return mess;
	}
	
	public LCCLookupAction(ServletContext context, ServletConfig config) 
	{
		super(context,config);
	}
	
	public void outputJSON(HttpServletResponse response, String[] ids) throws IOException
	{
		response.setContentType("application/json");
		PrintWriter pw = response.getWriter();
		
		int ids_len = ids.length;

		pw.append("{");

		for (int i = 0; i < ids_len; i++) {
			String id = ids[i];

			String subject = this.getLCCSubject(id);

			if (i > 0) {
				pw.append(",");
			}
			pw.append("\"" + id + "\":\"" + subject + "\"");
		}
		pw.append("}");
	}
	
	public void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException
	{
		String cgi_ids = request.getParameter("ids");

		if (cgi_ids == null) {
			String cgi_id = request.getParameter("id");
			if (cgi_id != null) {
				// if cgi_id in play then upgrade to cgi_ids (one item in it) to simplify later code
				cgi_ids = cgi_id;
			}
		}

		if (cgi_ids != null) {
			String[] ids = cgi_ids.split(",");
			outputJSON(response,ids);
		}
		else {
			
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing parameter to action '" + getHandle()
				+"' -- either parameter 'id' or 'ids' must be specified.");
		}
	}
}
