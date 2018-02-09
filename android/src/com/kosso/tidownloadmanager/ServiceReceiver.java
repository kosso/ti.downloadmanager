package com.kosso.tidownloadmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.DownloadManager;
import android.database.Cursor;
import java.io.File;
import android.net.Uri;
import org.appcelerator.kroll.common.Log;


public class ServiceReceiver extends BroadcastReceiver {
	
	TidownloadmanagerModule _module = null;

	private static final String LCAT = "TidownloadmanagerModule";
	public ServiceReceiver(TidownloadmanagerModule module) {
		_module = module;
	}

	@Override
	public void onReceive(Context ctxt, Intent intent) {
		String action = intent.getAction();
		// Log.d(LCAT, "onReceive Intent action: "+action);
		if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
			DownloadManager downloadManager = (DownloadManager)ctxt.getSystemService(Context.DOWNLOAD_SERVICE);
			long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
			DownloadManager.Query query = new DownloadManager.Query();
			query.setFilterById(downloadId);
			Cursor c = downloadManager.query(query);
			if(c != null) {
				if (c.moveToFirst()) {
					int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
					if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
						int bytesDownloaded = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
						int bytesTotal = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
						if(bytesDownloaded < bytesTotal){
							Log.d(LCAT, "THE DOWNLOAD DID NOT COMPLETE! ");							
						}
						String _downloadId = c.getString(c.getColumnIndex(DownloadManager.COLUMN_ID));
						String sourceUrl = c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI));
						// String localFileUrl = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
						String filePath = null;
						String fileName = null;
						String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
						if (downloadFileLocalUri != null) {
							File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
							filePath = mFile.getAbsolutePath();
							fileName = mFile.getName();
						}
						//String title = c.getString(c.getColumnIndex(DownloadManager.COLUMN_TITLE));
						String description = c.getString(c.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION));
						// Log.d(LCAT, "sending done.. "); 
						_module.done(_downloadId, sourceUrl, bytesTotal, filePath, fileName, description);
					}
				}
				c.close();
			}
		} else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
			_module.cancel();	
		} 
	}
}
