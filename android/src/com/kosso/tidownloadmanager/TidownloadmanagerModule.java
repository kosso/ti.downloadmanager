package com.kosso.tidownloadmanager;

/*

	com.kosso.tidownloadmanager

	Uses the built-in DownloadManager to download files. 
	Destination path must be to External Storage. 
	// Events: 'success', 'error' and 'progress'. 
	// Contains a 'uid' to determine which is which when downloading multiple files at once. 


	// Credit for original code and inspiration to @m1ga and https://github.com/m1ga/com.miga.downloadmanager 
	// Happy to try and merge this in to that module if needed. 

	// @Kosso. Jan 19 2018

*/

import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.KrollDict;
import android.app.DownloadManager;
import android.app.Activity;
import android.net.Uri;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileFactory;
import android.content.IntentFilter;
import org.appcelerator.kroll.KrollFunction;
import java.util.HashMap;
import android.database.Cursor;
import java.util.ArrayList;
import java.io.File;
import java.lang.InterruptedException;

@Kroll.module(name="Tidownloadmanager", id="com.kosso.tidownloadmanager")
public class TidownloadmanagerModule extends KrollModule
{

	// Standard Debugging variables
	private static final String LCAT = "TidownloadmanagerModule";

	private TiApplication appContext = TiApplication.getInstance();
	private Activity activity = appContext.getCurrentActivity();
	private DownloadManager dMgr;
	private KrollFunction callbackSuccess;
	private KrollFunction callbackProgress;
	private KrollFunction callbackError;
	
	public TidownloadmanagerModule()
	{
		super();
		ServiceReceiver service = new ServiceReceiver(this);		
		activity.registerReceiver(service, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
		activity.registerReceiver(service, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		//Log.d(LCAT, "inside onAppCreate");
	}

	public void startProgress(final long dId) {
		Log.d( LCAT, "startProgress for download queue ID:" + TiConvert.toString(dId));
		new Thread(new Runnable() {
			@Override
            public void run() {

				try {
					if(Thread.currentThread().isInterrupted()){
						Log.d(LCAT, "Thread was interrupted for ID: "+TiConvert.toString(dId));
						throw new InterruptedException();
					}
					Log.d(LCAT, "running for " + TiConvert.toString(dId));
					DownloadManager.Query q = new DownloadManager.Query();
					q.setFilterById(dId);
					boolean downloading = true;
					while(downloading && !Thread.currentThread().isInterrupted()) {
						Cursor cursor = dMgr.query(q);
						if(cursor != null) {
							if (cursor.moveToFirst()) {
								//final String _downloadId = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
								final int bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
								final int bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
								if(bytesTotal > 0){
									final double dlProgress = (int) ((bytesDownloaded * 100l) / bytesTotal);	
									if (callbackProgress!=null){
										String filePath = null;
										String downloadFileLocalUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
										if (downloadFileLocalUri != null) {
											File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
											filePath = mFile.getAbsolutePath();
										}
										HashMap<String,Object> event = new HashMap<String,Object>();
										event.put("uid",TiConvert.toString(dId));
										event.put("status",cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
										event.put("message",statusMessage(cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))));
										event.put("path",filePath);
										event.put("url",cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI)));
										event.put("title",cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)));
										event.put("description",cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)));
										event.put("progress",TiConvert.toString(dlProgress));
										event.put("size_downloaded",bytesDownloaded);
										event.put("size_total",bytesTotal);					
										callbackProgress.call(getKrollObject(), event);
									}
								}
								if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
									Log.d(LCAT, TiConvert.toString(dId)+": Download done! Interrupt thread... ");
									downloading = false;
									Thread.currentThread().interrupt();
									break;
								}
							}
							cursor.close();
						} else {
							// Cursor was null for this ID. So the download might have been cancelled.
							// Is the thread still running?
							Log.d(LCAT, TiConvert.toString(dId)+": Cursor was null for this ID. Try and interrupt thread... ");
							downloading = false;
							Thread.currentThread().interrupt();
							break;
						}
						cursor = null;
					} // end while downloading.
					
				} catch (InterruptedException ex) {
					Log.d(LCAT, "InterruptedException: ID: "+TiConvert.toString(dId));
				}
			}
		}).start();
  }
		
	private String statusMessage(final int status) {
        String msg = "???";
        switch (status) {
            case DownloadManager.STATUS_FAILED:
                msg = "Download failed!";
                break;
            case DownloadManager.STATUS_PAUSED:
                msg = "Download paused!";
                break;
            case DownloadManager.STATUS_PENDING:
                msg = "Download pending!";
                break;
            case DownloadManager.STATUS_RUNNING:
                msg = "Download in progress!";
                break;
            case DownloadManager.STATUS_SUCCESSFUL:
                msg = "Download complete!";
                break;
            default:
                msg = "Download is nowhere in sight";
                break;
        }
        return (msg);
	}

	public void error() {
		Log.e(LCAT,"Download error");
		if (callbackError!=null){
			HashMap<String,String> event = new HashMap<String, String>();
			event.put("foo","bar");
			// hmmm... 
			Log.d(LCAT,"fire error event..");
			callbackError.call(getKrollObject(), event);
		}
	}

	public void done(String downloadId, String finalPath, String sourceUrl, int bytesTotal, String filePath, String title, String description) {
		if (callbackSuccess!=null){
			HashMap<String,String> event = new HashMap<String, String>();
			event.put("uid",downloadId);
			event.put("path",filePath);
			event.put("title",title);
			event.put("description",description);
			event.put("path",finalPath);
			event.put("url",sourceUrl);
			event.put("size_total",TiConvert.toString(bytesTotal));
			// Log.i(LCAT,"fire success event..: " + TiConvert.toString(event));
			callbackSuccess.call(getKrollObject(), event);
		}
	}

	public void cancel() {
		// Do nothing... Called by ServiceReceiver when downlaod notification is clicked. [DownloadManager.ACTION_NOTIFICATION_CLICKED]

		// or.... 
		//Intent pageView = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
		//pageView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		//appContext.startActivity(pageView);
	}

	public void startDownloadManager(KrollDict dict) {        
		// TODO: Add support for an array of urls.
		Log.d(LCAT,"startDownloadManager:");
		Log.d(LCAT,"Source url  : " + TiConvert.toString(dict, "url"));
		Log.d(LCAT,"Download to : " + TiConvert.toString(dict, "path"));
		if(dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);
		}
		// Build the request.. 
		DownloadManager.Request dmReq = new DownloadManager.Request(Uri.parse(TiConvert.toString(dict, "url")));
		dmReq.setTitle( TiConvert.toString(dict, "title")  );
		dmReq.setDescription(TiConvert.toString(dict, "description") );
		TiBaseFile file = TiFileFactory.createTitaniumFile(new String[] { TiConvert.toString(dict, "path") }, false);
		dmReq.setDestinationUri(Uri.fromFile(file.getNativeFile()));
		final long downloadQueueId = dMgr.enqueue(dmReq);
		Log.d(LCAT,"URL added download to queue. id: " + TiConvert.toString(downloadQueueId));
		// Starts a Thread(Runnable() {..}) to monitor the download progress for this unique ID in the DownloadManager.
		startProgress(downloadQueueId);		
	}

	// ##################################################################################
	// Available Module Methods
	@Kroll.method
	public void startDownload(KrollDict dict) {
		callbackSuccess = (KrollFunction) dict.get("success");
		callbackError = (KrollFunction) dict.get("error");
		callbackProgress = (KrollFunction) dict.get("progress");
		startDownloadManager(dict);
	}	

	@Kroll.method
	public void cancelDownload(String uid) {
		Log.d(LCAT,"cancelDownload : " + uid);
		if (dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);
		}
		dMgr.remove( Long.parseLong(uid) );
	}

	@Kroll.method
	public void cancelAllDownloads() {
		Log.d(LCAT,"cancelAllDownloads");
		if (dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);
		}
		DownloadManager.Query query = new DownloadManager.Query();
		Cursor c = dMgr.query(query);
		c.moveToFirst();
		while (c.moveToNext()) {
			Log.d(LCAT, "removing uid: "+TiConvert.toString(c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID))));
			dMgr.remove(c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID)));
		}
		c.close();
		dMgr = null;
	}

	@Kroll.method
	public Object[] getDownloads() {
		ArrayList<HashMap<String, Object>> downList = new ArrayList<HashMap<String, Object>>();
		
		if (dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);
			return downList.toArray();
		}
		DownloadManager.Query query = new DownloadManager.Query();
		Cursor c = dMgr.query(query);
		c.moveToFirst();
		while (c.moveToNext()) {
			HashMap<String, Object> dl = new HashMap<String, Object>();
			String filePath = null;
			String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
			if (downloadFileLocalUri != null) {
				File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
				filePath = mFile.getAbsolutePath();
			}
			int bytes_downloaded = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
			int bytes_total = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
			dl.put("uid",c.getString(c.getColumnIndex(DownloadManager.COLUMN_ID)));
			dl.put("status",c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS)));
			dl.put("message",statusMessage(c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))));
			dl.put("path",filePath);
			dl.put("title",c.getString(c.getColumnIndex(DownloadManager.COLUMN_TITLE)));
			dl.put("description",c.getString(c.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)));
			dl.put("size_total",bytes_total);
			dl.put("size_downloaded",bytes_downloaded);
			downList.add(dl);
		}
		c.close();
		return downList.toArray();
	}
}

