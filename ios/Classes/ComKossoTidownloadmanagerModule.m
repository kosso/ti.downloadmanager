/**
 * TiDownloadManager
 *
 * Created by Kosso
 * Copyright (c) 2018 . All rights reserved.
 */

#import "ComKossoTidownloadmanagerModule.h"
#import "TiBase.h"
#import "TiHost.h"
#import "TiUtils.h"

@implementation ComKossoTidownloadmanagerModule

#pragma mark Internal

// This is generated for your module, please do not change it
- (id)moduleGUID
{
  return @"1bfdecfc-74c2-40d6-9d7c-f1458a878fbf";
}

// This is generated for your module, please do not change it
- (NSString *)moduleId
{
  return @"com.kosso.tidownloadmanager";
}

#pragma mark Lifecycle

- (void)startup
{
    [super startup];
    DebugLog(@"[DEBUG] %@ loaded", self);
    uid = 0;
    [[TWRDownloadManager sharedManager] cleanTmpDirectory];
}

-(void)shutdown:(id)sender
{
    // this method is called when the module is being unloaded
    // typically this is during shutdown. make sure you don't do too
    // much processing here or the app will be quit forceably
    
    // you *must* call the superclass
    [super shutdown:sender];
}

#pragma mark Listener Notifications

-(void)_listenerAdded:(NSString *)type count:(int)count
{
    if (count == 1 && [type isEqualToString:@"my_event"])
    {
        // the first (of potentially many) listener is being added
        // for event named 'my_event'
    }
}

-(void)_listenerRemoved:(NSString *)type count:(int)count
{
    if (count == 0 && [type isEqualToString:@"my_event"])
    {
        // the last listener called for event named 'my_event' has
        // been removed, we can optionally clean up any resources
        // since no body is listening at this point for that event
    }
}

- (BOOL)addSkipBackupAttributeToItemAtPath:(NSString *) filePathString
{
    NSURL* URL= [NSURL fileURLWithPath: filePathString];
    assert([[NSFileManager defaultManager] fileExistsAtPath: [URL path]]);
    NSError *error = nil;
    BOOL success = [URL setResourceValue: [NSNumber numberWithBool: YES]
                                  forKey: NSURLIsExcludedFromBackupKey error: &error];
    if(!success){
        NSLog(@"[WARN] Error excluding %@ from backup %@", [URL lastPathComponent], error);
    }
    return success;
}

#pragma Public APIs
- (void)downloadFile:(id)args
{
    ENSURE_SINGLE_ARG(args,NSDictionary);
    
    if( [args objectForKey:@"url"]==nil || [args objectForKey:@"filename"] == nil || [args objectForKey:@"path"] == nil){
        NSLog(@"[ERROR] missing parameters");
        return;
    }
    
    NSFileManager *fileManager = [[NSFileManager alloc] init];
    
    NSString *FILE_URL = [TiUtils stringValue:[args objectForKey:@"url"]];
    NSString *FILE_NAME = [TiUtils stringValue:[args objectForKey:@"filename"]];
    NSString *DESTINATION_PATH = [TiUtils stringValue:[args objectForKey:@"path"]];
    
    NSLog(@"[DEBUG] downloadFileForURL:  %@", FILE_URL);
    
    if([FILE_URL isEqualToString:@""]){
        NSLog(@"[DEBUG] NO URL:  %@", FILE_URL);
        return;
    }
    
    // Checks for file in device app Caches directory.
    if ([[TWRDownloadManager sharedManager] fileExistsForUrl:FILE_URL]) {
        NSLog(@"[DEBUG] file exists for url:  %@  .. remove it.. ", FILE_URL);
        [[TWRDownloadManager sharedManager] deleteFileForUrl:FILE_URL];
        // Will this ever be partial? If so, return a success event.
    }
    
    if ([fileManager fileExistsAtPath:DESTINATION_PATH]){
        NSLog(@"[DEBUG] file exists at path provided url:  %@  .. delete ", DESTINATION_PATH);
        
        NSError *error = nil;
        [fileManager removeItemAtPath:DESTINATION_PATH error:&error];
        
    }
    
    // Increment a 'uid' for this sesssion.
    uid++;
    // NSLog(@"[INFO] START a download with UID :  %d", uid);
    
    // STARTS THE DOWNLOAD ...
    [[TWRDownloadManager sharedManager] downloadFileForURL:FILE_URL withUID:uid withName:FILE_NAME
                                             progressBlock:^(int uid, NSString * url, CGFloat progress, int bytesWritten, int totalBytes) {
                                                 // NSLog(@"[INFO] progress %.2f : %@", progress, url);
                                                 // Send progress event if listener added
                                                 if([self _hasListeners:@"progress"]){
                                                     NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                                                            NUMINT(uid), @"uid",
                                                                            FILE_NAME, @"filename",
                                                                            DESTINATION_PATH, @"path",
                                                                            url, @"url",
                                                                            NUMDOUBLE(progress), @"progress",
                                                                            NUMINT(bytesWritten), @"bytes_downloaded",
                                                                            NUMINT(totalBytes), @"bytes_total",
                                                                            self, @"source",
                                                                            @"progress", @"type",nil];
                                                     [self fireEvent:@"progress" withObject:event];
                                                 }
                                             } remainingTime:^(int uid, NSString *url, NSUInteger seconds) {
                                                 // Estimated time left.
                                                 // Not used. No parity available on Android yet.
                                                 // NSLog(@"[INFO] ETA: %lu sec. %@", (unsigned long)seconds, FILE_URL);
                                             } completionBlock:^(int uid, NSString *url, NSString *filename) {
                                                 NSLog(@"[DEBUG] >> Download completed! : %@  %@", filename, url);
                                                 NSArray *paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
                                                 NSString *cachesDirectory = [paths objectAtIndex:0];
                                                 NSString *targetPath = [cachesDirectory stringByAppendingPathComponent:(NSString *)filename];
                                                 // Move file to final DESTINATION_PATH
                                                 int bytes_total = 0;
                                                 NSError *error;
                                                 if ( [ fileManager moveItemAtPath:targetPath toPath: DESTINATION_PATH error:&error]) {
                                                     // NSLog(@"[INFO] >> File moved to : %@", DESTINATION_PATH);
                                                     BOOL setNoBackup = [self addSkipBackupAttributeToItemAtPath:DESTINATION_PATH];
                                                     // NSLog(@"[INFO] >> File set to skip backup: %d", setNoBackup);
                                                     NSDictionary *attrs = [fileManager attributesOfItemAtPath: DESTINATION_PATH error: NULL];
                                                     unsigned long long long_bytes_total = [attrs fileSize];
                                                     bytes_total = (int)long_bytes_total;
                                                     if([self _hasListeners:@"success"]){
                                                         NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:
                                                                                NUMINT(uid), @"uid",
                                                                                filename, @"filename",
                                                                                DESTINATION_PATH, @"path",
                                                                                url, @"url",
                                                                                NUMINT(bytes_total), @"total_bytes",
                                                                                self, @"source",
                                                                                @"success", @"type",nil];
                                                         [self fireEvent:@"success" withObject:event];
                                                         
                                                         NSLog(@"[DEBUG] >> remaining currentDownloads count: %d", [[TWRDownloadManager sharedManager] currentDownloads].count);
                                                         
                                                     }
                                                 } else {
                                                     // NSLog(@"[INFO] File NOT moved to : %@", DESTINATION_PATH);
                                                     // NSLog(@"[INFO] from : %@", targetPath);
                                                     if([self _hasListeners:@"error"]){
                                                         NSDictionary *event = [[NSDictionary alloc] initWithObjectsAndKeys:
                                                                                NUMINT(uid), @"uid",
                                                                                filename, @"filename",
                                                                                DESTINATION_PATH, @"path",
                                                                                url, @"url",
                                                                                @"file_move_error", @"message",
                                                                                self, @"source",
                                                                                @"error", @"type",nil];
                                                         [self fireEvent:@"error" withObject:event];
                                                     }
                                                 }
                                             } errorBlock:^(int uid, NSString * url) {
                                                 NSLog(@"[DEBUG] Download error for url : %@", url);
                                                 // Send error event if listener added
                                                 if([self _hasListeners:@"error"]){
                                                     NSDictionary *event = [[NSDictionary alloc] initWithObjectsAndKeys:
                                                                            NUMINT(uid), @"uid",
                                                                            FILE_NAME, @"filename",
                                                                            DESTINATION_PATH, @"path",
                                                                            url, @"url",
                                                                            @"download_error", @"message",
                                                                            self, @"source",
                                                                            @"error", @"type",nil];
                                                     [self fireEvent:@"error" withObject:event];
                                                 }
                                             } enableBackgroundMode:YES];
}

- (NSArray *)getDownloads:(id)unused
{
    NSArray *allDownloads = [[TWRDownloadManager sharedManager] currentDownloadsInfo];
    // Now returns NSArray of dictionaries with more info about the download.
    // NSLog(@"[INFO] getDownloads: %@", allDownloads);
    return allDownloads;
}

- (void)cancelDownloadForUID:(id)args
{
    int DOWNLOAD_UID = [TiUtils intValue:[args objectAtIndex:0]];
    NSLog(@"[DEBUG] cancelDownloadForUID:  %d", DOWNLOAD_UID);
    [[TWRDownloadManager sharedManager] cancelDownloadForUID:DOWNLOAD_UID];
}

- (void)cancelDownloadForUrl:(id)args
{
    NSString *DOWNLOAD_URL = [TiUtils stringValue:[args objectAtIndex:0]];
    NSLog(@"[DEBUG] cancelDownloadForUrl:  %@", DOWNLOAD_URL);
    [[TWRDownloadManager sharedManager] cancelDownloadForUrl:DOWNLOAD_URL];
}

- (void)cancelAllDownloads:(id)unused
{
    NSLog(@"[DEBUG] cancelAllDownloads");
    [[TWRDownloadManager sharedManager] cancelAllDownloads];
}

@end
