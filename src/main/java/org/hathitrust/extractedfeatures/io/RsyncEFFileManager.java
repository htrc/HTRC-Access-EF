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
import java.util.HashMap;
import java.util.Map;
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


public class RsyncEFFileManager
{
	protected static Logger logger = Logger.getLogger(RsyncEFFileManager.class.getName());
	
	protected static String rsync_base = null;
	//protected static final String rsync_base = "data.analytics.hathitrust.org::features/";
    // When there was a server outage at Illinois, the following was used as a backup for the rsync-server
    // protected static final String rsync_base = "magnolia.soic.indiana.edu::features/";
	    
	protected static Boolean uses_custom_tmpdir_ = null;
	
	protected File local_pairtree_or_stubby_root_;
	protected File rsync_tmp_dir_;
	protected File for_download_tmp_dir_;

	protected final int DOWNLOAD_BUFFER_SIZE = 1024;

	protected static CacheAccess<String, String> id_cache_ = null;
	protected static RsyncEFFileManager rsyncef_file_manager_ = null;
	protected static Map<String,Process> rsyncef_jsonbz_downloads_in_progress_ = null;
	protected static Map<String,Integer> rsyncef_jsonbz_downloaded_refcount_   = null;
	
	private RsyncEFFileManager(ServletConfig config)
	{	
		synchronized (RsyncEFFileManager.class) {
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
		}
		
		try {
			rsync_tmp_dir_ = Files.createTempDirectory("rsync").toFile();
			logger.info("Created temporary directory for rsynced JSON files: " + rsync_tmp_dir_.getAbsolutePath());
			
			for_download_tmp_dir_ = Files.createTempDirectory("for-download").toFile();
			logger.info("Created temporary directory for download files: " + for_download_tmp_dir_.getAbsolutePath());
			
		} catch (IOException e) {
			String message = String.format("Error creating temporary directcory: %s", e.getMessage());
			System.err.println(message);
		}

		String ptRoot = config.getInitParameter("pairtreeRoot");
		if (ptRoot != null) {
			local_pairtree_or_stubby_root_ = new File(ptRoot);
			if (!local_pairtree_or_stubby_root_.exists()) {
				System.err.println("Error: " + local_pairtree_or_stubby_root_ + " does not exist!");
			}
		}
		else {
			// Using an rsync server
			
			String rsync_serverset = config.getInitParameter("ef.rsync.serverset");
			
			if (rsync_serverset == null) {
				//rsync_base = "data.analytics.hathitrust.org::features/";
				rsync_base = "queenpalm.ischool.illinois.edu::features-2020.03/";
			}
			else {
				rsync_base = rsync_serverset;
			}
			
			
		}
		synchronized (RsyncEFFileManager.class) {
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
		
			if (rsyncef_jsonbz_downloads_in_progress_ == null) {
				rsyncef_jsonbz_downloads_in_progress_ = new HashMap<String,Process>();
			}
			if (rsyncef_jsonbz_downloaded_refcount_ == null) {
				rsyncef_jsonbz_downloaded_refcount_ = new HashMap<String,Integer>();				
			}
		}
	}

	public static RsyncEFFileManager getInstance(ServletConfig config)
	{
		synchronized (RsyncEFFileManager.class)
		{
			if (rsyncef_file_manager_ == null) {
				rsyncef_file_manager_ = new RsyncEFFileManager(config);
			}
		}
		
		return rsyncef_file_manager_;
	}
	
	public boolean usingRsync()
	{
		return local_pairtree_or_stubby_root_ == null;
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

			// **** !!!! ****
			int cp;
			while ((cp = br.read()) != -1) {
			    sb.append((char) cp);
			}
	
	        br.close();
		} 
		catch (Exception e) {
			sb.setLength(0);
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
	
	public File getForDownloadFile(String filename_tail)
	{
		// Got a null point exception on this line from WebSocketResponse:228 // *******
		File tmp_stored_file = new File(for_download_tmp_dir_, filename_tail);
		
		return tmp_stored_file;
	}
	
	
	protected File doRsyncDownload(String pairtree_or_stubby_full_json_filename_bz) throws IOException
	{
		String json_filename_tail_bz = VolumeUtils.full_filename_to_tail(pairtree_or_stubby_full_json_filename_bz);
		File tmp_full_json_file_bz = new File(rsync_tmp_dir_, json_filename_tail_bz);

		String json_content = id_cache_.get("json-id-" + json_filename_tail_bz);
		
		if (json_content == null) {
			// Not in cache

		    //logger.info("Did not find '" + json_filename_tail_bz + "' in cache"); 

			// Usage of HTRC rsync server:
			//   rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
			
			Runtime runtime = Runtime.getRuntime();
			String[] rsync_command = {"rsync", "-av", rsync_base + pairtree_or_stubby_full_json_filename_bz, rsync_tmp_dir_.getPath()};

			try {
				Process proc = null;
				
				synchronized(rsyncef_jsonbz_downloads_in_progress_) {
					//System.err.println("<***> running rsync command to get: " + pairtree_or_stubby_full_json_filename_bz);
					proc = runtime.exec(rsync_command);
					rsyncef_jsonbz_downloads_in_progress_.put(pairtree_or_stubby_full_json_filename_bz, proc);
				}

				int retCode = proc.waitFor();
				//System.err.println("</***> completed rsync command to get: " + pairtree_or_stubby_full_json_filename_bz);
				
				synchronized(rsyncef_jsonbz_downloads_in_progress_) {
					rsyncef_jsonbz_downloads_in_progress_.remove(pairtree_or_stubby_full_json_filename_bz);
				}
				
				if (retCode != 0) {
					throw new IOException("rsync command to retrieve " + pairtree_or_stubby_full_json_filename_bz + " failed with code " + retCode);
					
				}
				
				json_content = readCompressedTextFile(tmp_full_json_file_bz);
				
				//logger.info("doRsyncDownload() Storing '" + json_filename_tail_bz + "' in cache"); 
				id_cache_.put("json-id-" + json_filename_tail_bz, json_content);

				
			} catch (InterruptedException e) {
				System.err.println("Rsync process interrupted");
				throw new IOException(e);
			}	
		}
		else {
			
			if (!tmp_full_json_file_bz.exists()) {
				// Local tmp file version has been deleted, but uncompressed version still in cache
				// => no need to retrieve rsync, as can write out local cached version directly (BZIP2 compressed) 
				// in tmp_dir, making it look as if it has been retrieved via rsync
				//System.err.println("**** Using locally cached version!");
				writeCompressedTextFile(tmp_full_json_file_bz,json_content);
			}
		}
		
		return tmp_full_json_file_bz;
	}


	public File fileOpen(String pairtree_or_stubby_full_json_filename_bz)
	{
		String thread_name = Thread.currentThread().getName();
		//System.err.println("**** [" + thread_name + "] RsyncEFFileManager::fileOpen() called: " + pairtree_or_stubby_full_json_filename_bz);
		
		File file = null;
		if (local_pairtree_or_stubby_root_ != null) {
			// Access the file locally
			file = new File(local_pairtree_or_stubby_root_, pairtree_or_stubby_full_json_filename_bz);
		} else {
			Process proc = null;
			synchronized(rsyncef_jsonbz_downloads_in_progress_) {
				//System.err.println("#### fileOpen(): Testing to see if rsync process already in play for: " + pairtree_or_stubby_full_json_filename_bz);
				proc = rsyncef_jsonbz_downloads_in_progress_.get(pairtree_or_stubby_full_json_filename_bz);
			}
			//System.err.println("####  [" + thread_name + "] fileOpen(): returned proccess object = " + proc);
			
			if (proc != null) {
				try {
					//System.err.println("####  [" + thread_name + "] fileOpen(): Away to wait for proc to complete for: " + pairtree_or_stubby_full_json_filename_bz);
					
					int ret_code = proc.waitFor();
					//System.err.println("#### fileOpen(): Proc now completed for: " + pairtree_or_stubby_full_json_filename_bz);

					// If ret_code == 0, then there will be a version of the file waiting for us
					// is the cache when doRsyncDownload() is called

					if (ret_code != 0) {
						System.err.println("Previously initiated rsync command for " + pairtree_or_stubby_full_json_filename_bz + " failed");
						System.err.println("Trying new invocation of rsync");
					}
				} 
				catch (InterruptedException e) {
					// No need to print any error message here, as this will have been
					// done by the initial call to doRsyncDownload() that initiated the rsync command
				}
			}

			// Work through the rsync server
			try {
				//System.err.println("####  [" + thread_name + "] fileOpen(): Away to call doRsyncDownload, where (if proc != null) the file should now be in cache: " + pairtree_or_stubby_full_json_filename_bz);

				file = doRsyncDownload(pairtree_or_stubby_full_json_filename_bz);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			synchronized(rsyncef_jsonbz_downloaded_refcount_) {
				if (rsyncef_jsonbz_downloaded_refcount_.containsKey(pairtree_or_stubby_full_json_filename_bz))
				{
					int ref_count = rsyncef_jsonbz_downloaded_refcount_.get(pairtree_or_stubby_full_json_filename_bz);
					ref_count++;
					//System.err.println("####  [" + thread_name + "] fileOpen(): " + pairtree_or_stubby_full_json_filename_bz + " ref_count now = " + ref_count);

					rsyncef_jsonbz_downloaded_refcount_.put(pairtree_or_stubby_full_json_filename_bz,ref_count);
				}
				else {
					//System.err.println("####  [" + thread_name + "] fileOpen(): " + pairtree_or_stubby_full_json_filename_bz + " ref_count set = " + 1);

					rsyncef_jsonbz_downloaded_refcount_.put(pairtree_or_stubby_full_json_filename_bz,1);
				}
			}
		}

		return file;
	}
	
	public void fileClose(String pairtree_or_stubby_full_json_filename_bz)
	{
		String thread_name = Thread.currentThread().getName();
		//System.err.println("**** [" + thread_name + "] RsyncEFFileManager::fileClose() called: " + pairtree_or_stubby_full_json_filename_bz);
		
		if (local_pairtree_or_stubby_root_ == null) {
			
			synchronized(rsyncef_jsonbz_downloaded_refcount_) {
				if (rsyncef_jsonbz_downloaded_refcount_.containsKey(pairtree_or_stubby_full_json_filename_bz))
				{
					int ref_count = rsyncef_jsonbz_downloaded_refcount_.get(pairtree_or_stubby_full_json_filename_bz);
					//System.err.println("####  [" + thread_name + "] fileClose(): " + pairtree_or_stubby_full_json_filename_bz + " ref_count before dec = " + ref_count);

					ref_count--;
					if (ref_count > 0) {
						rsyncef_jsonbz_downloaded_refcount_.put(pairtree_or_stubby_full_json_filename_bz,ref_count);
					}
					else {
						// remove the file retrieved over rsync
						String json_filename_tail_bz = VolumeUtils.full_filename_to_tail(pairtree_or_stubby_full_json_filename_bz);
						File file = new File(rsync_tmp_dir_,json_filename_tail_bz);
						//System.err.println("*** fileClose() looking to delete: " + file.getAbsolutePath());
						if (file.exists()) {
							boolean removed_file = file.delete();
							if (!removed_file) {
								System.err.println("Error: failed to remove rsync downloaded file " + file.getAbsolutePath() + " in WebSocketResponse::fileClose()");
							}
						}
						rsyncef_jsonbz_downloaded_refcount_.remove(pairtree_or_stubby_full_json_filename_bz);
					}
				}
			}
		}
	}
	
	/*
	public String getVolumeContent(String volume_id)
	{
		String pairtree_full_json_filename = VolumeUtils.idToPairtreeFilename(volume_id);
		String json_filename_tail = VolumeUtils.full_filename_to_tail(pairtree_full_json_filename);
		
		// Check cache first
		String json_content = id_cache_.get("json-id-" + json_filename_tail);
		
		if (json_content == null) {

			// Not in cache => work out if file is available locally, or need to go through rsync
			if (local_pairtree_root_ != null) {
				// Access the file locally
				File file = new File(local_pairtree_root_, pairtree_full_json_filename);
				json_content = readCompressedTextFile(file);
			}
			else {
				// Work through rsync to get content
				
				// Usage of HTRC rsync server:
				//   rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
				
				Runtime runtime = Runtime.getRuntime();
				String[] rsync_command = {"rsync", "-av", rsync_base + pairtree_full_json_filename, rsync_tmp_dir_.getPath()};

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
//		else {
//			logger.info("Retrieving '" + json_filename_tail + "' from cache"); 
//		}
		
		return json_content;
	}
*/
	
}
