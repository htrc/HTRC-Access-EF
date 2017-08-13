package org.hathitrust.extractedfeatures;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * Servlet implementation class VolumeCheck
 */
public class VolumeCheck extends HttpServlet {

  protected static final String file_ext = ".json.bz2";
  protected static final String ht_col_url = "https://babel.hathitrust.org/cgi/mb";
  private static final long serialVersionUID = 1L;
  protected static int HASHMAP_INIT_SIZE = 13800000;
  protected static HashMap<String, Boolean> id_check_ = null;
  protected static File tmpDir;
  protected static File pairtreeRoot;

  protected final int BUFFER_SIZE = 1024;
    
  public VolumeCheck() {
  }

  protected static String full_filename_to_tail(String full_filename) {
    String filename_tail = full_filename.substring(full_filename.lastIndexOf("/") + 1);
    return filename_tail;
  }

  protected static String filename_tail_to_id(String filename_tail) {
    String id = null;
    if (filename_tail.endsWith(file_ext)) {
      id = filename_tail.substring(0, filename_tail.lastIndexOf(file_ext));
    } else {
      id = filename_tail;
    }

    id = id.replaceAll(",", ".").replaceAll("\\+", ":").replaceAll("=", "/");

    return id;
  }

  protected static String id_to_pairtree_filename(String id) {
    // Example :-
    //   id: miun.adx6300.0001.001
    //   pairtree filename: miun/pairtree_root/ad/x6/30/0,/00/01/,0/01/adx6300,0001,001/miun.adx6300,0001,001.json.bz2

    // 1. Map 'difficult' chars:
    //   . => ,
    //   : => +
    //   / => =

    // 2. Process resulting string:
    //   split on first dot
    //   add "pairtree_root"
    //   then split everything else 2 chars at a time

    // 3. Finally add in the (safely transformed) id:
    //   append directory that is prefix-removed id (transformed to be safe)
    //   further append 'id-safe'.json.bz

    int id_dot_pos = id.indexOf(".");
    String id_prefix = id.substring(0, id_dot_pos);
    String id_tail = id.substring(id_dot_pos + 1);
    String id_tail_safe = id_tail.replaceAll("\\.", ",").replaceAll(":", "+").replaceAll("/", "=");

    String[] pairs = id_tail_safe.split("(?<=\\G..)");
    String joined_pairs = String.join("/", pairs);

    String id_safe = id_prefix + "." + id_tail_safe;
    String main_dir = id_prefix + "/pairtree_root/" + joined_pairs;
    String filename = main_dir + "/" + id_tail_safe + "/" + id_safe + file_ext;

    return filename;
  }

  protected void storeIDs(BufferedReader br) {
    long line_num = 1;
    String line;

    try {

      System.err.print("Loading hashmap: ");
      while ((line = br.readLine()) != null) {

        String full_json_filename = line;
        String json_filename_tail = full_filename_to_tail(full_json_filename);
        String id = filename_tail_to_id(json_filename_tail);

        id_check_.put(id, true);

        if ((line_num % 100000) == 0) {
          System.err.print(".");
        }
        line_num++;

      }
      System.err.println(" => done.");
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
    
  /**
   * @see Servlet#init(ServletConfig)
   */
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    if (id_check_ == null) {
      id_check_ = new HashMap<String, Boolean>(HASHMAP_INIT_SIZE);

      String htrc_list_file = "htrc-ef-all-files.txt";
      ServletContext servletContext = getServletContext();
      System.err.println(servletContext);
      InputStream is = servletContext.getResourceAsStream("/WEB-INF/classes/" + htrc_list_file);

      try {
        System.err.println("INFO: Loading in volume IDS: " + htrc_list_file);

        InputStreamReader isr = new InputStreamReader(is, "UTF-8");
        BufferedReader br = new BufferedReader(isr);

        storeIDs(br);
        br.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    if (tmpDir == null) {
      try {
        tmpDir = Files.createTempDirectory("rsync").toFile();
      } catch (IOException e) {
        throw new ServletException("Could not create temp folder", e);
      }

    }

    String ptRoot = config.getInitParameter("pairtreeRoot");
    if (ptRoot != null) {
      pairtreeRoot = new File(ptRoot);
      if (!pairtreeRoot.exists()) {
        throw new ServletException(pairtreeRoot + " does not exist!");
      }
    }
  }

  protected File doRsyncDownload(String full_json_filename) throws IOException {
    String json_filename_tail = full_filename_to_tail(full_json_filename);

    Runtime runtime = Runtime.getRuntime();
    String[] rsync_command = {"rsync", "-av",
        "data.analytics.hathitrust.org::features/" + full_json_filename, tmpDir.getPath()};

    try {
      Process proc = runtime.exec(rsync_command);
      int retCode = proc.waitFor();

      if (retCode != 0) {
        throw new Exception("rsync command failed with code " + retCode);
      }

      return new File(tmpDir, json_filename_tail);
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  protected void doCollectionToWorkset(HttpServletResponse response, String col_title,
      String c, String a, String format) throws IOException {
    String post_url_params = "c=" + c + "&a=" + a + "&format=" + format;

    byte[] post_data = post_url_params.getBytes(StandardCharsets.UTF_8);
    int post_data_len = post_data.length;

    try {

      URL post_url = new URL(ht_col_url);
      HttpURLConnection conn = (HttpURLConnection) post_url.openConnection();
      conn.setDoOutput(true);
      conn.setInstanceFollowRedirects(false);
      conn.setRequestMethod("POST");
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setRequestProperty("charset", "utf-8");
      conn.setRequestProperty("Content-Length", Integer.toString(post_data_len));
      conn.setUseCaches(false);

      try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
        dos.write(post_data);
      }
      // try-resource auto-closes stream

      InputStream is = conn.getInputStream();
      InputStreamReader isr = new InputStreamReader(is);
      BufferedReader reader = new BufferedReader(isr);

      StringBuilder workset_friendly_sb = new StringBuilder();
      StringBuilder workset_unfriendly_sb = new StringBuilder();

      String line = null;
      int ci = 0;
      while ((line = reader.readLine()) != null) {
        if (ci == 0) {
          workset_friendly_sb.append("#" + line + "\n");
        } else {
          int first_tab_pos = line.indexOf("\t");
          String id = (first_tab_pos > 0) ? line.substring(0, first_tab_pos) : line;

          if (id_check_.containsKey(id)) {
            workset_friendly_sb.append(line + "\n");
          } else {
            workset_unfriendly_sb.append("#" + line + "\n");
          }
        }

        ci++;
      }

      String col_title_filename = col_title + ".txt";
      response.setContentType("text/plain");
      response
          .setHeader("Content-Disposition", "attachment; filename=\"" + col_title_filename + "\"");

      PrintWriter pw = response.getWriter();
      pw.append(workset_friendly_sb.toString());

      if (workset_unfriendly_sb.length() > 0) {
        pw.append(
            "## The following volumes were listed in the HT collection, but have been omitted as they are not in the HTRC Extracted Feature dataset\n");
        pw.append(workset_unfriendly_sb.toString());
      }
      pw.flush();
    } catch (Exception e) {
      e.printStackTrace();
      response.sendError(HttpServletResponse.SC_BAD_REQUEST,
          "Failed to convert HT collection to HTRC workset");
    }

  }

  protected File json_pairtree_as_local_file(String full_json_filename)
  {
    File file = null;
    if (pairtreeRoot != null) {
      // Access the file locally
      file = new File(pairtreeRoot, full_json_filename);
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
	  
  /**
   * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
   */
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String cgi_ids = request.getParameter("ids");
    String cgi_id = request.getParameter("id");
    String cgi_download_id = request.getParameter("download-id");
    String cgi_download_ids = request.getParameter("download-ids");
    String cgi_convert_col = request.getParameter("convert-col");

    // if cgi_id in play then upgrade to cgi_ids (one item in it) to simplify later code
    if (cgi_ids == null) {
      if (cgi_id != null) {
	cgi_ids = cgi_id;
      }
    }

    // if cgi_id in play then upgrade to cgi_ids (one item in it) to simplify later code
    if (cgi_download_ids == null) {
      if (cgi_download_id != null) {
	cgi_download_ids = cgi_download_id;
      }
    }

    if (cgi_ids != null) {
      response.setContentType("application/json");
      PrintWriter pw = response.getWriter();

      String[] ids = cgi_ids.split(",");
      int ids_len = ids.length;

      pw.append("{");

      for (int i = 0; i < ids_len; i++) {
        String id = ids[i];

        boolean exists = id_check_.containsKey(id);

        if (i > 0) {
          pw.append(",");
        }
        pw.append("\"" + id + "\":" + exists);
      }
      pw.append("}");

    }
    /*
    else if (cgi_id != null) {
      response.setContentType("application/json");
      PrintWriter pw = response.getWriter();

      String id = cgi_id;
      boolean exists = id_check_.containsKey(id);
      pw.append("{'" + id + "':" + exists + "}");
      } */
    else if (cgi_download_ids != null) {
      
      String[] download_ids = cgi_download_ids.split(",");
      int download_ids_len = download_ids.length;

      ZipOutputStream zbros = null;
      OutputStream download_os = null;

      boolean output_as_zip = (download_ids_len > 1);
      
      if (output_as_zip) {
	// Output needs to be zipped up
	response.setContentType("application/zip");
	response.setHeader("Content-Disposition","attachment; filename=htrc-ef-export.zip");

	OutputStream ros = response.getOutputStream();
	BufferedOutputStream bros = new BufferedOutputStream(ros);
	zbros = new ZipOutputStream(bros);

	download_os = zbros;
      }
      
      for (int i=0; i<download_ids_len; i++) {
      
	String download_id = download_ids[i];
	boolean exists = id_check_.containsKey(download_id);
	if (!exists) {
	  // Error
	  response.sendError(HttpServletResponse.SC_BAD_REQUEST,
			     "The requested volume id does not exist.");
	  break;	  
	}
	else {
	  
	  // rsync -av data.analytics.hathitrust.org::features/{PATH-TO-FILE} .
	  String full_json_filename = id_to_pairtree_filename(download_id);
	  File file = json_pairtree_as_local_file(full_json_filename);
	  
	  if (file == null) {
	    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Rsync failed");
	    break;
	  }
	  else {
	    FileInputStream fis = new FileInputStream(file);
	    BufferedInputStream bis = new BufferedInputStream(fis);
	    String json_filename_tail = full_filename_to_tail(full_json_filename);

	    if (output_as_zip) {
	      ZipEntry zipentry = zipentry = new ZipEntry(json_filename_tail);
	      zbros.putNextEntry(zipentry);
	    }
	    else {
	      response.setContentType("application/x-bzip2");
	      response.setHeader("Content-Disposition",
				 "attachment; filename=\"" + json_filename_tail + "\"");
	      
	      OutputStream ros = response.getOutputStream();
	      download_os = new BufferedOutputStream(ros);
	    }		

	    
	    byte[] buf = new byte[1024];
	    
	    while (true) {
	      int num_bytes = bis.read(buf);
	      if (num_bytes == -1) {
		break;
	      }
	      download_os.write(buf, 0, num_bytes);
	    }

	    bis.close();	    
	    if (output_as_zip) {
		zbros.closeEntry();
	    }
	    else {
	      download_os.close();
	    }

	    if (pairtreeRoot == null) {
	      // remove file retrieved over rsync
	      file.delete();
	    }
	  }
        }
      }

      if (output_as_zip) {
	download_os.close();
      }

    } else if (cgi_convert_col != null) {

      String cgi_col_title = request.getParameter("col-title");
      String cgi_a = request.getParameter("a");
      String cgi_format = request.getParameter("format");

      if ((cgi_a == null) || (cgi_format == null)) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST,
            "Malformed arguments.  Need 'a', and 'format'");
      } else {
        if (cgi_col_title == null) {
          cgi_col_title = "htrc-workset-" + cgi_convert_col;
        }

        doCollectionToWorkset(response, cgi_col_title, cgi_convert_col, cgi_a, cgi_format);
      }

    } else {
      PrintWriter pw = response.getWriter();

      pw.append("General Info: Number of HTRC Volumes in check-list = " + id_check_.size());

    }
    //pw.close();

  }

  /**
   * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
   */
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    doGet(request, response);
  }

}
