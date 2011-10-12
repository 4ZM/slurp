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

import java.io.IOException;

import android.app.ProgressDialog;
import android.nfc.tech.MifareClassic;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

public class ReadTagTask extends AsyncTask<MifareClassic, Integer, byte[][][]> {

    private ProgressDialog mProgressDialog;
    SLURPActivity mActivity;
    MifareKeyChain mKeyChain;

    public ReadTagTask(SLURPActivity activity, MifareKeyChain keyChain) {
        assert (activity != null && keyChain != null);
        mActivity = activity;
        mKeyChain = keyChain;
    }

    @Override
    protected void onPreExecute() {
        mActivity.setProgressBarIndeterminateVisibility(true);

        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setMessage("Reading tag...");
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    @Override
    protected byte[][][] doInBackground(MifareClassic... tagParam) {
        if (tagParam == null || tagParam.length != 1)
            return null;

        MifareClassic tag = tagParam[0];
        Log.i(SLURPActivity.LOGTAG, "ReadTagTask: doInBackground");
        try {
            tag.connect();

            int sectorCount = tag.getSectorCount();
            byte[][][] data = new byte[sectorCount][][];

            for (int i = 0; i < sectorCount; ++i) {
                data[i] = readSector(tag, i, mKeyChain.getKeyA(i), mKeyChain.getKeyB(i));
                publishProgress((100 * (i + 1)) / sectorCount);
            }

            tag.close();

            return data;
        } catch (IOException e) {
            Log.e(SLURPActivity.LOGTAG, "ReadTagTask: Auth IOException");
            return null;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        mProgressDialog.setProgress(progress[0]);
        Log.i(SLURPActivity.LOGTAG, "TestKeysTask: progress update " + progress[0]);
    }

    @Override
    protected void onPostExecute(byte[][][] data) {
        Log.i(SLURPActivity.LOGTAG, "ReadTagTask: onPostExecute");

        mActivity.setProgressBarIndeterminateVisibility(false);
        mProgressDialog.dismiss();

        if (data == null)
            Toast.makeText(mActivity, "Couldn't read data", Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(mActivity, "Data Read", Toast.LENGTH_SHORT).show();

        mActivity.setTagData(data);
    }

    // TODO This function should look at the access ctrl bits to determine
    // what keys to use.
    private byte[][] readSector(MifareClassic tag, int sector, byte[] keyA, byte[] keyB) throws IOException {
        byte[][] data = new byte[tag.getBlockCountInSector(sector)][];

        Log.i(SLURPActivity.LOGTAG, "ReadTagTask");
        boolean res = keyA != null && tag.authenticateSectorWithKeyA(sector, keyA);

        if (res)
            Log.i(SLURPActivity.LOGTAG, "Use Key A");

        if (!res && keyB != null) {
            Log.i(SLURPActivity.LOGTAG, "Use Key A");
            res = tag.authenticateSectorWithKeyB(sector, keyB);
        }

        if (!res)
            throw new IOException("READ ERROR - can't auth");

        int blocks = tag.getBlockCountInSector(sector);
        for (int i = 0; i < blocks; ++i)
            data[i] = tag.readBlock(SLURPActivity.getBlockIndex(tag, sector, i));

        // We might not have read access to the access ctrl block.
        // just fill in the data from our known keys.
        for (int i = 0; i < MifareKeyChain.KEY_SIZE; ++i)
            data[blocks - 1][i] = keyA[i];

        for (int i = 0; i < MifareKeyChain.KEY_SIZE; ++i)
            data[blocks - 1][i + 10] = keyB[i];

        return data;
    }

}