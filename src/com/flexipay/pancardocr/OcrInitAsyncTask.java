/*
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.flexipay.pancardocr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

/**
 * Installs the language data required for OCR, and initializes the OCR engine using a background 
 * thread.
 */
final class OcrInitAsyncTask extends AsyncTask<String, String, Boolean> {
  private static final String TAG = OcrInitAsyncTask.class.getSimpleName();

  /** Suffixes of required data files for Cube. */
  private static final String[] CUBE_DATA_FILES = {
    ".cube.bigrams",
    ".cube.fold", 
    ".cube.lm", 
    ".cube.nn", 
    ".cube.params", 
    //".cube.size", // This file is not available for Hindi
    ".cube.word-freq", 
    ".tesseract_cube.nn", 
    ".traineddata"
  };

  private CaptureActivity activity;
  private Context context;
  private TessBaseAPI baseApi;
  private ProgressDialog dialog;
  private ProgressDialog indeterminateDialog;
  private final String languageCode;
  private String languageName;
  private int ocrEngineMode;
  private boolean assetsCopied;

  /**
   * AsyncTask to asynchronously download data and initialize Tesseract.
   * 
   * @param activity
   *          The calling activity
   * @param baseApi
   *          API to the OCR engine
   * @param dialog
   *          Dialog box with thermometer progress indicator
   * @param indeterminateDialog
   *          Dialog box with indeterminate progress indicator
   * @param languageCode
   *          ISO 639-2 OCR language code
   * @param languageName
   *          Name of the OCR language, for example, "English"
   * @param ocrEngineMode
   *          Whether to use Tesseract, Cube, or both
   */
  OcrInitAsyncTask(CaptureActivity activity, TessBaseAPI baseApi, ProgressDialog dialog, 
      ProgressDialog indeterminateDialog, String languageCode, String languageName, 
      int ocrEngineMode, boolean assetsCopied) {
    this.activity = activity;
    this.context = activity.getBaseContext();
    this.baseApi = baseApi;
    this.dialog = dialog;
    this.indeterminateDialog = indeterminateDialog;
    this.languageCode = languageCode;
    this.languageName = languageName;
    this.ocrEngineMode = ocrEngineMode;
    this.assetsCopied = assetsCopied;
  }

  @Override
  protected void onPreExecute() {
    super.onPreExecute();
    dialog.setTitle("Please wait");
    if(assetsCopied){
    	dialog.setMessage("Initializing OCR");
        dialog.setIndeterminate(true);
    }
    else {
    dialog.setMessage("Setting up OCR engine...");
    dialog.setIndeterminate(false);
    dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    }
    dialog.setCancelable(false);
    dialog.show();
    activity.setButtonVisibility(false);
  }

  /**
   * In background thread, perform required setup, and request initialization of
   * the OCR engine.
   * 
   * @param params
   *          [0] Pathname for the directory for storing language data files to the SD card
   */
  protected Boolean doInBackground(String... params) {

	  if(!assetsCopied){
	  boolean result = copyAssetFolder(this.context.getAssets(), "ocr", 
              this.context.getFilesDir().getAbsolutePath());
	  if(result){
		  SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
          prefs.edit().putBoolean(PreferencesActivity.KEY_ASSETS_COPIED, result).commit();
	  }
	  }
    // Initialize the OCR engine
    if (baseApi.init(this.context.getFilesDir().getAbsolutePath() + File.separator, languageCode, ocrEngineMode)) {
      return true;
    }
    return false;
  }
  
  private static boolean copyAssetFolder(AssetManager assetManager,
          String fromAssetPath, String toPath) {
      try {
          String[] files = assetManager.list(fromAssetPath);
          new File(toPath).mkdirs();
          boolean res = true;
          for (String file : files)
              if (file.contains("."))
                  res &= copyAsset(assetManager, 
                          fromAssetPath + "/" + file,
                          toPath + "/" + file);
              else 
                  res &= copyAssetFolder(assetManager, 
                          fromAssetPath + "/" + file,
                          toPath + "/" + file);
          return res;
      } catch (Exception e) {
          e.printStackTrace();
          return false;
      }
  }

  private static boolean copyAsset(AssetManager assetManager,
          String fromAssetPath, String toPath) {
      InputStream in = null;
      OutputStream out = null;
      try {
        in = assetManager.open(fromAssetPath);
        new File(toPath).createNewFile();
        out = new FileOutputStream(toPath);
        copyFile(in, out);
        in.close();
        in = null;
        out.flush();
        out.close();
        out = null;
        return true;
      } catch(Exception e) {
          e.printStackTrace();
          return false;
      }
  }

  private static void copyFile(InputStream in, OutputStream out) throws IOException {
      byte[] buffer = new byte[1024];
      int read;
      while((read = in.read(buffer)) != -1){
        out.write(buffer, 0, read);
      }
  }

  /**
   * Delete any existing data files for Cube that are present in the given directory. Files may be 
   * partially uncompressed files left over from a failed install, or pre-v3.01 traineddata files.
   * 
   * @param tessdataDir
   *          Directory to delete the files from
   */
  private void deleteCubeDataFiles(File tessdataDir) {
    File badFile;
    for (String s : CUBE_DATA_FILES) {
      badFile = new File(tessdataDir.toString() + File.separator + languageCode + s);
      if (badFile.exists()) {
        Log.d(TAG, "Deleting existing file " + badFile.toString());
        badFile.delete();
      }
      badFile = new File(tessdataDir.toString() + File.separator + "tesseract-ocr-3.01." 
          + languageCode + ".tar");
      if (badFile.exists()) {
        Log.d(TAG, "Deleting existing file " + badFile.toString());
        badFile.delete();
      }
    }
  }


  /**
   * Update the dialog box with the latest incremental progress.
   * 
   * @param message
   *          [0] Text to be displayed
   * @param message
   *          [1] Numeric value for the progress
   */
  @Override
  protected void onProgressUpdate(String... message) {
    super.onProgressUpdate(message);
    int percentComplete = 0;

    percentComplete = Integer.parseInt(message[1]);
    dialog.setMessage(message[0]);
    dialog.setProgress(percentComplete);
    dialog.show();
  }

  @Override
  protected void onPostExecute(Boolean result) {
    super.onPostExecute(result);
    
    try {
      indeterminateDialog.dismiss();
    } catch (IllegalArgumentException e) {
      // Catch "View not attached to window manager" error, and continue
    }

    if (result) {dialog.dismiss();
      // Restart recognition
      activity.resumeOCR();
      activity.showLanguageName();
    } else {
      activity.showErrorMessage("Error", "Network is unreachable - cannot download language data. "
          + "Please enable network access and restart this app.");
    }
  }
}