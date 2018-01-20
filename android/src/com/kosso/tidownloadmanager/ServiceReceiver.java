
package com.kosso.tidownloadmanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.DownloadManager;
import org.appcelerator.kroll.common.Log;
import android.database.Cursor;


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
			DownloadManager.Query query = new DownloadManager.Query();
			query.setFilterById(downloadId);
			Cursor c = downloadManager.query(query);
			if(c != null) {
				if (c.moveToFirst()) {
					int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
					if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
						String _downloadId = c.getString(c.getColumnIndex(DownloadManager.COLUMN_ID));
						String sourceUrl = c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI));
						String localFileUrl = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
						int bytesTotal = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
						_module.done(_downloadId, localFileUrl, sourceUrl, bytesTotal);
					}
				}
				c.close();
			}

		} else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
			_module.cancel();	
		} 
    }

}
