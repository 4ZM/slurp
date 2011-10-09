/**
 * Copyright (c) 2011 Anders Sundman <anders@4zm.org>
 * 
 * This file is part of SLURP.
 * 
 * SLURP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * SLURP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with SLURP.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sparvnastet.nfc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.os.Environment;

public class MifareKeyChain {

	public final static int KEY_SIZE = 6;
	
	private final static int A_KEY = 0;
	private final static int B_KEY = 1;
	
	private byte[][][] mKeys;

	public MifareKeyChain(int sectors) {
		assert(sectors > 0);
		mKeys = new byte[sectors][2][];
	}

	private MifareKeyChain(byte[][][] keyData) {
		assert(keyData != null);
		mKeys = keyData;
	}

	public int getSectorCount() { return mKeys.length; }
	public byte[] getKeyA(int sector) { return mKeys[sector][A_KEY]; }
	public byte[] getKeyB(int sector) { return mKeys[sector][B_KEY]; }
	public void setKeyA(int sector, byte[] key) { 
		assert(key == null || key.length == KEY_SIZE); 
		mKeys[sector][A_KEY] = key; 
	}
	public void setKeyB(int sector, byte[] key) { 
		assert(key == null || key.length == KEY_SIZE);
		mKeys[sector][B_KEY] = key; 
	}
	
	/**
	 * Factory method. Read keys from a file on the external 
	 * storage and create a key chain instance.
	 * 
	 * The file format (binary) is:
	 * [Sector 0 A key|6 Bytes]
	 * [Sector 0 B key|6 Bytes]
	 * [Sector 1 A key|6 Bytes]
	 * ...
	 * [Sector N B key|6 Bytes]
	 * 
	 * @param keyFile 
	 * @return 
	 * @throws IOException
	 */
	public static MifareKeyChain LoadKeys(File keyFile) throws IOException {
		assert(keyFile != null);
		
		String state = Environment.getExternalStorageState();

		if (!Environment.MEDIA_MOUNTED.equals(state) && 
				!Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
			throw new IOException("Can't access external storage");

		if (!keyFile.exists())
			return null;

		InputStream is = new FileInputStream(keyFile);
		int totalBytes = is.available();

		if ((totalBytes % (2 * KEY_SIZE)) != 0)
			throw new IOException("Invalid format of keyfile");

		int totalSectors = totalBytes / (2 * KEY_SIZE);
		byte[][][] keys = new byte[totalSectors][2][];
		for (int sector = 0; sector < totalSectors; ++sector) {
			keys[sector][0] = new byte[KEY_SIZE];
			is.read(keys[sector][A_KEY]); 
			keys[sector][0] = new byte[KEY_SIZE];
			is.read(keys[sector][B_KEY]); 
		}

		is.close();
		
		return new MifareKeyChain(keys);
	}

	public void StoreKeys(File keyFile) throws IOException {
		if (mKeys == null)
			return;

		String state = Environment.getExternalStorageState();

		if (!Environment.MEDIA_MOUNTED.equals(state)
				|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
			throw new IOException("Can't access external storage. Write access required.");

		OutputStream os = new FileOutputStream(keyFile);
		
		byte[][][] keys = mKeys; // Local variable optimization
		for (int sector = 0; sector < keys.length; ++sector) {
			os.write(keys[sector][A_KEY]); 
			os.write(keys[sector][B_KEY]); 
		}

		os.close();
	}

}
