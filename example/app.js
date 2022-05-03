var dlmod = require('com.kosso.tidownloadmanager');

var win = Ti.UI.createWindow({});

// NB : Andrdoid: You must declare these in tiapp.xml

function requestPermissions() {
	var storagePermission = 'android.permission.READ_EXTERNAL_STORAGE';
	var wstoragePermission = 'android.permission.WRITE_EXTERNAL_STORAGE';
	var silentstoragePermission = 'android.permission.DOWNLOAD_WITHOUT_NOTIFICATION';
	var permissionsToRequest = [];
	permissionsToRequest.push(storagePermission);
	permissionsToRequest.push(wstoragePermission);
	permissionsToRequest.push(silentstoragePermission);

	var hasStoragePerm = Ti.Android.hasPermission(storagePermission);
	var whasStoragePerm = Ti.Android.hasPermission(wstoragePermission);
	var silenthasStoragePerm = Ti.Android.hasPermission(silentstoragePermission);

	if (hasStoragePerm && whasStoragePerm && silenthasStoragePerm) {
		console.log('PERMISSION ALREADY GRANTED');
		return;
	}

	if (permissionsToRequest.length > 0) {
		Ti.Android.requestPermissions(permissionsToRequest, function(e) {
			if (e.success) {
				console.log('SUCCESS!', e);
				return;
			} else {
				console.log('ERROR: ' + e.error);
				return;
			}
		});
	}

}

var btnPerms = Ti.UI.createButton({
	title: 'check permissions',
	top: 10,
	width: 200,
	height: 40,
});

btnPerms.addEventListener('click', function() {
	console.log('requestPermissions... ');
	requestPermissions();
});
win.add(btnPerms);


var urls = [
	'http://qrdio.com/_intest/test.m4a',
	'http://qrdio.com/_intest/tune1.mp3',
	'http://qrdio.com/_intest/tune2.mp3',
	'http://qrdio.com/_intest/tune3.mp3'
];

var btnDownload = Ti.UI.createButton({
	title: 'start downloads',
	top: 60,
	width: 200,
	height: 40,
});

var index = 0;

btnDownload.addEventListener('click', function() {
	startDownloads();
});

win.add(btnDownload);


function startDownloads() {

	function onDone(e) {
		console.log('SUCCESS:', e);
		//
	}

	function onError(e) {
		console.log('ERROR: ', e);
	}

	function onProgress(e) {
		console.log('progress: ', e);
	}

	downloadFile(urls[0], onDone, onError, onProgress);
	downloadFile(urls[1], onDone, onError, onProgress);
	downloadFile(urls[2], onDone, onError, onProgress);
	downloadFile(urls[3], onDone, onError, onProgress);
}

function downloadFile(url, onDone, onError, onProgress) {
	console.log('downloadFile URL  :' + url);
	var filename = url.substring(url.lastIndexOf('/') + 1);
	console.log('FILENAME :' + filename);

	// External Storage required
	var _file = Ti.Filesystem.getFile(Ti.Filesystem.externalStorageDirectory, filename);
	var file = _file.nativePath;

	console.log('DEST :' + file);

	// ** NOTE : Internal Application Data Storage *NOT* supported with builtin DownloadManager
	// var _file = Ti.Filesystem.getFile(Ti.Filesystem.applicationDataDirectory, filename);
	// TODO: we could move it after, if we allow the option to choose internal/external storage.

	if (_file.exists()) {
		console.log('deleting existing file');
		_file.deleteFile();
	}
	_file = null;

	console.log('call downloadFile in module ... ');

	dlmod.downloadFile({
		url: url,
		path: file,
		success: onDone,
		error: onError,
		progress: onProgress
	});
}

win.open();
