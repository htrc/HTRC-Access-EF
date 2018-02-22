package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bson.Document;
import org.hathitrust.extractedfeatures.CartContent;

import static com.mongodb.client.model.Filters.*;

import com.mongodb.client.MongoCollection;

public class ShoppingcartAction extends BaseAction
{
	protected static Logger logger = Logger.getLogger(ShoppingcartAction.class.getName()); 
	
	// Storage for generated keys
	protected HashMap<String, CartContent> cart_map_;   

	protected static MongoCollection<Document> mongo_shoppingcart_col_ = null;

	public String getHandle() 
	{
		return "shoppingcart";
	}

	public String[] getDescription() 
	{
		String[] mess = 
			{ "Create, retrieve, add and remove items to a specified shopping-cart",
					"Required parameter: 'key'\n"
							+"                    mode=set|add-ids|del-ids|get\n",
							"Further parameter: 'ids' (used with 'set', 'add-ids' and 'del-ids')\n"
									+"Returns:           'status ok' for 'set', 'add-ids' and 'del-ids'; returns ids list for 'get'"
			};

		return mess;
	}

	public ShoppingcartAction(ServletContext context) 
	{
		super(context);

		cart_map_ = new HashMap<String, CartContent>();

		if (mongo_shoppingcart_col_ == null) {
			if (mongo_state_ == MongoDBState.Connected) {
				// The following will create the collection if it didn't already exist
				mongo_shoppingcart_col_ = mongo_db_.getCollection("shoppingcart");
			}
		}

	}

	protected void replaceCart(String key, CartContent cart) 
	{		
		cart_map_.put(key, cart);
		Document doc = cart.toDocument(key);
		mongo_shoppingcart_col_.replaceOne(eq("_id",key), doc);
	}
	
	protected void setCart(String key, CartContent cart) 
	{		
		cart_map_.put(key, cart);
		
		Document doc = cart.toDocument(key);
		Document prev_doc = mongo_shoppingcart_col_.find(eq("_id", key)).first();

		if (prev_doc == null) {
			mongo_shoppingcart_col_.insertOne(doc);
		}
		else {
			mongo_shoppingcart_col_.replaceOne(eq("_id",key), doc);
		}
	}
	
	protected void setCart(String key, String ids) 
	{
		CartContent cart = new CartContent(ids);
		setCart(key,cart);
	}

	protected void setCart(String key, List<String> vol_ids, List<String> seq_ids) 
	{
		CartContent cart = new CartContent(vol_ids,seq_ids);
		setCart(key,cart);
	}
	
	protected void addToCart(String key, String ids_str) 
	{
		// Look for in cache first before going to DB // ****
		CartContent cart = cart_map_.get(key);

		if (cart != null) {
			cart.appendToCart(ids_str);
			replaceCart(key,cart);
		}
		else {
			// Retrieve from MongoDB
			Document doc = mongo_shoppingcart_col_.find(eq("_id", key)).first();

			if (doc == null) {
				setCart(key,ids_str);
			}
			else {
				cart = CartContent.DocumentToCart(doc);
				cart.appendToCart(ids_str);
			
				replaceCart(key,cart);
			}
		}
	}

	protected boolean delFromCart(String key, String ids_str) 
	{
		boolean status = true;
		
		// Look for in cache first before going to DB 
		CartContent cart = cart_map_.get(key);

		if (cart != null) {
			cart.removeFromCart(ids_str);
			replaceCart(key,cart);
		}
		else {
			// Retrieve from MongoDB
			Document doc = mongo_shoppingcart_col_.find(eq("_id", key)).first();

			if (doc == null) {
				logger.warning("Key: " + key + "not found");
				status = false;
			}
			else {
				cart = CartContent.DocumentToCart(doc);
				cart.removeFromCart(ids_str);

				replaceCart(key,cart);
			}
		}
		
		return status;
	}

	
	protected CartContent getCart(String key) 
	{
		CartContent cart = cart_map_.get(key);

		if (cart == null) {

			Document doc = mongo_shoppingcart_col_.find(eq("_id", key)).first();

			if (doc != null) {
				// Found it in mongoDB
				cart = CartContent.DocumentToCart(doc);
				
				// Re-populate the hashmap
				cart_map_.put(key, cart);
			}
		}

		return cart;
	}

	public void doAction(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException
	{
		response.setContentType("text/plain");
		response.setCharacterEncoding("UTF-8");

		String key  = request.getParameter("key");
		String mode = request.getParameter("mode");
		
		if ((key != null) && (mode != null)) {

			PrintWriter pw = response.getWriter();

			String ids_str = request.getParameter("ids");

			if (mode.equals("set")) {
				setCart(key,ids_str);
			}
			else if (mode.equals("get")) {
				CartContent cart = getCart(key);
				if (cart == null) {
					pw.append("");
				}
				else {
					String cart_json_str = cart.toJSON(key);					
					pw.append(cart_json_str);		
				}
			}
			else if (mode.equals("add-ids")) {
				addToCart(key,ids_str);
			}
			else if (mode.equals("del-ids")) {
				delFromCart(key,ids_str);
			}
			else {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'key' and/or 'mode' parameters to " + getHandle());
			}
		}
		else {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'key' and/or 'mode' parameters to " + getHandle());
		}
	}
}