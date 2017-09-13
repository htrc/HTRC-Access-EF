package org.hathitrust.extractedfeatures;

public class VolumeUtils 
{
	protected static final String file_ext = ".json.bz2";

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

	public static String id_to_pairtree_filename(String id) {
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






}
