package org.hathitrust.extractedfeatures.action;


import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.bson.Document;
import org.hathitrust.extractedfeatures.CartContent;
import org.hathitrust.extractedfeatures.io.FlexiResponse;

import static com.mongodb.client.model.Filters.*;

import com.mongodb.client.MongoCollection;

public class ShoppingcartAction extends IdMongoDBAction
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
							+"                    mode=set|add-ids|del-ids|get|del\n",
							"Further parameter: 'ids' (used with 'set', 'add-ids' and 'del-ids')\n"
									+"Returns:           'status ok' for 'set', 'add-ids', 'del-ids' and 'del'; returns ids list for 'get'"
			};

		return mess;
	}

	public ShoppingcartAction(ServletContext context, ServletConfig config) 
	{
		super(context,config);

		cart_map_ = new HashMap<String, CartContent>();

		if (mongo_shoppingcart_col_ == null) {
			if (mongo_state_ == MongoDBState.Connected) {
				// The following will create the collection if it didn't already exist
				mongo_shoppingcart_col_ = mongo_db_.getCollection("shoppingcart");
			}
		}

	}

	public  boolean isOperational()
	{
		return mongo_shoppingcart_col_ != null;
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

	protected boolean delCart(String key) 
	{
		boolean status = true;
		
		// Remove it from DB
		Document doc = mongo_shoppingcart_col_.find(eq("_id", key)).first();
		if (doc != null) {
			// Found it in mongoDB
			
			// Remove it from the DB
			mongo_shoppingcart_col_.deleteOne(doc);
		}
		else {
			// Failed to find the cart key in DB
			status = false;
		}
		
		// Remove it from cache (even if not found in DB, could still be handing around cache)
		CartContent cached_cart = cart_map_.get(key);

		if (cached_cart != null) {
			cart_map_.remove(key);
		}

		
		return status;
	}
	
	public void doAction(Map<String,List<String>> param_map, FlexiResponse flexi_response) 
			throws ServletException, IOException
	{
		flexi_response.setContentType("text/plain");
		flexi_response.setCharacterEncoding("UTF-8");

		String key  = getParameter(param_map,"key");
		String mode = getParameter(param_map,"mode");
		
		if ((key != null) && (mode != null)) {

			String ids_str = getParameter(param_map,"ids");

			if (mode.equals("set")) {
				setCart(key,ids_str);
			}
			else if (mode.equals("get") || mode.equals("get-silentfail")) {
				CartContent cart = getCart(key);
				if (cart == null) {
				    if (mode.equals("get")) {
				    	flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No shopping-cart entry for key="+key);
				    }
				    else {
				    	// get-silentfail
				    	flexi_response.append("");
				    }

				}
				else {
					String cart_json_str = cart.toJSON(key);					
					flexi_response.append(cart_json_str);		
				}
			}
			else if (mode.equals("add-ids")) {
				addToCart(key,ids_str);
			}
			else if (mode.equals("del-ids")) {
				delFromCart(key,ids_str);
			}
			else if (mode.equals("del")) {
				flexi_response.append("Delete key, status = "  + delCart(key));
			}
			else {
				flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unrecognized 'mode' parameter value '" + mode + "' to " + getHandle());
			}
		}
		else {
		    if ((key == null) && (mode == null)) {
		    	flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'key' and 'mode' parameters to " + getHandle());
		    }
		    else {
			if (mode == null) {
				flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'mode' parameters to " + getHandle());
			}
			else {
				flexi_response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing 'key' parameters to " + getHandle());
			}
			
		    }
		}
	}
}
