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
import org.appcelerator.kroll.annotations.Kroll.method;
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
import java.lang.Thread;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Kroll.module(name="Tidownloadmanager", id="com.kosso.tidownloadmanager")
public class TidownloadmanagerModule extends KrollModule
{

	// Standard Debugging variables
	private static final String LCAT = "TidownloadmanagerModule";

	private TiApplication appContext = TiApplication.getInstance();
	private Activity activity = appContext.getCurrentActivity();
	private DownloadManager dMgr;	
	public Future<?> future;
	public HashMap<Long, Future<?>> hashMapOfRequestedThreads;
	public ExecutorService executorService;
	private ServiceReceiver service;

	public TidownloadmanagerModule()
	{
		super();
		if(!isDownloadManagerAvailable()){
			Log.e(LCAT, "NO DOWNLOAD MANAGER AVAIABLE. REQUIRES GINGERBREAD OR NEWER!");
			return;
		}
		registerReceiver();
		hashMapOfRequestedThreads = new HashMap<Long, Future<?>>();
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		// Log.d(LCAT, "inside onAppCreate");
		// put module init code that needs to run when the application is created
	}

	public static boolean isDownloadManagerAvailable() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return true;
		}
		return false;
	}

	public void registerReceiver(){
		if(service==null){
			Log.d(LCAT, "registerReceiver");
			service = new ServiceReceiver(this);		
			activity.registerReceiver(service, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
			activity.registerReceiver(service, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));	
		}
	}

	public void resetReceiver(){
		if(service!=null){
			Log.d(LCAT, "resetReceiver");
			activity.unregisterReceiver(service);
			service = null;
		}
		service = new ServiceReceiver(this);		
		activity.registerReceiver(service, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
		activity.registerReceiver(service, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));	
	}

	public void startProgress(final long dId) {
		Log.d( LCAT, "startProgress for download queue ID:" + TiConvert.toString(dId));

		executorService = Executors.newSingleThreadExecutor();
		Runnable thisRunnable = new Runnable() {
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
									//if (callbackProgress!=null){
										String filePath = null;
										String fileName = null;	
										String downloadFileLocalUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
										if (downloadFileLocalUri != null) {
											File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
											filePath = mFile.getAbsolutePath();
											fileName = mFile.getName();
											
										}
										HashMap<String,Object> event = new HashMap<String,Object>();
										event.put("uid",TiConvert.toString(dId));
										//event.put("status",cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
										//event.put("message",statusMessage(cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))));
										event.put("path",filePath);
										event.put("url",cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI)));
										// event.put("title",cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)));
										
										//event.put("filename",cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME))); // why is the path?
										event.put("filename", fileName); 

										// event.put("description",cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)));
										event.put("progress",TiConvert.toDouble( dlProgress / 100));
										event.put("bytes_downloaded",bytesDownloaded);
										event.put("bytes_total",bytesTotal);					
										//callbackProgress.call(getKrollObject(), event);
										fireEvent("progress", event);

									//}
								}
								if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
									Log.d(LCAT, TiConvert.toString(dId)+": Download done! Interrupt thread... ");
									downloading = false;
									Thread.currentThread().interrupt();
									
									Boolean cancelled = hashMapOfRequestedThreads.get(dId).cancel(true); // cancel the Future
									Log.d(LCAT, "Download complete: Cancelled runnable: " + cancelled);

									hashMapOfRequestedThreads.remove(dId); // delete from HashMap
									Log.d(LCAT, "Download complete: hashMapOfRequestedThreads count: " + TiConvert.toString(hashMapOfRequestedThreads.size()));
									if(hashMapOfRequestedThreads.size() == 0){
										// shutdown the executorService
										Log.d(LCAT, "All Downloads look complete: KILL THE EXECUTOR!");
										executorService.shutdown();
										executorService = null;
										cursor.close();
									}
									break;
								}
							}
							cursor.close();
						} 
						cursor = null;
					}
				} catch (InterruptedException ex) {
					Log.d(LCAT, "InterruptedException: ID: "+TiConvert.toString(dId));
				}
			}
		};

		// Store the thread runnable in a HashMap of Futures.
		Future<?> future = executorService.submit(thisRunnable);
		// 
		hashMapOfRequestedThreads.put(dId, future);
		Log.d(LCAT, "added to hashMapOfRequestedThreads: count:" + TiConvert.toString(hashMapOfRequestedThreads.size()));
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
		//if (callbackError!=null){
			HashMap<String,String> event = new HashMap<String, String>();
			event.put("foo","bar");
			// TODO
			Log.d(LCAT,"fire error event..");
			//callbackError.call(getKrollObject(), event);
			fireEvent("error", event);

		//}
	}

	public void done(String downloadId, String sourceUrl, int bytesTotal, String filePath, String fileName, String description) {
		//if (callbackSuccess!=null){
			HashMap<String,String> event = new HashMap<String, String>();
			event.put("uid",downloadId);
			event.put("path",filePath);
			// event.put("title",title);
			event.put("filename",fileName);
			event.put("description",description); // left here for now for test
			event.put("url",sourceUrl);
			event.put("bytes_total",TiConvert.toString(bytesTotal));
			// Log.i(LCAT,"fire success event..: " + TiConvert.toString(event));
			// callbackSuccess.call(getKrollObject(), event);
			fireEvent("success", event);
		//}
	}

	public void cancel() {
		// Do nothing... Called by ServiceReceiver when downlaod notification is clicked. [DownloadManager.ACTION_NOTIFICATION_CLICKED]

		// or.... 
		//Intent pageView = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
		//pageView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		//appContext.startActivity(pageView);
	}

	public void startDownload(KrollDict dict) {        
		// TODO: Add support for an array of urls.
		// Log.d(LCAT,"startDownload:");
		Log.d(LCAT,"Source url  : " + TiConvert.toString(dict, "url"));
		Log.d(LCAT,"Download to : " + TiConvert.toString(dict, "path"));
		// iOS specifies filename too
		if(dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(TiApplication.DOWNLOAD_SERVICE);
		}
		// Build the request.. 
		DownloadManager.Request dmReq = new DownloadManager.Request(Uri.parse(TiConvert.toString(dict, "url")));
	
		// Hides downloads from the main device DownloadManger UI (usually dragged down from the top of the screen)
		dmReq.setVisibleInDownloadsUi(false);
		dmReq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
		// REQUIRES: <uses-permission android:name="android.permission.DOWNLOAD_WITHOUT_NOTIFICATION" />
		// https://developer.android.com/reference/android/app/DownloadManager.Request.html#setNotificationVisibility(int)
		
		// Since we make the downloads invisible to the standard UI, do we even need these? iOS does not support them.
		//	dmReq.setTitle( TiConvert.toString(dict, "title")  );
		//	dmReq.setDescription(TiConvert.toString(dict, "description") );
		TiBaseFile file = TiFileFactory.createTitaniumFile(new String[] { TiConvert.toString(dict, "path") }, false);
		dmReq.setDestinationUri(Uri.fromFile(file.getNativeFile()));
		// ADD TO DOWNLOAD QUEUE
		final long downloadQueueId = dMgr.enqueue(dmReq);
		Log.d(LCAT,"URL added download to queue. id: " + TiConvert.toString(downloadQueueId));
		// Starts a Thread(Runnable() {..}) to monitor the download progress for this unique ID in the DownloadManager.
		startProgress(downloadQueueId);		
	}

	// ##################################################################################
	// Available Module Methods
	// iOS parity method
	@Kroll.method
	public void downloadFile(KrollDict dict) {
		if(service==null){
			registerReceiver();
		}
		startDownload(dict);
	}

	@Kroll.method
	public void cancelDownload(String uid) {
		// Log.d(LCAT,"cancelDownload : " + uid);
		Future<?> f = hashMapOfRequestedThreads.get(Long.parseLong(uid));
		if(f != null){
			Boolean cancelled = f.cancel(true); // cancel the Future
			// Log.d(LCAT, "cancel single: cancelling runnable: " + cancelled);
		}
		f = null;
		hashMapOfRequestedThreads.remove(Long.parseLong(uid));  // delete from HashMap
		// Log.d(LCAT, "cancel single: hashMapOfRequestedThreads count: " + TiConvert.toString(hashMapOfRequestedThreads.size()));
		if(hashMapOfRequestedThreads.size() == 0){
			// shutdown the executorService
			// Log.d(LCAT, "cancel single: KILL THE EXECUTOR!");
			executorService.shutdownNow();
			executorService = null;	
		}
		if (dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);
		}
		int removed = dMgr.remove( Long.parseLong(uid) );
		Log.d(LCAT, "removed from downloads: uid: " + removed);
	}
	@Kroll.method
	public void cancelDownloadForUID(String uid) {
		// iOS parity
		cancelDownload(uid);
	}

	@Kroll.method
	public void cancelAllDownloads() {
		// Log.d(LCAT,"cancelAllDownloads:" );
		if (dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);
		}
		DownloadManager.Query query = new DownloadManager.Query();
		Cursor c = dMgr.query(query);
		c.moveToFirst();
		while (c.moveToNext()) {
			if(TiConvert.toInt(c.getString(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))) < TiConvert.toInt(c.getString(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))){
				// Incomplete.. 
				Log.d(LCAT, "cancel all: removing incompleted download: uid: "+TiConvert.toString(c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID))));
				long dId = Long.parseLong(c.getString(c.getColumnIndex(DownloadManager.COLUMN_ID)));
				Future<?> f = hashMapOfRequestedThreads.get( dId );
				if(f != null){
					Boolean cancelled = f.cancel(true); // cancel the Future!
					Log.d(LCAT, "in cancel all: cancel runnable: " + cancelled);
				}
				f = null;
				hashMapOfRequestedThreads.remove( dId );  // delete from HashMap

				int removed = dMgr.remove( dId );
				// Log.d(LCAT, "removed from downloads: " + removed);
				// Log.d(LCAT, "cancel all: hashMapOfRequestedThreads count: " + TiConvert.toString(hashMapOfRequestedThreads.size()));		
			}
		}
		c.close();
		// Log.d(LCAT, "after cancel all: KILL THE EXECUTOR!");
		executorService.shutdownNow();
		executorService = null;

		if(service!=null){
			activity.unregisterReceiver(service);
			service = null;
			Log.d(LCAT, "unregistered Receiver");
		}
	}

	@Kroll.method
	public Object[] getDownloads() {
		ArrayList<HashMap<String, Object>> downList = new ArrayList<HashMap<String, Object>>();
		if (dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);
			//return downList.toArray();
			// Gets old ones too. 
		}
		DownloadManager.Query query = new DownloadManager.Query();
		Cursor c = dMgr.query(query);
		c.moveToFirst();
		while (c.moveToNext()) {
			HashMap<String, Object> dl = new HashMap<String, Object>();
			String filePath = null;
			String fileName = null;	
			String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
			if (downloadFileLocalUri != null) {
				File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
				filePath = mFile.getAbsolutePath();
				fileName = mFile.getName();
			}
			int bytes_downloaded = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
			int bytes_total = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
			dl.put("uid",c.getString(c.getColumnIndex(DownloadManager.COLUMN_ID)));
			dl.put("filename",fileName);
			dl.put("status",c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS)));
			dl.put("message",statusMessage(c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))));
			dl.put("path",filePath);
			dl.put("title",c.getString(c.getColumnIndex(DownloadManager.COLUMN_TITLE))); // Not used by iOS
			dl.put("description",c.getString(c.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION))); // Not used by iOS
			dl.put("bytes_total",bytes_total);
			dl.put("bytes_downloaded",bytes_downloaded);
			downList.add(dl);
		}
		c.close();
		return downList.toArray();
	}

	@Kroll.method
	public void destroy() {
		Log.d(LCAT, "destroy");
		/*
		 ** Not obligatory.
		 But if you call this just before exiting the app, it stops a big warning 
		 in the logs about not unregistering the service. You don't see it elsewhere.
		*/
		stop();
		super.onDestroy(activity);
	}

	@Kroll.method
	public void stop() {
		Log.d(LCAT, "stop");
		if(service!=null){
			activity.unregisterReceiver(service);
		}
		if(executorService != null){
			executorService.shutdownNow();
			executorService = null;
		}
		service = null;
		dMgr = null;
	}	
}
