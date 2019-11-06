package org.hathitrust.extractedfeatures.action;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.hathitrust.extractedfeatures.io.FlexiResponse;  

public class CheckExistsAction extends IdMongoDBAction
{	
	public String getHandle() 
	{
		return "check-exists";
	}
	
	public String[] getDescription() 
	{
		String[] mess = 
			{ "Check the specified ID(s) exists.",
					"Required parameter: 'id' or 'ids'",
					"Returns:            a JSON record reporting true/false for each id given."
			};
		
		return mess;
	}
	
	public CheckExistsAction(ServletContext context, ServletConfig config) 
	{
		super(context,config);
	}
	
	public void outputJSON(FlexiResponse flexi_response, String[] ids) throws IOException
	{
		flexi_response.setContentType("application/json");
		
		int ids_len = ids.length;

		flexi_response.append("{");

		for (int i = 0; i < ids_len; i++) {
			String id = ids[i];

			boolean exists = exists(id);

			if (i > 0) {
				flexi_response.append(",");
			}
			flexi_response.append("\"" + id + "\":" + exists);
		}
		flexi_response.append("}");
	}
	
	public void doAction(Map<String,List<String>> param_map, FlexiResponse response) 
			throws ServletException, IOException
	{
		String cgi_ids = getParameter(param_map,"ids");

		if (cgi_ids == null) {
			String cgi_id = getParameter(param_map,"id");
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
