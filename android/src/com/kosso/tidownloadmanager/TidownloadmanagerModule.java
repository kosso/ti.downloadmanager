package com.kosso.tidownloadmanager;

/*

	com.kosso.tidownloadmanager

	Uses the built-in DownloadManager to download files. 
	Destination path must be to External Storage. 
	// Events: 'success', 'error' and 'progress'. 
	// Contains a 'uid' to determine which is which when downloading multiple files at once. 
	// Not decided if this was a good idea yet. ;) 

	// Credit for original code and inspiration to @m1ga and https://github.com/m1ga/com.miga.downloadmanager 
	// Happy to try and merge this in to that module if needed. 

	// @Kosso. Jan 19 2018

*/

import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;
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


@Kroll.module(name="Tidownloadmanager", id="com.kosso.tidownloadmanager")
public class TidownloadmanagerModule extends KrollModule
{

	// Standard Debugging variables
	private static final String LCAT = "TidownloadmanagerModule";
	private static final boolean DBG = TiConfig.LOGD;

	private TiApplication appContext = TiApplication.getInstance();
	private Activity activity = appContext.getCurrentActivity();
	private DownloadManager dMgr;
	private KrollFunction callbackSuccess;
	private KrollFunction callbackProgress;
	private KrollFunction callbackError;
	private long downloadQueueId;
	
	public TidownloadmanagerModule()
	{
		super();
		ServiceReceiver service = new ServiceReceiver(this);		
		activity.registerReceiver(service, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
		activity.registerReceiver(service, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));
		// activity.registerReceiver(service, new IntentFilter(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		//Log.d(LCAT, "inside onAppCreate");
	}


	// via: https://github.com/hallahan/DownloadManagerTest/blob/master/app/src/main/java/com/spatialdev/downloadmanagertest/MainActivity.java
	public void startProgress() {
		//Log.i(LCAT, "startProgress... ");
        new Thread(new Runnable() {
            @Override
            public void run() {

				Log.i(LCAT, "startProgress run thread for ID:" + TiConvert.toString(downloadQueueId));
				
				DownloadManager.Query q = new DownloadManager.Query();
				q.setFilterById(downloadQueueId);
				
                boolean downloading = true;
                while(downloading) {
					Cursor cursor = dMgr.query(q);
					if(cursor != null) {
						if (cursor.moveToFirst()) {
							final String _downloadId = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
							final int bytesDownloaded = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
							final int bytesTotal = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
							if(bytesTotal > 0){
								final double dlProgress = (int) ((bytesDownloaded * 100l) / bytesTotal);	
								if (callbackProgress!=null){
									HashMap<String,String> event = new HashMap<String, String>();
									event.put("uid", _downloadId);
									event.put("url", cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI)));
									event.put("progress", TiConvert.toString(dlProgress));
									event.put("bytes", TiConvert.toString(bytesDownloaded));
									event.put("total", TiConvert.toString(bytesTotal));					
									callbackProgress.call(getKrollObject(), event);
								}
							} else {
								Log.i(LCAT, _downloadId+":waiting... status: "+ statusMessage(cursor, bytesDownloaded, 0));
							}
							if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
								// Log.i(LCAT, _downloadId+":download done!");
								downloading = false;
							}
						}
						cursor.close();
					}
					cursor = null;
                }
            }
        }).start();
    }

    private String statusMessage(Cursor c, int bytesDownloaded, int bytesTotal) {
        String msg = "???";
        switch (c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
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
                msg = "Download in progress! " + bytesDownloaded + " / " + bytesTotal;
                break;
            case DownloadManager.STATUS_SUCCESSFUL:
                msg = "Download complete! " + bytesDownloaded + " / " + bytesTotal;
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
			Log.i(LCAT,"fire error event..");
			callbackError.call(getKrollObject(), event);
		}
	}

	public void done(String downloadId, String finalPath, String sourceUrl, int bytesTotal) {
		if (callbackSuccess!=null){
			HashMap<String,String> event = new HashMap<String, String>();
			event.put("uid",downloadId);
			event.put("path",finalPath);
			event.put("url",sourceUrl);
			event.put("bytes",TiConvert.toString(bytesTotal));
			// Log.i(LCAT,"fire success event..: " + TiConvert.toString(event));
			callbackSuccess.call(getKrollObject(), event);
		}
	}

	public void cancel() {
		
		//Intent pageView = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
		//pageView.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		//appContext.startActivity(pageView);
	}

	public void startDownloadManager(KrollDict dict) {        
		dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);

		// TODO: Add support for an array of urls... Limit concurrent downloads?

		DownloadManager.Request dmReq = new DownloadManager.Request(Uri.parse(TiConvert.toString(dict, "url")));
		dmReq.setTitle( TiConvert.toString(dict, "title")  );
		dmReq.setDescription(TiConvert.toString(dict, "description") );
		
		// Log.i(LCAT,"Download to " + TiConvert.toString(dict, "filename"));
		TiBaseFile file = TiFileFactory.createTitaniumFile(new String[] { TiConvert.toString(dict, "filename") }, false);
		dmReq.setDestinationUri(Uri.fromFile(file.getNativeFile()));
		downloadQueueId = dMgr.enqueue(dmReq);

		Log.i(LCAT,"URL added download to queue. id: " + TiConvert.toString(downloadQueueId));
		
		startProgress();
		
	}

	// Methods
	@Kroll.method
	public void startDownload(KrollDict dict) {
		callbackSuccess = (KrollFunction) dict.get("success");
		callbackError = (KrollFunction) dict.get("error");
		callbackProgress = (KrollFunction) dict.get("progress");

		startDownloadManager(dict);
	}

	// Credit: https://github.com/m1ga/com.miga.downloadmanager @ v2.1.0 
	@Kroll.method
	public Object[] getDownloads() {
		ArrayList<HashMap<String, Object>> downList = new ArrayList<HashMap<String, Object>>();
		
		DownloadManager.Query query = new DownloadManager.Query();
		if (dMgr == null){
			dMgr = (DownloadManager) appContext.getSystemService(appContext.DOWNLOAD_SERVICE);
			return downList.toArray();
		}

		Cursor c = dMgr.query(query);
		c.moveToFirst();
		while (c.moveToNext()) {
			HashMap<String, Object> dl = new HashMap<String, Object>();

			String filename = null;
			String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
			if (downloadFileLocalUri != null) {
				File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
				filename = mFile.getAbsolutePath();
			}

			int bytes_downloaded = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
			int bytes_total = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

			dl.put("status",c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS)));
			dl.put("filename", filename);
			dl.put("size_total", bytes_total);
			dl.put("size_downloaded", bytes_downloaded);

			downList.add(dl);
		}
		c.close();
		return downList.toArray();
	}
}