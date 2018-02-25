package org.hathitrust.extractedfeatures.io;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

public class FileUtils
{
	protected static Logger logger = Logger.getLogger(FileUtils.class.getName());

	// Based on: http://stackoverflow.com/questions/453018/number-of-lines-in-a-file-in-java
	
	public static long countLines(BufferedInputStream bis) throws IOException 
	{
		try {
			byte[] c = new byte[1024];
			long count = 0;
			int readChars = 0;
			boolean empty = true;
			while ((readChars = bis.read(c)) != -1) {
				empty = false;
				for (int i = 0; i < readChars; ++i) {
					if (c[i] == '\n') {
						++count;
					}
				}
			}
			return (count == 0 && !empty) ? 1 : count;
		} 
		finally {
			bis.close();
		}
	}
	public static long countLines(String filename) throws IOException 
	{
		BufferedInputStream bis = new BufferedInputStream(new FileInputStream(filename));

		return countLines(bis);
	}
}
