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
import java.io.IOException;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

public class SLURPActivity extends Activity {
    static final int KEY_SIZE = 6;
    static final String LOGTAG = "NFC";
    static final String CURRENT_KEY_FILE = "current.keys";

    private NfcAdapter mAdapter;
    private PendingIntent mPendingIntent;
    private IntentFilter[] mFilters;
    private String[][] mTechLists;

    private byte[][][] mTagData;

    private FindKeysTask mKeysTask;
    private ReadTagTask mReadTagTask;

    private MifareKeyChain mKeyChain;

    private EditText mTextBoxKeys;
    private EditText mTextBoxData;

    private void setTagData(byte[][][] data) {
        mTagData = data;
        mTextBoxData.setText("");

        if (data != null) {
            for (int sector = 0; sector < data.length; ++sector) {
                mTextBoxData.append("Sector " + sector + ":\n");
                for (int blockIndex = 0; blockIndex < data[sector].length; ++blockIndex) {
                    mTextBoxData.append(bytesToString(data[sector][blockIndex]) + "\n");
                }
            }
        }
    }

    private void setKeys(MifareKeyChain keys) {
        mKeyChain = keys;

        mTextBoxKeys.setText("");
        if (mKeyChain != null) {
            for (int sector = 0; sector < keys.getSectorCount(); ++sector) {
                mTextBoxKeys.append("Sector " + (sector < 10 ? ("0" + sector) : sector) + ": " + 
                    bytesToString(keys.getKeyA(sector)) + " | " +
                    bytesToString(keys.getKeyB(sector)) + "\n");
            }
        }
    }

    private void readTag(MifareClassic tag) {
        mReadTagTask = new ReadTagTask();
        Log.i(LOGTAG, "Starting keys thread");

        mReadTagTask.execute(tag);
    }

    private void findKeys(MifareClassic tag) {
        mKeysTask = new FindKeysTask();
        Log.i(LOGTAG, "Starting keys thread");

        mKeysTask.execute(tag);
    }

    private byte[][] getDefaultKeys() {
        ArrayList<byte[]> keys = new ArrayList<byte[]>();
        keys.add(MifareClassic.KEY_DEFAULT);
        keys.add(MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY);
        keys.add(MifareClassic.KEY_NFC_FORUM);

        try {
            final String KEY_TAG = "key";
            Resources res = getResources();
            XmlResourceParser xpp = res.getXml(R.xml.mifare_default_keys);
            int eventType;
            eventType = xpp.next();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && xpp.getName().equals(KEY_TAG)) {
                    eventType = xpp.next();
                    if (eventType == XmlPullParser.TEXT) {
                        String keyStr = xpp.getText();
                        Log.i(LOGTAG, "Read key from resource: " + keyStr);
                        byte[] key = fromHexString(keyStr);
                        keys.add(key);
                    }
                } else {
                    eventType = xpp.next();
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[][] keysArray = new byte[keys.size()][];
        return keys.toArray(keysArray);
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        Log.i(LOGTAG, "onCreate");
        setContentView(R.layout.main);

        mTextBoxKeys = (EditText) findViewById(R.id.EditTextKeys);
        mTextBoxData = (EditText) findViewById(R.id.editTextData);

        mAdapter = NfcAdapter.getDefaultAdapter(this);

        loadKeys();

        // Setup foreground processing of NFC intents
        mPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter techFilter = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        mFilters = new IntentFilter[] { techFilter };
        mTechLists = new String[][] { new String[] { MifareClassic.class.getName() } };

        resolveIntent(getIntent());
    }

    private boolean loadKeys() {
        // Try to load keys from the "current" key file.
        File keyFile = new File(getExternalFilesDir(null), CURRENT_KEY_FILE);
        try {
            if (!keyFile.exists())
                return false;

            setKeys(MifareKeyChain.LoadKeys(keyFile));
        } catch (IOException e) {
            Log.e(LOGTAG, "Error loading keys: " + e);
            return false;
        }

        return true;
    }

    private boolean saveKeys() {
        if (mKeyChain == null)
            return false;

        File keyFile = new File(getExternalFilesDir(null), CURRENT_KEY_FILE);
        try {
            mKeyChain.StoreKeys(keyFile);
        } catch (IOException e) {
            Log.e(LOGTAG, "Error loading keys: " + e);
            return false;
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.clear_keys:
            setKeys(null);
            Toast.makeText(this, "Cleared Keys", Toast.LENGTH_SHORT).show();
            break;
        case R.id.clear_data:
            setTagData(null);
            Toast.makeText(this, "Cleared Data", Toast.LENGTH_SHORT).show();
            break;
        case R.id.save_keys:
            if (mKeyChain == null) {
                Toast.makeText(this, "No keys in use", Toast.LENGTH_LONG).show();
            } else if (saveKeys()) {
                Toast.makeText(this, "Keys Saved", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Save Failed", Toast.LENGTH_LONG).show();
            }
            break;
        case R.id.load_keys:
            loadKeys();
            if (mKeyChain != null)
                Toast.makeText(this, "Keys Loaded", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(this, "Load Failed", Toast.LENGTH_LONG).show();

            break;
        case R.id.dump_data:
            Toast.makeText(this, "Not Impl.", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    void resolveIntent(Intent intent) {
        Log.i(LOGTAG, "resolveIntent action=" + intent.getAction());

        String action = intent.getAction();

        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

            Parcelable tags = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if (tags == null) {
                Log.i(LOGTAG, "resolveIntent: ParcelableExtra (tag) was null");
                return;
            }

            tagDetected((Tag) tags);
        }
    }

    private void tagDetected(Tag tag) {
        MifareClassic mifareTag = MifareClassic.get(tag);
        if (mifareTag == null) {
            Log.i(LOGTAG, "resolveIntent: Unknown tag type (not MifareClassic)");
            return;
        }
        Log.i(LOGTAG, "resolveIntent: found MifareClassic tag");
        Log.i(LOGTAG, "Sector Count: " + mifareTag.getSectorCount());

        int size = mifareTag.getSize();
        if (size == MifareClassic.SIZE_1K)
            Log.i(LOGTAG, "Size: 1k");
        else if (size == MifareClassic.SIZE_4K)
            Log.i(LOGTAG, "Size: 4k");
        else
            Log.i(LOGTAG, "Size: ? (code " + size + ")");

        Log.i(LOGTAG, "Tag type: " + mifareTag.getType());

        if (mKeyChain == null || mKeyChain.getSectorCount() != mifareTag.getSectorCount()) {
            Log.i(LOGTAG, "Keys is null, will start search");
            mKeyChain = null;
            findKeys(mifareTag);
        } else {
            Log.i(LOGTAG, "Keys are pressent, will try to read data");
            readTag(mifareTag);
        }

        Log.i(LOGTAG, "Leaving resolveIntent");
    }

    static private int getBlockIndex(MifareClassic tag, int sector, int relBlock) {
        int blockOffset = 0;
        for (int i = 0; i < sector; ++i)
            blockOffset += tag.getBlockCountInSector(i);
        return blockOffset + relBlock;
    }

    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    public void onPause() {
        super.onPause();
        mAdapter.disableForegroundDispatch(this);
    }

    public void onResume() {
        super.onResume();
        mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
    }

    private enum SECTOR_KEY {
        KEY_A, KEY_B
    };

    private class FindKeysTask extends AsyncTask<MifareClassic, Integer, MifareKeyChain> {
        private MifareClassic mTag;
        private int mSectorCount;
        private byte[][] mDefaultKeys = getDefaultKeys(); // Make static

        ProgressDialog mProgressDialog;

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);

            mProgressDialog = new ProgressDialog(SLURPActivity.this);
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

            Log.i(LOGTAG, "TestKeysTask: doInBackground");
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
                Log.e(LOGTAG, "TestKeysTask: Auth IOException");
                mTag = null;
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            mProgressDialog.setMessage("Sector:  " + progress[0] + " / " + (mSectorCount - 1));
            Log.i(LOGTAG, "TestKeysTask: progress update " + progress[0]);
        }

        @Override
        protected void onPostExecute(MifareKeyChain keyChain) {
            Log.i(LOGTAG, "TestKeysTask: onPostExecute");

            setProgressBarIndeterminateVisibility(false);
            mProgressDialog.dismiss();

            if (keyChain == null) {
                Toast.makeText(SLURPActivity.this, "Keys Not Found", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SLURPActivity.this, "Keys Found", Toast.LENGTH_SHORT).show();
                readTag(mTag);
            }

            setKeys(keyChain);
        }

        private byte[] probeKey(MifareClassic tag, int sector, SECTOR_KEY keyType) throws IOException {
            for (byte[] key : mDefaultKeys) {
                Log.i(LOGTAG, "Sector: " + sector + ", Key (" + keyType + "): " + bytesToString(key));

                if ((keyType == SECTOR_KEY.KEY_A && tag.authenticateSectorWithKeyA(sector, key))
                        || (keyType == SECTOR_KEY.KEY_B && tag.authenticateSectorWithKeyB(sector, key))) {
                    Log.i(LOGTAG, "** SUCCESS ** Sector: " + sector + ", Key (" + keyType + "): " + bytesToString(key));
                    return key;
                }
            }

            return null;
        }
    }

    private class ReadTagTask extends AsyncTask<MifareClassic, Integer, byte[][][]> {

        private ProgressDialog mProgressDialog;

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);

            mProgressDialog = new ProgressDialog(SLURPActivity.this);
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
            Log.i(LOGTAG, "ReadTagTask: doInBackground");
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
                Log.e(LOGTAG, "ReadTagTask: Auth IOException");
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            mProgressDialog.setProgress(progress[0]);
            Log.i(LOGTAG, "TestKeysTask: progress update " + progress[0]);
        }

        @Override
        protected void onPostExecute(byte[][][] data) {
            Log.i(LOGTAG, "ReadTagTask: onPostExecute");

            setProgressBarIndeterminateVisibility(false);
            mProgressDialog.dismiss();

            if (data == null)
                Toast.makeText(SLURPActivity.this, "Couldn't read data", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(SLURPActivity.this, "Data Read", Toast.LENGTH_SHORT).show();

            setTagData(data);
        }

        private byte[][] readSector(MifareClassic tag, int sector, byte[] keyA, byte[] keyB) throws IOException {
            byte[][] data = new byte[tag.getBlockCountInSector(sector)][];

            Log.i(LOGTAG, "ReadTagTask");
            boolean res = keyA != null && tag.authenticateSectorWithKeyA(sector, keyA);

            if (res)
                Log.i(LOGTAG, "Use Key A");

            if (!res && keyB != null) {
                Log.i(LOGTAG, "Use Key A");
                res = tag.authenticateSectorWithKeyB(sector, keyB);
            }

            if (!res)
                throw new IOException("READ ERROR - can't auth");

            for (int i = 0; i < tag.getBlockCountInSector(sector); ++i)
                data[i] = tag.readBlock(getBlockIndex(tag, sector, i));

            return data;
        }

    }

    private static byte[] fromHexString(final String encoded) {
        if ((encoded.length() % 2) != 0)
            throw new IllegalArgumentException("Input string must contain an even number of characters");

        final byte result[] = new byte[encoded.length() / 2];
        final char enc[] = encoded.toCharArray();
        for (int i = 0; i < enc.length; i += 2) {
            StringBuilder curr = new StringBuilder(2);
            curr.append(enc[i]).append(enc[i + 1]);
            result[i / 2] = (byte) Integer.parseInt(curr.toString(), 16);
        }
        return result;
    }

    private static String byteToHexString(byte b) {
        String hex = Integer.toHexString(b & 0xff);
        return (b & 0xff) < 0x10 ? "0" + hex : hex;
    }

    private static String bytesToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append(byteToHexString(bytes[0]));
        for (int i = 1; i < bytes.length; ++i) {
            sb.append(" ");
            sb.append(byteToHexString(bytes[i]));
        }
        return sb.toString();
    }
}