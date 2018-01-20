
// Example app for com.kosso.tidownloadmanager


// @kosso
// 19 Jan 2018


var dlmod = require('com.kosso.tidownloadmanager');


var tabGroup = Ti.UI.createTabGroup();

/**
 * Add the two created tabs to the tabGroup object.
 */
tabGroup.addTab(createTab("Home", "Go to the test tab", "assets/images/tab1.png"));
tabGroup.addTab(createPlayerTab("Test", "Test", "assets/images/tab2.png"));

/**
 * Open the tabGroup
 */
tabGroup.open();

/**
 * Creates a new Tab and configures it.
 *
 * @param  {String} title The title used in the `Ti.UI.Tab` and it's included `Ti.UI.Window`
 * @param  {String} message The title displayed in the `Ti.UI.Label`
 * @return {String} icon The icon used in the `Ti.UI.Tab`
 */
function createTab(title, message, icon) {
    var win = Ti.UI.createWindow({
        title: title,
        backgroundColor: '#fff'
    });

    var label = Ti.UI.createLabel({
        text: message,
        color: "#333",
        font: {
            fontSize: 20
        }
    });

    win.add(label);

    var tab = Ti.UI.createTab({
        title: title,
        icon: icon,
        window: win
    });

    return tab;
}



function createPlayerTab(title, message, icon) {
    var win = Ti.UI.createWindow({
        title: 'Audio Test',
        backgroundColor: '#fff',
        layout: 'vertical'
    });

    var startStopButton = Ti.UI.createButton({
        title: 'Start/Stop Streaming',
        top: 10,
        width: 200,
        height: 40
    });

    var pauseResumeButton = Ti.UI.createButton({
        title: 'Pause/Resume Streaming',
        top: 10,
        width: 200,
        height: 40,
        enabled: false
    });

    win.add(startStopButton);
    win.add(pauseResumeButton);

    // allowBackground: true on Android allows the
    // player to keep playing when the app is in the
    // background.
    var audioPlayer = Ti.Media.createAudioPlayer({
        url: 'http://stream.amazingradio.com:8000',
        allowBackground: true
    });

    startStopButton.addEventListener('click',function() {
        // When paused, playing returns false.
        // If both are false, playback is stopped.
        if (audioPlayer.playing || audioPlayer.paused) {
            audioPlayer.stop();
            pauseResumeButton.enabled = false;
            if (Ti.Platform.name === 'android')
            {
                audioPlayer.release();
            }
        } else {
            audioPlayer.start();
            pauseResumeButton.enabled = true;
        }
    });

    pauseResumeButton.addEventListener('click', function() {
        if (audioPlayer.paused) {
            audioPlayer.start();
        } else {
            audioPlayer.pause();
        }
    });

    audioPlayer.addEventListener('progress', function(e) {
        Ti.API.info('Time Played: ' + Math.round(e.progress) + ' milliseconds');
    });

    audioPlayer.addEventListener('change', function(e) {
        Ti.API.info('State: ' + e.description + ' (' + e.state + ')');
    });

    win.addEventListener('close',function() {
        audioPlayer.stop();
        if (Ti.Platform.osname === 'android')
        {
            audioPlayer.release();
        }
    });





    function requestPermissions(){
        var storagePermission = 'android.permission.READ_EXTERNAL_STORAGE';
        var wstoragePermission = 'android.permission.WRITE_EXTERNAL_STORAGE';

        var hasStoragePerm = Ti.Android.hasPermission(storagePermission);
        var permissionsToRequest = [];
        if (!hasStoragePerm) {
            permissionsToRequest.push(storagePermission);
        }
        var whasStoragePerm = Ti.Android.hasPermission(wstoragePermission);
        if (!whasStoragePerm) {
            permissionsToRequest.push(wstoragePermission);
        }

        if (permissionsToRequest.length > 0) {
            Ti.Android.requestPermissions(permissionsToRequest, function(e) {
                if (e.success) {
                    
                   console.log('SUCCESS!', e);
     
                } else {

                    console.log('ERROR: ' + e.error);
                    console.log('No permission for external storage.');

                }
            });
        }
        if(hasStoragePerm && whasStoragePerm){
            console.log('permissions already granted');
        }

    }


    var btnPerms = Ti.UI.createButton({
        title: 'check perms.',
        top: 10,
        width: 200,
        height: 40,
        enabled: false
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
        title: 'test downloads',
        top: 10,
        width: 200,
        height: 40,
        enabled: false
    });

    var index = 0;

    btnDownload.addEventListener('click', function() {
        

        startDownloads();


    });

    win.add(btnDownload);

    
    function startDownloads(){
        

        function onDone(e){
            console.log('SUCCESS:', e);
            // 
        }

        function onError(e){
            console.log('ERROR: ', e);
            // 
        }
       function onProgress(e){
            console.log('progress: ', e);
            // 
        }

        downloadFile(urls[0], onDone, onError, onProgress);

        downloadFile(urls[1], onDone, onError, onProgress);
        
        downloadFile(urls[2], onDone, onError, onProgress);
        
        downloadFile(urls[3], onDone, onError, onProgress);


    }

    function downloadFile(url, onDone, onError, onProgress){

        console.log('downloadFile URL  :'+url);

        var filename = url.substring(url.lastIndexOf('/') + 1);

        console.log('FILENAME :'+filename);

        // External Storage required
        var _file = Ti.Filesystem.getFile(Ti.Filesystem.externalStorageDirectory, filename);
        var file = _file.nativePath;
        
        console.log('DEST :'+file);

        // ** NOTE : Internal Application Data Storage *NOT* supported with builtin DownloadManager
        // var _file = Ti.Filesystem.getFile(Ti.Filesystem.applicationDataDirectory, filename);
        
        // TODO: we could move it after, if we allow the option to choose internal/external storage.

        if(_file.exists()){
            console.log('deleting existing file');
            _file.deleteFile();
        }
        _file = null;

        console.log('startDownload... ');

        dlmod.startDownload({
            url: url,
            filename: file,
            success: onDone,
            error: onError,
            progress: onProgress,
            title: 'Download',
            description: 'Download ' + filename
        });

        

    }



    var tab = Ti.UI.createTab({
        title: title,
        icon: icon,
        window: win
    });

    return tab;
}


    


