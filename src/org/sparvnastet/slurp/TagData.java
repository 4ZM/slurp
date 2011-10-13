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

package org.sparvnastet.slurp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * This class represents the data of a MifareClassic tag.
 */
public class TagData implements Parcelable {
    private byte[][][] mData;

    public TagData(int sectors) {
        assert (sectors > 0);
        mData = new byte[sectors][][];
    }

    public int getSectorCount() {
        return mData.length;
    }

    public int getBlockInSectorCount(int sector) {
        assert (sector >= 0 && sector < mData.length);
        return mData[sector] == null ? 0 : mData[sector].length;
    }

    public void setSector(int sector, byte[][] data) {
        assert (sector >= 0 && sector < mData.length);
        mData[sector] = data;
    }

    public byte[][] getSector(int sector) {
        assert (sector >= 0 && sector < mData.length);
        return mData[sector];
    }

    /**
     * Save the tag data to the external storage.
     *
     * @param directory
     * @throws IOException
     */
    public void saveData(File directory) throws IOException {
        assert (isDataComplete());

        String state = Environment.getExternalStorageState();

        if (!Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
            throw new IOException("Storage medium not writable");

        StringBuilder sb = new StringBuilder();
        sb.append(DataFormater.bytesToString(mData[0][0]).replace(" ", ""));
        sb.append(".");

        final String DATE_FORMAT = "yyyyMMdd.HHmmss";
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        sb.append(sdf.format(cal.getTime()));
        sb.append(".card");

        String fileName = sb.toString();
        Log.i(SLURPActivity.LOGTAG, "Trying to safe data to: " + fileName);

        File dataFile = new File(directory, fileName);

        OutputStream os = new FileOutputStream(dataFile);

        byte[][][] data = mData; // Local variable optimization
        int totalBytes = 0;
        for (byte[][] sector : data) {
            for (byte[] block : sector) {
                os.write(block);
                totalBytes += block.length;
            }
        }

        // Pad up to 4k size (for nfc-mfclassic compatibility)
        assert (4096 - totalBytes >= 0);
        os.write(new byte[4096 - totalBytes]);

        os.close();
    }

    /**
     * @return true if all the sectors contains all valid (not null) blocks.
     */
    public boolean isDataComplete() {
        for (byte[][] sector : mData) {
            if (sector == null)
                return false;
            for (byte[] block : sector) {
                if (block == null)
                    return false;
            }
        }
        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mData.length);
        for (byte[][] sector : mData) {
            if (sector == null)
                dest.writeInt(0);
            else {
                dest.writeInt(sector.length);
                for (byte[] block : sector) {
                    if (block == null)
                        dest.writeInt(0);
                    else {
                        dest.writeInt(block.length);
                        dest.writeByteArray(block);
                    }
                }
            }
        }
    }

    public static final Parcelable.Creator<TagData> CREATOR = new Parcelable.Creator<TagData>() {
        public TagData createFromParcel(Parcel in) {
            int sectors = in.readInt();
            TagData tag = new TagData(sectors);

            for (int i = 0; i < sectors; ++i) {
                int blocks = in.readInt();
                byte[][] sectorData = new byte[blocks][];
                for (int j = 0; j < blocks; ++j) {
                    int blockLength = in.readInt();
                    if (blockLength > 0) {
                        sectorData[j] = in.createByteArray();
                    }
                    tag.setSector(i, sectorData);
                }
            }

            return tag;
        }

        public TagData[] newArray(int size) {
            return new TagData[size];
        }
    };

}
