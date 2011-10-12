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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

public class SLURPActivity extends Activity {
    public static final String LOGTAG = "NFC";
    private static final String CURRENT_KEY_FILE = "current.keys";
    private static final String BUNDLE_KEY_CHAIN = "KEY_CHAIN";

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

    /** Activity class overrides */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        Log.i(LOGTAG, "onCreate");
        setContentView(R.layout.main);

        mTextBoxKeys = (EditText) findViewById(R.id.EditTextKeys);
        mTextBoxData = (EditText) findViewById(R.id.editTextData);

        mAdapter = NfcAdapter.getDefaultAdapter(this);

        if (savedInstanceState == null)
            loadKeys();
        else {
            MifareKeyChain keyChain = savedInstanceState.getParcelable(BUNDLE_KEY_CHAIN);
            setKeys(keyChain);
        }

        // Setup foreground processing of NFC intents
        mPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter techFilter = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        mFilters = new IntentFilter[] { techFilter };
        mTechLists = new String[][] { new String[] { MifareClassic.class.getName() } };

        Intent intent = getIntent();
        resolveIntent(intent);
    }

    @Override
    public void onNewIntent(Intent intent) {
        setIntent(intent);
        resolveIntent(intent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
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
            if (saveData())
                Toast.makeText(this, "Data Saved", Toast.LENGTH_LONG).show();
            else
                Toast.makeText(this, "Save Failed", Toast.LENGTH_LONG).show();
            break;
        }
        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BUNDLE_KEY_CHAIN, mKeyChain);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAdapter != null)
            mAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null)
            mAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters, mTechLists);
    }

    public void setTagData(byte[][][] data) {
        mTagData = data;
        mTextBoxData.setText("");

        if (data != null) {
            for (int sector = 0; sector < data.length; ++sector) {
                mTextBoxData.append("Sector " + sector + ":\n");
                for (int blockIndex = 0; blockIndex < data[sector].length; ++blockIndex) {
                    mTextBoxData.append(DataFormater.bytesToString(data[sector][blockIndex]) + "\n");
                }
            }
        }
    }

    public void setKeys(MifareKeyChain keys) {
        mKeyChain = keys;

        mTextBoxKeys.setText("");
        if (mKeyChain != null) {
            for (int sector = 0; sector < keys.getSectorCount(); ++sector) {
                mTextBoxKeys.append("Sector " + (sector < 10 ? ("0" + sector) : sector) + ": "
                        + DataFormater.bytesToString(keys.getKeyA(sector)) + " | "
                        + DataFormater.bytesToString(keys.getKeyB(sector)) + "\n");
            }
        }
    }

    public void readTag(MifareClassic tag) {
        mReadTagTask = new ReadTagTask(this, mKeyChain);
        Log.i(LOGTAG, "Starting keys thread");

        mReadTagTask.execute(tag);
    }

    private void findKeys(MifareClassic tag) {
        mKeysTask = new FindKeysTask(this);
        Log.i(LOGTAG, "Starting keys thread");
        mKeysTask.execute(tag);
    }

    public byte[][] getDefaultKeys() {
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
                        byte[] key = DataFormater.fromHexString(keyStr);
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

    private boolean saveData() {
        if (mTagData == null)
            return false;

        String state = Environment.getExternalStorageState();

        if (!Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
            return false;

        StringBuilder sb = new StringBuilder();
        sb.append(DataFormater.bytesToString(mTagData[0][0]).replace(" ", ""));
        sb.append(".");

        final String DATE_FORMAT = "yyyyMMdd.HHmmss";
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        sb.append(sdf.format(cal.getTime()));
        sb.append(".card");

        String fileName = sb.toString();
        Log.i(LOGTAG, "Trying to safe data to: " + fileName);

        File dataFile = new File(getExternalFilesDir(null), fileName);

        try {

            OutputStream os = new FileOutputStream(dataFile);

            byte[][][] data = mTagData; // Local variable optimization
            int totalBytes = 0;
            for (int sector = 0; sector < data.length; ++sector) {
                for (int block = 0; block < data[sector].length; ++block) {
                    os.write(data[sector][block]);
                    totalBytes += data[sector][block].length;
                }
            }

            // Pad up to 4k size (for nfc-mfclassic compatibility)
            assert (4096 - totalBytes >= 0);
            os.write(new byte[4096 - totalBytes]);

            os.close();

        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    void resolveIntent(Intent intent) {
        Log.i(LOGTAG, "resolveIntent action=" + intent.getAction());

        String action = intent.getAction();
        if (Intent.ACTION_MAIN.equals(action)) {
            setTagData(null);
        } else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {

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
            Log.i(LOGTAG, "Unknown tag type found (not MifareClassic)");
            return;
        }
        Log.i(LOGTAG, "Found MifareClassic Tag. Sector Count: " + mifareTag.getSectorCount());

        if (mKeyChain == null || mKeyChain.getSectorCount() != mifareTag.getSectorCount()) {
            Log.i(LOGTAG, "Keys is null, will start search");
            mKeyChain = null;
            findKeys(mifareTag);
        } else {
            Log.i(LOGTAG, "Keys are pressent, will try to read data");
            readTag(mifareTag);
        }
    }

    static public int getBlockIndex(MifareClassic tag, int sector, int relBlock) {
        int blockOffset = 0;
        for (int i = 0; i < sector; ++i)
            blockOffset += tag.getBlockCountInSector(i);
        return blockOffset + relBlock;
    }

}