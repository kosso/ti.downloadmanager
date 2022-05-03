package com.kosso.tidownloadmanager;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.DownloadManager;
import android.database.Cursor;
import java.io.File;
import android.net.Uri;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.util.TiConvert;
import android.webkit.URLUtil;

@SuppressLint("Range")
public class ServiceReceiver extends BroadcastReceiver {
	
	TidownloadmanagerModule _module = null;

	private static final String LCAT = "TidownloadmanagerModule";
	public ServiceReceiver(TidownloadmanagerModule module) {
		_module = module;
	}

	@Override
	public void onReceive(Context ctxt, Intent intent) {

		String action = intent.getAction();
		
		if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {

			DownloadManager downloadManager = (DownloadManager)ctxt.getSystemService(Context.DOWNLOAD_SERVICE);
			long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
			//Log.d(LCAT, "downloadId was: " + TiConvert.toString(downloadId));
			DownloadManager.Query query = new DownloadManager.Query();
			query.setFilterById(downloadId);
			Cursor c = downloadManager.query(query);
			if(c != null) {
				if (c.moveToFirst()) {
					int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);

					if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
						int bytesDownloaded = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
						int bytesTotal = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
						String _downloadId = c.getString(c.getColumnIndex(DownloadManager.COLUMN_ID));
						String sourceUrl = c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI));
						String filePath = null;
						String fileName = null;
						String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
						if (downloadFileLocalUri != null) {
							File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
							filePath = mFile.getAbsolutePath();
							fileName = mFile.getName();
						}
						
						_module.done(_downloadId, sourceUrl, bytesTotal, filePath, fileName);
					} else if (DownloadManager.STATUS_FAILED == c.getInt(columnIndex)) {
						String _downloadId = c.getString(c.getColumnIndex(DownloadManager.COLUMN_ID));
						String sourceUrl = c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI));
						String filePath = null;
						String fileName = null;
						String downloadFileLocalUri = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
						
						if (downloadFileLocalUri != null) {
							File mFile = new File(Uri.parse(downloadFileLocalUri).getPath());
							filePath = mFile.getAbsolutePath();
							fileName = mFile.getName();
						} else {
							// Android has this useful Util method.
							fileName = URLUtil.guessFileName(sourceUrl, null, null);
						}
						_module.error(_downloadId, sourceUrl, filePath, fileName);
						
					}
				}
			}
			c.close();

		} else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
			_module.cancel();	
		} 
  }
}