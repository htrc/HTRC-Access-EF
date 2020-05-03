package org.hathitrust.extractedfeatures;

import java.util.ArrayList;

public class VolumeUtils 
{
	public enum EFRsyncFormatEnum { pairtree, stubby }
	
	protected static final String file_ext = ".json.bz2";

	public static EFRsyncFormatEnum EFRsyncFormat = null;
	
	private VolumeUtils() {
	}

	public static String full_filename_to_tail(String full_filename) {
		String filename_tail = full_filename.substring(full_filename.lastIndexOf("/") + 1);
		return filename_tail;
	}

	public static String filename_tail_to_id(String filename_tail) {
		String id = null;
		if (filename_tail.endsWith(file_ext)) {
			id = filename_tail.substring(0, filename_tail.lastIndexOf(file_ext));
		} else {
			id = filename_tail;
		}

		id = id.replaceAll(",", ".").replaceAll("\\+", ":").replaceAll("=", "/");

		return id;
	}

	protected static String idToPairtreeFilename(String id) {
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


	protected static String idToStubbyFilename(String id) {
		// Example :-
		//   id: miun.adx6300.0001.001
		//   stubby filename: miun/a600,1/miun.adx6300,0001,001.json.bz2

		
		// 1. Map 'difficult' chars:
		//   . => ,
		//   : => +
		//   / => =

		// 2. Process resulting string:
		//   split on first dot
		//   then extract every third character of the tail, starting at pos 0 

		// 3. Finally directory concat the prefix, every_third char (safely transformed) and id-safe:
		//   further append 'id-safe'.json.bz

		int id_dot_pos = id.indexOf(".");
		String id_prefix = id.substring(0, id_dot_pos);
		String id_tail = id.substring(id_dot_pos + 1);
		String id_tail_safe = id_tail.replaceAll("\\.", ",").replaceAll(":", "+").replaceAll("/", "=");

		StringBuffer every_third = new StringBuffer();
		
		for (int i=0; i<id_tail_safe.length(); i+=3) {
			every_third.append(id_tail_safe.charAt(i));
		}
		
		String id_safe = id_prefix + "." + id_tail_safe;
		String main_dir = id_prefix + "/" + every_third;
		String filename = main_dir + "/" + id_safe + file_ext;

		return filename;
	}

	public static String idToRsyncFilename(String id) 
	{
		String rsync_filename = null;
		
		if (EFRsyncFormat == EFRsyncFormatEnum.pairtree) {
			rsync_filename = idToPairtreeFilename(id);
		}
		else {
			rsync_filename = idToStubbyFilename(id);
		}
		
		return rsync_filename;
	}

}
