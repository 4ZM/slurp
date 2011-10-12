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

public class FindKeysTask extends AsyncTask<MifareClassic, Integer, MifareKeyChain> {
    private enum SECTOR_KEY {
        KEY_A, KEY_B
    };

    private MifareClassic mTag;
    private int mSectorCount;
    private byte[][] mDefaultKeys;

    ProgressDialog mProgressDialog;
    SLURPActivity mActivity;

    public FindKeysTask(SLURPActivity activity) {
        assert (activity != null);
        mActivity = activity;
        mDefaultKeys = mActivity.getDefaultKeys();
    }

    @Override
    protected void onPreExecute() {
        mActivity.setProgressBarIndeterminateVisibility(true);

        mProgressDialog = new ProgressDialog(mActivity);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setTitle("Trying keys...");
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();
    }

    @Override
    protected MifareKeyChain doInBackground(MifareClassic... tagParam) {
        if (tagParam == null || tagParam.length != 1)
            return null;

        mTag = tagParam[0];

        Log.i(SLURPActivity.LOGTAG, "TestKeysTask: doInBackground");
        try {
            mTag.connect();

            mSectorCount = mTag.getSectorCount();
            MifareKeyChain keyChain = new MifareKeyChain(mSectorCount);

            byte[] keyA, keyB;
            for (int i = 0; i < mSectorCount; ++i) {
                publishProgress(i);

                keyA = probeKey(mTag, i, SECTOR_KEY.KEY_A);
                keyB = probeKey(mTag, i, SECTOR_KEY.KEY_B);
                if (keyA == null || keyB == null) // Require both keys
                    return null;

                keyChain.setKeyA(i, keyA);
                keyChain.setKeyB(i, keyB);
            }

            mTag.close();

            return keyChain;
        } catch (IOException e) {
            Log.e(SLURPActivity.LOGTAG, "TestKeysTask: Auth IOException");
            mTag = null;
            return null;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        mProgressDialog.setMessage("Sector:  " + progress[0] + " / " + (mSectorCount - 1));
        Log.i(SLURPActivity.LOGTAG, "TestKeysTask: progress update " + progress[0]);
    }

    @Override
    protected void onPostExecute(MifareKeyChain keyChain) {
        Log.i(SLURPActivity.LOGTAG, "TestKeysTask: onPostExecute");

        mActivity.setProgressBarIndeterminateVisibility(false);
        mProgressDialog.dismiss();

        mActivity.setKeys(keyChain);

        if (keyChain == null) {
            Toast.makeText(mActivity, "Keys Not Found", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(mActivity, "Keys Found", Toast.LENGTH_SHORT).show();
            mActivity.readTag(mTag);
        }
    }

    private byte[] probeKey(MifareClassic tag, int sector, FindKeysTask.SECTOR_KEY keyType) throws IOException {
        for (byte[] key : mDefaultKeys) {
            Log.i(SLURPActivity.LOGTAG,
                    "Sector: " + sector + ", Key (" + keyType + "): " + DataFormater.bytesToString(key));

            if ((keyType == SECTOR_KEY.KEY_A && tag.authenticateSectorWithKeyA(sector, key))
                    || (keyType == SECTOR_KEY.KEY_B && tag.authenticateSectorWithKeyB(sector, key))) {
                Log.i(SLURPActivity.LOGTAG, "** SUCCESS ** Sector: " + sector + ", Key (" + keyType + "): "
                        + DataFormater.bytesToString(key));
                return key;
            }
        }

        return null;
    }
}