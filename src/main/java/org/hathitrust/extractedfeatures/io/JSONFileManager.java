package org.hathitrust.extractedfeatures.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.jcs.JCS;
import org.apache.commons.jcs.access.exception.CacheException;
import org.apache.commons.jcs.access.CacheAccess;

import org.hathitrust.extractedfeatures.VolumeUtils;


public class JSONFileManager
{
	protected static Logger logger = Logger.getLogger(JSONFileManager.class.getName()); // org.hathitrust.extractedfeatures.io.
	
	protected static final String rsync_base = "data.analytics.hathitrust.org::features/";
        // When there was a server outage at Illinois, the following was used as a backup for the rsyc-server
        //protected static final String rsync_base = "magnolia.soic.indiana.edu::features/";
	    
	protected static Boolean uses_custom_tmpdir_ = null;
	
	protected File rsync_tmp_dir_;
	protected File zip_tmp_dir_;
	protected File local_pairtree_root_;

	protected final int DOWNLOAD_BUFFER_SIZE = 1024;

	protected static CacheAccess<String, String> id_cache_ = null;
	protected static JSONFileManager json_file_manager_ = null;
	
	
	private JSONFileManager(ServletConfig config)
	{	
		if (uses_custom_tmpdir_ == null) {
			// haven't previously checked for config parameter being set
			String java_io_tmpdir_str = config.getInitParameter("java.io.tmpdir");
			if ((java_io_tmpdir_str != null) && (!java_io_tmpdir_str.equals(""))) {
				logger.info("Servlet specifies custom java.io.tmpdir: " + java_io_tmpdir_str);
				File java_io_tmpdir = new File(java_io_tmpdir_str);
				
				boolean update_java_prop = true;
				
				if (!java_io_tmpdir.exists()) {
					boolean made_dir = java_io_tmpdir.mkdir();
					if (made_dir) {
						logger.info("Successfully created directory");
					}
					else {
						update_java_prop = false;
					}
				}
				else {
					logger.info("Checking directory: existsD");
					
				}
				
				if (update_java_prop) {
					logger.info("Updated Java property java.io.tmpdir to: " + java_io_tmpdir_str);
					System.setProperty("java.io.tmpdir", java_io_tmpdir_str);
					uses_custom_tmpdir_ = true;
				}
				else {
					uses_custom_tmpdir_ = false;
				}
			}
			else {
				uses_custom_tmpdir_ = false;
			}
		}
		
		try {
			rsync_tmp_dir_ = Files.createTempDirectory("rsync").toFile();
			logger.info("Created temporary directory for rsynced JSON files: " + rsync_tmp_dir_.getAbsolutePath());
			
			zip_tmp_dir_ = Files.createTempDirectory("zip").toFile();
			logger.info("Created temporary directory for zipped files: " + zip_tmp_dir_.getAbsolutePath());
			
		} catch (IOException e) {
			String message = String.format("Error creating temporary directcory: %s", e.getMessage());
			System.err.println(message);
		}

		String ptRoot = config.getInitParameter("pairtreeRoot");
		if (ptRoot != null) {
			local_pairtree_root_ = new File(ptRoot);
			if (!local_pairtree_root_.exists()) {
				System.err.println("Error: " + local_pairtree_root_ + " does not exist!");
			}
		}

		if (id_cache_ == null) {
			try {
				id_cache_ = JCS.getInstance( "idCache" );
			}
			catch ( CacheException e )
			{
				String message = String.format( "Problem initializing cache: %s", e.getMessage());
				System.err.println(message);
			}
		}
	}

	protected BufferedReader getBufferedReaderForCompressedFile(BufferedInputStream bis) 
			throws IOException, CompressorException 
	{
	    CompressorInputStream cis = new CompressorStreamFactory().createCompressorInputStream(bis);
	    BufferedReader br = new BufferedReader(new InputStreamReader(cis,"UTF8"));
	    return br;
	}

	protected BufferedWriter getBufferedWriterForCompressedFile(BufferedOutputStream bos) 
			throws IOException, CompressorException 
	{
	    CompressorOutputStream cos = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.BZIP2,bos);
	    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(cos,"UTF8"));
	    return bw;
	}
	
	public String readCompressedTextFile(File file)
	{
		StringBuilder sb = new StringBuilder();
		
		try {	
			FileInputStream fis = new FileInputStream(file);
			BufferedInputStream bis = new BufferedInputStream(fis);
			
			BufferedReader br = getBufferedReaderForCompressedFile(bis);

			int cp;
			while ((cp = br.read()) != -1) {
			    sb.append((char) cp);
			}
	
	        br.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return sb.toString();
	}
	
	public void writeCompressedTextFile(File file, String content)
	{
		try {	
			FileOutputStream fos = new FileOutputStream(file);
			BufferedOutputStream bos = new BufferedOutputStream(fos);
			
			BufferedWriter bw = getBufferedWriterForCompressedFile(bos);
			bw.write(content);
	        bw.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static JSONFileManager getInstance(ServletConfig config)
	{
		synchronized (JSONFileManager.class)
		{
			if (json_file_manager_ == null) {
				json_file_manager_ = new JSONFileManager(config);
			}
		}
		return json_file_manager_;
	}
	
	public File getFullZipFilename(String zip_filename_tail)
	{
		File tmp_full_zip_file = new File(zip_tmp_dir_, zip_filename_tail);
		
		return tmp_full_zip_file;
	}
	
	protected File doRsyncDownload(String full_json_filename) throws IOException 
	{
		String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);
		File tmp_full_json_file = new File(rsync_tmp_dir_, json_filename_tail);

		String json_content = id_cache_.get("json-id-" + json_filename_tail);
		
		if (json_content == null) {
			// Not in cache

		    logger.info("Did not find '" + json_filename_tail + "' in cache"); 

			// Usage of HTRC rsync server:
			//   rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
			
			Runtime runtime = Runtime.getRuntime();
			String[] rsync_command = {"rsync", "-av", rsync_base + full_json_filename, rsync_tmp_dir_.getPath()};

			try {
				Process proc = runtime.exec(rsync_command);
				int retCode = proc.waitFor();

				if (retCode != 0) {
					throw new Exception("rsync command failed with code " + retCode);
				}

				json_content = readCompressedTextFile(tmp_full_json_file);
				logger.info("doRsyncDownload() Storing '" + json_filename_tail + "' in cache"); 

				id_cache_.put("json-id-" + json_filename_tail, json_content);

				
			} catch (Exception e) {
				throw new IOException(e);
			}	
		}
		else {
			// Uncompressed version found in cache
			// => avoid retrieving via rsync, and dump local version directly (BZIP2 compressed) 
			// in tmp_dir as if it had been retrieved via rsync
			System.err.println("**** Using locally cached version!");
			writeCompressedTextFile(tmp_full_json_file,json_content);
		}
		
		return tmp_full_json_file;
	}


	public File fileOpen(String full_json_filename)
	{
		File file = null;
		if (local_pairtree_root_ != null) {
			// Access the file locally
			file = new File(local_pairtree_root_, full_json_filename);
		} else {
			// Work through the rsync server
			try {
				file = doRsyncDownload(full_json_filename);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return file;
	}

	public String getVolumeContent(String volume_id)
	{
		String full_json_filename = VolumeUtils.idToPairtreeFilename(volume_id);
		String json_filename_tail = VolumeUtils.full_filename_to_tail(full_json_filename);
		
		// Check cache first
		String json_content = id_cache_.get("json-id-" + json_filename_tail);
		
		if (json_content == null) {

			// Not in cache => work out if file is available locally, or need to go through rsync
			if (local_pairtree_root_ != null) {
				// Access the file locally
				File file = new File(local_pairtree_root_, full_json_filename);
				json_content = readCompressedTextFile(file);
			}
			else {
				// Work through rsync to get content
				
				// Usage of HTRC rsync server:
				//   rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
				
				Runtime runtime = Runtime.getRuntime();
				String[] rsync_command = {"rsync", "-av", rsync_base + full_json_filename, rsync_tmp_dir_.getPath()};

				try {
					Process proc = runtime.exec(rsync_command);
					int retCode = proc.waitFor();

					if (retCode != 0) {
						throw new Exception("rsync command failed with code " + retCode);
					}

					File tmp_full_json_file = new File(rsync_tmp_dir_, json_filename_tail);
					json_content = readCompressedTextFile(tmp_full_json_file);
			
					tmp_full_json_file.delete();
					
				} catch (Exception e) {
					e.printStackTrace();
					logger.warning("Rsync command run was: " + String.join(" ",rsync_command));
					json_content = null;
				}			

			}
			// Store in cache for next time
			logger.info("Storing '" + json_filename_tail + "' in cache"); 
			id_cache_.put("json-id-" + json_filename_tail, json_content);
		}
		else {
			logger.info("Retrieving '" + json_filename_tail + "' from cache"); 
		}
		return json_content;
	}
	
	public boolean usingRsync()
	{
		return local_pairtree_root_ == null;
	}

	
}
