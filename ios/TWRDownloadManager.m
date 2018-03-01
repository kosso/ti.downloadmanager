//
//  TWRDownloadManager.m
//  DownloadManager
//
//  Created by Michelangelo Chasseur on 25/07/14.
//  Copyright (c) 2014 Touchware. All rights reserved.
//

#import "TWRDownloadManager.h"
#import "TWRDownloadObject.h"
#import <UIKit/UIKit.h>

static NSString* const EtagsDefault = @"ETagsDefault";
static NSTimeInterval const progressUpdateSeconds = 0.5;

@interface TWRDownloadManager () <NSURLSessionDelegate, NSURLSessionDownloadDelegate, NSURLSessionDataDelegate>

@property (strong, nonatomic) NSURLSession *session;
@property (strong, nonatomic) NSURLSession *backgroundSession;
@property (strong, nonatomic) NSMutableDictionary *downloads;
@property (strong, nonatomic) NSMutableDictionary *downloadOperations;
// @property (strong, nonatomic) NSMutableDictionary *downloadOperationsUID;
@property (atomic) NSTimeInterval timeLastProgressUpdate;
@property (strong, nonatomic) NSMutableDictionary* urlEtags;
@property (strong, nonatomic) NSOperationQueue* downloadQueue;

@end

@implementation TWRDownloadManager

+ (instancetype)sharedManager {
    static id sharedManager = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        sharedManager = [[self alloc] init];
    });
    return sharedManager;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        // Default session
        NSURLSessionConfiguration *configuration = [NSURLSessionConfiguration defaultSessionConfiguration];
        self.session = [NSURLSession sessionWithConfiguration:configuration delegate:self delegateQueue:nil];
        
        // Background session
        NSURLSessionConfiguration *backgroundConfiguration = nil;
        
        if (floor(NSFoundationVersionNumber) > NSFoundationVersionNumber_iOS_7_1) {
            backgroundConfiguration = [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:[[NSBundle mainBundle] bundleIdentifier]];
        } else {
            backgroundConfiguration = [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:@"re.touchwa.downloadmanager"];
        }
        
        self.backgroundSession = [NSURLSession sessionWithConfiguration:backgroundConfiguration delegate:self delegateQueue:nil];
        
        self.downloads = [NSMutableDictionary new];
        self.downloadOperations = [NSMutableDictionary new];
        // self.downloadOperationsUID = [NSMutableDictionary new];
        
        NSUserDefaults* defaults = [NSUserDefaults standardUserDefaults];
        _urlEtags = [[defaults dictionaryForKey:EtagsDefault] mutableCopy];
        if (!_urlEtags)
        {
            _urlEtags = [NSMutableDictionary dictionary];
        }
        
        _downloadQueue = [NSOperationQueue new];
        [_downloadQueue setMaxConcurrentOperationCount:1];
    }
    return self;
}

#pragma mark - Downloading...

- (void)downloadFileForURL:(NSString *)urlString
                   withUID:(int)uid
                  withName:(NSString *)fileName
          inDirectoryNamed:(NSString *)directory
              friendlyName:(NSString *)friendlyName
             progressBlock:(TWRDownloadProgressBlock)progressBlock
               cancelBlock:(TWRDownloadCancelationBlock)cancelBlock
                errorBlock:(TWRDownloadErrorBlock)errorBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    
    // NSLog(@"[INFO] downloadFileForURL UID :  %d", uid);
    
    NSURL *url = [NSURL URLWithString:urlString];
    if (!fileName) {
        fileName = [urlString lastPathComponent];
    }
    
    if (!friendlyName) {
        friendlyName = fileName;
    }
    
    if (![self fileDownloadCompletedForUrl:urlString]) {
        NSLog(@"File is downloading!");
        return;
    }
    
    TWRDownloadObject *dlObject = [[TWRDownloadObject alloc] initWithDownloadTask:nil withUID:uid progressBlock:progressBlock cancelBlock:cancelBlock errorBlock:errorBlock remainingTime:remainingTimeBlock completionBlock:completionBlock];
    dlObject.isRedownload = NO;
    dlObject.uid = uid;
    [self.downloads setObject:dlObject forKey:urlString];
    
    NSBlockOperation *operation = [NSBlockOperation new];
    [operation addExecutionBlock:^{
        
        NSUInteger bytes = 0;
        if ([self fileExistsWithName:fileName inDirectory:directory]) {
            NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
            [request setHTTPMethod:@"HEAD"];
            NSHTTPURLResponse *response;
            [NSURLConnection sendSynchronousRequest: request returningResponse: &response error: nil];
            
            BOOL shouldDelete = YES;
            if ([response respondsToSelector:@selector(allHeaderFields)]) {
                NSDictionary *dictionary = [response allHeaderFields];
                
                NSString* etag = [dictionary objectForKey:@"Etag"];
                NSString* ranges = [dictionary objectForKey:@"Accept-Ranges"];
                
                if (etag && [ranges isEqualToString:@"bytes"]) {
                    if ([[_urlEtags objectForKey:urlString] isEqualToString:etag])
                    {
                        shouldDelete = NO;
                    }
                    
                    [_urlEtags setObject:etag forKey:urlString];
                    NSUserDefaults* defaults = [NSUserDefaults standardUserDefaults];
                    [defaults setObject:_urlEtags forKey:EtagsDefault];
                    [defaults synchronize];
                }
            }
            
            if (shouldDelete) {
                [self deleteFileWithName:fileName inDirectory:directory];
            }
            else {
                bytes = (int)[[[NSFileManager defaultManager] attributesOfItemAtPath:[self localPathForFile:fileName inDirectory:directory] error:nil] fileSize];
            }
        }
        
        NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
        // Kosso
        // Until we have a better way to manage to queue, we set a massive timeout so a huge queue doesn't suddenly timeout.
        request.timeoutInterval = 9999999;
        
        if (bytes > 0) {
            [request setValue:[NSString stringWithFormat:@"bytes=%lud-", (unsigned long)bytes] forHTTPHeaderField:@"Range"];
        }
        if (_userAgent) {
            [request setValue:_userAgent forHTTPHeaderField:@"User-Agent"];
        }
        
        NSURLSessionDataTask *downloadTask;
        if (backgroundMode) {
            downloadTask = [self.backgroundSession dataTaskWithRequest:request];
        } else {
            downloadTask = [self.session dataTaskWithRequest:request];
        }
        
        TWRDownloadObject *downloadObject = [[TWRDownloadObject alloc] initWithDownloadTask:downloadTask withUID:uid progressBlock:progressBlock cancelBlock:cancelBlock errorBlock:errorBlock remainingTime:remainingTimeBlock completionBlock:completionBlock];
        downloadObject.uid = uid;
        downloadObject.startDate = [NSDate date];
        downloadObject.fileName = fileName;
        downloadObject.friendlyName = friendlyName;
        downloadObject.directoryName = directory;
        downloadObject.startBytes = bytes;
        downloadObject.isRedownload = dlObject.isRedownload;
        [self.downloads setObject:downloadObject forKey:urlString];
        [downloadTask resume];
        
        self.timeLastProgressUpdate = 0;
    }];
    [self.downloadOperations addEntriesFromDictionary:@{urlString:operation}];
    
    [_downloadQueue addOperation:operation];
}

- (void)downloadFileForURL:(NSString *)urlString
                   withUID:(int)uid
                  withName:(NSString *)fileName
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    [self downloadFileForURL:urlString
                     withUID:uid
                    withName:fileName
            inDirectoryNamed:directory
                friendlyName:fileName
               progressBlock:progressBlock
                 cancelBlock:nil
                  errorBlock:nil
               remainingTime:remainingTimeBlock
             completionBlock:completionBlock
        enableBackgroundMode:backgroundMode];
}

- (void)downloadFileForURL:(NSString *)urlString
                   withUID:(int)uid
                  withName:(NSString *)fileName
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
                errorBlock:(TWRDownloadErrorBlock)errorBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    [self downloadFileForURL:urlString
                     withUID:uid
                    withName:fileName
            inDirectoryNamed:directory
                friendlyName:fileName
               progressBlock:progressBlock
                 cancelBlock:nil
                  errorBlock:errorBlock
               remainingTime:remainingTimeBlock
             completionBlock:completionBlock
        enableBackgroundMode:backgroundMode];
}


- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    [self downloadFileForURL:url
                     withUID:uid
                    withName:[url lastPathComponent]
            inDirectoryNamed:directory
               progressBlock:progressBlock
               remainingTime:remainingTimeBlock
             completionBlock:completionBlock
        enableBackgroundMode:backgroundMode];
}

- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    [self downloadFileForURL:url
                     withUID:uid
                    withName:[url lastPathComponent]
            inDirectoryNamed:nil
               progressBlock:progressBlock
               remainingTime:remainingTimeBlock
             completionBlock:completionBlock
        enableBackgroundMode:backgroundMode];
}

- (void)downloadFileForURL:(NSString *)urlString
                   withUID:(int)uid
                  withName:(NSString *)fileName
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    [self downloadFileForURL:urlString
                     withUID:uid
                    withName:fileName
            inDirectoryNamed:directory
               progressBlock:progressBlock
               remainingTime:nil
             completionBlock:completionBlock
        enableBackgroundMode:backgroundMode];
}

- (void)downloadFileForURL:(NSString *)urlString
                   withUID:(int)uid
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    // if no file name was provided, use the last path component of the URL as its name
    [self downloadFileForURL:urlString
                     withUID:uid
                    withName:[urlString lastPathComponent]
            inDirectoryNamed:directory
               progressBlock:progressBlock
             completionBlock:completionBlock
        enableBackgroundMode:backgroundMode];
}

- (void)downloadFileForURL:(NSString *)urlString
                   withUID:(int)uid
             progressBlock:(TWRDownloadProgressBlock)progressBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    [self downloadFileForURL:urlString
                     withUID:uid
            inDirectoryNamed:nil
               progressBlock:progressBlock
             completionBlock:completionBlock
        enableBackgroundMode:backgroundMode];
}

// Kosso
- (void)downloadFileForURL:(NSString *)urlString
                   withUID:(int)uid
                  withName:(NSString *)fileName
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
                errorBlock:(TWRDownloadErrorBlock)errorBlock
      enableBackgroundMode:(BOOL)backgroundMode {
    [self downloadFileForURL:urlString
                     withUID:uid
                    withName:fileName
            inDirectoryNamed:nil
               progressBlock:progressBlock
               remainingTime:remainingTimeBlock
             completionBlock:completionBlock
                  errorBlock:errorBlock
        enableBackgroundMode:backgroundMode];
}


- (void)cancelDownloadForUrl:(NSString *)fileIdentifier {
    NSOperation* op = [self.downloadOperations objectForKey:fileIdentifier];
    [op cancel];
    
    TWRDownloadObject *download = [self.downloads objectForKey:fileIdentifier];
    if (download) {
        NSString *url = download.downloadTask.originalRequest.URL.absoluteString;
        [download.downloadTask cancel];
        [self.downloads removeObjectForKey:fileIdentifier];
        
        if (download.cancelationBlock) {
            download.cancelationBlock(0, url);
        }
    }
    if (self.downloads.count == 0) {
        [self cleanTmpDirectory];
    }
}

- (void)cancelAllDownloads {
    [_downloadQueue cancelAllOperations];
    
    [self.downloads enumerateKeysAndObjectsUsingBlock:^(id key, TWRDownloadObject *download, BOOL *stop) {
        NSString *url = download.downloadTask.originalRequest.URL.absoluteString;
        [download.downloadTask cancel];
        [self.downloads removeObjectForKey:key];
        
        if (download.cancelationBlock) {
            download.cancelationBlock(0, url);
        }
    }];
    [self cleanTmpDirectory];
}

- (NSArray *)currentDownloads {
    NSMutableArray *currentDownloads = [NSMutableArray new];
    [self.downloads enumerateKeysAndObjectsUsingBlock:^(id key, TWRDownloadObject *download, BOOL *stop) {
        //NSLog(@"[INFO] >>> currentDownloads - uid: %d", download.uid);
        //NSLog(@"[INFO] >>> currentDownloads - url: %@", download.downloadTask.originalRequest.URL.absoluteString);
        //NSLog(@"[INFO] >>> currentDownloads - bytes received: %d / %d", (int)download.downloadTask.countOfBytesReceived, (int)download.downloadTask.countOfBytesExpectedToReceive);
        [currentDownloads addObject:download.downloadTask.originalRequest.URL.absoluteString];
    }];
    return currentDownloads;
}

// Kosso. Add more info to the list of of downloads.
- (NSArray *)currentDownloadsInfo {
    NSMutableArray *currentDownloads = [NSMutableArray new];
    [self.downloads enumerateKeysAndObjectsUsingBlock:^(id key, TWRDownloadObject *download, BOOL *stop) {
        //NSLog(@"[INFO] >>> currentDownloadsInfo - uid: %d", download.uid);
        //NSLog(@"[INFO] >>> currentDownloadsInfo - url: %@", download.downloadTask.originalRequest.URL.absoluteString);
        //NSLog(@"[INFO] >>> currentDownloadsInfo - bytes received: %d / %d", (int)download.downloadTask.countOfBytesReceived, (int)download.downloadTask.countOfBytesExpectedToReceive);
        
        NSNumber *uidNumber = @(download.uid);
        NSNumber *bytesReceivedNumber = @(download.downloadTask.countOfBytesReceived);
        NSNumber *bytesTotalNumber = @(download.downloadTask.countOfBytesExpectedToReceive);
        NSNumber *fakeStatus = [NSNumber numberWithInt:0]; // Kosso: For Android parity where 8 = finished, which will never be in this list.
        
        NSMutableDictionary *dict = [NSMutableDictionary dictionaryWithObjectsAndKeys:
                                     uidNumber, @"uid",
                                     download.fileName ,@"filename",
                                     bytesReceivedNumber ,@"bytes_downloaded",
                                     bytesTotalNumber, @"bytes_total",
                                     download.downloadTask.originalRequest.URL.absoluteString, @"url",
                                     fakeStatus, @"status",
                                     nil];
        
        [currentDownloads addObject:dict];
    }];
    return currentDownloads;
}

- (void)cancelDownloadForUID:(int)uid {
    // NSLog(@"[INFO] >>> cancelDownloadForUID - uid: %d", uid);
    NSMutableArray *currentDownloads = [NSMutableArray new];
    [self.downloads enumerateKeysAndObjectsUsingBlock:^(id key, TWRDownloadObject *download, BOOL *stop) {
        if(download.uid == uid){
            // NSLog(@"[INFO] >>> cancelling download for UID: %d : url: %@", download.uid, download.downloadTask.originalRequest.URL.absoluteString);
            [self cancelDownloadForUrl:download.downloadTask.originalRequest.URL.absoluteString];
        }
    }];
}

#pragma mark - NSURLSession Delegate

//- (void)URLSession:(NSURLSession *)session
//      downloadTask:(NSURLSessionDownloadTask *)downloadTask
//      didWriteData:(int64_t)bytesWritten
// totalBytesWritten:(int64_t)totalBytesWritten
//totalBytesExpectedToWrite:(int64_t)totalBytesExpectedToWrite {
//    NSString *fileIdentifier = downloadTask.originalRequest.URL.absoluteString;
//    TWRDownloadObject *download = [self.downloads objectForKey:fileIdentifier];
//    if (download.progressBlock) {
//        CGFloat progress = (CGFloat)totalBytesWritten / (CGFloat)totalBytesExpectedToWrite;
//        dispatch_async(dispatch_get_main_queue(), ^(void) {
//            if(download.progressBlock){
//                download.progressBlock(fileIdentifier, progress); //exception when progressblock is nil
//            }
//        });
//    }
//
//    CGFloat remainingTime = [self remainingTimeForDownload:download bytesTransferred:totalBytesWritten totalBytesExpectedToWrite:totalBytesExpectedToWrite];
//    if (download.remainingTimeBlock) {
//        dispatch_async(dispatch_get_main_queue(), ^(void) {
//            if (download.remainingTimeBlock) {
//                download.remainingTimeBlock(fileIdentifier, (NSUInteger)remainingTime);
//            }
//        });
//    }
//}

-(void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask didReceiveResponse:(NSURLResponse *)r completionHandler:(void (^)(NSURLSessionResponseDisposition))completionHandler
{
    NSHTTPURLResponse *response = (NSHTTPURLResponse*)r;
    
    if ([response respondsToSelector:@selector(allHeaderFields)]) {
        NSDictionary *dictionary = [response allHeaderFields];
        
        NSString* etag = [dictionary objectForKey:@"Etag"];
        if (etag)
        {
            [_urlEtags setObject:etag forKey:dataTask.originalRequest.URL.absoluteString];
            NSUserDefaults* defaults = [NSUserDefaults standardUserDefaults];
            [defaults setObject:_urlEtags forKey:EtagsDefault];
            [defaults synchronize];
        }
    }
    
    completionHandler(NSURLSessionResponseAllow);
}

// Download progress
- (void)URLSession:(NSURLSession *)session dataTask:(nonnull NSURLSessionDataTask *)dataTask didReceiveData:(nonnull NSData *)data
{
    NSString *fileIdentifier = dataTask.originalRequest.URL.absoluteString;
    TWRDownloadObject *download = [self.downloads objectForKey:fileIdentifier];
    
    NSError *error;
    NSDictionary *fileAttributes = [[NSFileManager defaultManager] attributesOfItemAtPath:[self localPathForFile:download.fileName inDirectory:download.directoryName] error:&error];
    
    long long totalBytesWritten = 0;
    if (!error)
    {
        NSNumber *fileSizeNumber = [fileAttributes objectForKey:NSFileSize];
        totalBytesWritten = [fileSizeNumber longLongValue];
    }
    totalBytesWritten += [data length];
    
    long long totalBytesExpectedToWrite = dataTask.response.expectedContentLength;
    totalBytesExpectedToWrite = totalBytesExpectedToWrite <= 0 ? -1 : totalBytesExpectedToWrite + download.startBytes;
    
    NSFileHandle *fileHandle = [NSFileHandle fileHandleForWritingAtPath:[self localPathForFile:download.fileName inDirectory:download.directoryName]];
    if (!fileHandle)
    {
        [data writeToFile:[self localPathForFile:download.fileName inDirectory:download.directoryName] atomically:YES];
    }
    else
    {
        @try {
            [fileHandle seekToEndOfFile];
            [fileHandle writeData:data];
            [fileHandle closeFile];
        }
        @catch (NSException *exception) {
            if (fileIdentifier) {
                dispatch_async(dispatch_get_main_queue(), ^(void) {
                    NSLog(@"[INFO] ERROR exception in URLSession : didReceiveData");
                    NSLog(@"[INFO] %@", [exception callStackSymbols]);
                    
                    [self cancelDownloadForUrl:fileIdentifier];
                    if (download.errorBlock) {
                        download.errorBlock(download.uid, fileIdentifier);
                    }
                });
            }
        }
    }
    
    NSTimeInterval now = [[NSDate date] timeIntervalSince1970];
    if (now - self.timeLastProgressUpdate > progressUpdateSeconds)
    {
        self.timeLastProgressUpdate = now;
        if (download.progressBlock) {
            
            CGFloat progress = (CGFloat)totalBytesWritten / (CGFloat)totalBytesExpectedToWrite;
            dispatch_async(dispatch_get_main_queue(), ^(void) {
                if(download.progressBlock){
                    // Kosso. Add bytes written and total expected btyes to progress
                    download.progressBlock(download.uid, fileIdentifier, progress, (int)totalBytesWritten, (int)totalBytesExpectedToWrite); //exception when progressblock is nil
                }
            });
        }
        
        CGFloat remainingTime = [self remainingTimeForDownload:download bytesTransferred:totalBytesWritten totalBytesExpectedToWrite:totalBytesExpectedToWrite];
        if (download.remainingTimeBlock) {
            dispatch_async(dispatch_get_main_queue(), ^(void) {
                if (download.remainingTimeBlock) {
                    download.remainingTimeBlock(download.uid, fileIdentifier, (NSUInteger)remainingTime);
                }
            });
        }
    }
}

//// Download finished
//- (void)URLSession:(NSURLSession *)session dataTask:(nonnull NSURLSessionDataTask *)dataTask willCacheResponse:(nonnull NSCachedURLResponse *)proposedResponse completionHandler:(nonnull void (^)(NSCachedURLResponse * _Nullable))completionHandler
//{
//
//}

//- (void)URLSession:(NSURLSession *)session downloadTask:(NSURLSessionDownloadTask *)downloadTask didFinishDownloadingToURL:(NSURL *)location {
////    NSLog(@"Download finisehd!");
//
//    NSError *error;
//    NSURL *destinationLocation;
//
//    NSString *fileIdentifier = downloadTask.originalRequest.URL.absoluteString;
//    TWRDownloadObject *download = [self.downloads objectForKey:fileIdentifier];
//
//     BOOL success = YES;
//
//    if ([downloadTask.response isKindOfClass:[NSHTTPURLResponse class]]) {
//        NSInteger statusCode = [(NSHTTPURLResponse*)downloadTask.response statusCode];
//        if (statusCode >= 400) {
//            NSLog(@"ERROR: HTTP status code %@", @(statusCode));
//            success = NO;
//        }
//    }
//
//    if (success) {
//        if (download.directoryName) {
//            destinationLocation = [[[self cachesDirectoryUrlPath] URLByAppendingPathComponent:download.directoryName] URLByAppendingPathComponent:download.fileName];
//        } else {
//            destinationLocation = [[self cachesDirectoryUrlPath] URLByAppendingPathComponent:download.fileName];
//        }
//
//        // Move downloaded item from tmp directory to te caches directory
//        // (not synced with user's iCloud documents)
//        [[NSFileManager defaultManager] moveItemAtURL:location
//                                                toURL:destinationLocation
//                                                error:&error];
//        if (error) {
//            NSLog(@"ERROR: %@", error);
//        }
//
//        if (download.completionBlock) {
//            dispatch_async(dispatch_get_main_queue(), ^(void) {
//                download.completionBlock(fileIdentifier);
//            });
//        }
//
//        dispatch_async(dispatch_get_main_queue(), ^{
//            // Show a local notification when download is over.
//            UILocalNotification *localNotification = [[UILocalNotification alloc] init];
//            localNotification.alertBody = [NSString stringWithFormat:@"%@ has been downloaded", download.friendlyName];
//            [[UIApplication sharedApplication] presentLocalNotificationNow:localNotification];
//        });
//    }
//    else {
//        if (download.errorBlock) {
//            dispatch_async(dispatch_get_main_queue(), ^(void) {
//                download.errorBlock(fileIdentifier);
//            });
//        }
//    }
//
//    // remove object from the download
//    [self.downloads removeObjectForKey:fileIdentifier];
//}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
    NSString *fileIdentifier = task.originalRequest.URL.absoluteString;
    TWRDownloadObject *download = [self.downloads objectForKey:fileIdentifier];
    
    BOOL success = YES;
    
    if ([task.response isKindOfClass:[NSHTTPURLResponse class]]) {
        NSInteger statusCode = [(NSHTTPURLResponse*)task.response statusCode];
        if (statusCode >= 400) {
            NSLog(@"ERROR: HTTP status code %@", @(statusCode));
            success = NO;
        }
    }
    
    if (error || !success) {
        //if(error){
        //    NSLog(@"[INFO] ERROR in URLSession didCompleteWithError : %@", error);
        //}
        if (!success) {
            //NSLog(@"[INFO] no success in URLSession");
            if ([self fileExistsWithName:download.fileName] && !download.isRedownload) {
                NSString *path = [download.fileName stringByAppendingString:@"_tmp"];
                if (path) {
                    [self.downloads removeObjectForKey:fileIdentifier];
                    [self.urlEtags removeObjectForKey:fileIdentifier];
                    
                    [self deleteFileWithName:path];
                    [self downloadFileForURL:fileIdentifier withUID: download.uid withName:path inDirectoryNamed:download.directoryName friendlyName:download.friendlyName progressBlock:download.progressBlock cancelBlock:download.cancelationBlock errorBlock:^(int uid, NSString *url) {
                        // Download failed- delete both files for good measure
                        [self deleteFileWithName:download.fileName];
                        [self deleteFileWithName:path];
                        
                        if (download.errorBlock) {
                            download.errorBlock(download.uid, url);
                        }
                    } remainingTime:download.remainingTimeBlock completionBlock:^(int uid, NSString *url, NSString *filename) {
                        
                        // Delete file in original path
                        [self deleteFileWithName:download.fileName];
                        NSError *error = nil;
                        [[NSFileManager defaultManager] moveItemAtPath:path toPath:download.fileName error:&error];
                        
                        download.completionBlock(download.uid, url, download.fileName);
                    } enableBackgroundMode:YES];
                    TWRDownloadObject *redownload = [self.downloads objectForKey:fileIdentifier];
                    redownload.isRedownload = YES;
                    return;
                }
            }
        }
        
        if (download.errorBlock) {
            dispatch_async(dispatch_get_main_queue(), ^(void) {
                download.errorBlock(download.uid, fileIdentifier);
            });
        }
    }
    else
    {
        if (fileIdentifier) {
            [_urlEtags removeObjectForKey:fileIdentifier];
        }
        NSUserDefaults* defaults = [NSUserDefaults standardUserDefaults];
        [defaults setObject:_urlEtags forKey:EtagsDefault];
        [defaults synchronize];
        
        if (download.completionBlock) {
            dispatch_async(dispatch_get_main_queue(), ^(void) {
                download.completionBlock(download.uid, fileIdentifier, download.fileName);
            });
        }
    }
    
    // remove object from the download
    if (fileIdentifier) {
        [self.downloads removeObjectForKey:fileIdentifier];
    }
}

- (CGFloat)remainingTimeForDownload:(TWRDownloadObject *)download
                   bytesTransferred:(int64_t)bytesTransferred
          totalBytesExpectedToWrite:(int64_t)totalBytesExpectedToWrite {
    NSTimeInterval timeInterval = [[NSDate date] timeIntervalSinceDate:download.startDate];
    CGFloat speed = (CGFloat)bytesTransferred / (CGFloat)timeInterval;
    CGFloat remainingBytes = totalBytesExpectedToWrite - bytesTransferred;
    CGFloat remainingTime =  remainingBytes / speed;
    return remainingTime;
}

#pragma mark - File Management

- (BOOL)createDirectoryNamed:(NSString *)directory {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
    NSString *cachesDirectory = [paths objectAtIndex:0];
    NSString *targetDirectory = [cachesDirectory stringByAppendingPathComponent:directory];
    
    NSError *error;
    return [[NSFileManager defaultManager] createDirectoryAtPath:targetDirectory
                                     withIntermediateDirectories:YES
                                                      attributes:nil
                                                           error:&error];
}

- (NSURL *)cachesDirectoryUrlPath {
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
    NSString *cachesDirectory = [paths objectAtIndex:0];
    NSURL *cachesDirectoryUrl = [NSURL fileURLWithPath:cachesDirectory];
    return cachesDirectoryUrl;
}

- (BOOL)fileDownloadCompletedForUrl:(NSString *)fileIdentifier {
    BOOL retValue = YES;
    TWRDownloadObject *download = [self.downloads objectForKey:fileIdentifier];
    if (download) {
        // downloads are removed once they finish
        retValue = NO;
    }
    return retValue;
}

- (BOOL)isFileDownloadingForUrl:(NSString *)fileIdentifier {
    return [self isFileDownloadingForUrl:fileIdentifier
                       withProgressBlock:nil];
}

- (BOOL)isFileDownloadingForUrl:(NSString *)fileIdentifier
              withProgressBlock:(TWRDownloadProgressBlock)block {
    return [self isFileDownloadingForUrl:fileIdentifier
                       withProgressBlock:block
                         completionBlock:nil];
}

- (BOOL)isFileDownloadingForUrl:(NSString *)fileIdentifier
              withProgressBlock:(TWRDownloadProgressBlock)block
                completionBlock:(TWRDownloadCompletionBlock)completionBlock {
    BOOL retValue = NO;
    TWRDownloadObject *download = [self.downloads objectForKey:fileIdentifier];
    if (download) {
        if (block) {
            download.progressBlock = block;
        }
        if (completionBlock) {
            download.completionBlock = completionBlock;
        }
        retValue = YES;
    }
    return retValue;
}

#pragma mark File existance

- (NSString *)localPathForFile:(NSString *)fileIdentifier {
    return [self localPathForFile:fileIdentifier inDirectory:nil];
}

- (NSString *)localPathForFile:(NSString *)fileIdentifier inDirectory:(NSString *)directoryName {
    NSString *fileName = [fileIdentifier lastPathComponent];
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
    NSString *cachesDirectory = [paths objectAtIndex:0];
    return [[cachesDirectory stringByAppendingPathComponent:directoryName] stringByAppendingPathComponent:fileName];
}

- (BOOL)fileExistsForUrl:(NSString *)urlString {
    return [self fileExistsForUrl:urlString inDirectory:nil];
}

- (BOOL)fileExistsForUrl:(NSString *)urlString inDirectory:(NSString *)directoryName {
    return [self fileExistsWithName:[urlString lastPathComponent] inDirectory:directoryName];
}

- (BOOL)fileExistsWithName:(NSString *)fileName
               inDirectory:(NSString *)directoryName {
    BOOL exists = NO;
    
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES);
    NSString *cachesDirectory = [paths objectAtIndex:0];
    
    // if no directory was provided, we look by default in the base cached dir
    if ([[NSFileManager defaultManager] fileExistsAtPath:[[cachesDirectory stringByAppendingPathComponent:directoryName] stringByAppendingPathComponent:fileName]]) {
        exists = YES;
    }
    
    return exists;
}

- (BOOL)fileExistsWithName:(NSString *)fileName {
    return [self fileExistsWithName:fileName inDirectory:nil];
}

#pragma mark File deletion

- (BOOL)deleteFileForUrl:(NSString *)urlString {
    return [self deleteFileForUrl:urlString inDirectory:nil];
}

- (BOOL)deleteFileForUrl:(NSString *)urlString inDirectory:(NSString *)directoryName {
    return [self deleteFileWithName:[urlString lastPathComponent] inDirectory:directoryName];
}

- (BOOL)deleteFileWithName:(NSString *)fileName {
    return [self deleteFileWithName:fileName inDirectory:nil];
}

- (BOOL)deleteFileWithName:(NSString *)fileName
               inDirectory:(NSString *)directoryName {
    BOOL deleted = NO;
    
    NSError *error;
    NSURL *fileLocation;
    if (directoryName) {
        fileLocation = [[[self cachesDirectoryUrlPath] URLByAppendingPathComponent:directoryName] URLByAppendingPathComponent:fileName];
    } else {
        fileLocation = [[self cachesDirectoryUrlPath] URLByAppendingPathComponent:fileName];
    }
    
    
    // Move downloaded item from tmp directory to te caches directory
    // (not synced with user's iCloud documents)
    [[NSFileManager defaultManager] removeItemAtURL:fileLocation error:&error];
    
    if (error) {
        deleted = NO;
        NSLog(@"Error deleting file: %@", error);
    } else {
        deleted = YES;
    }
    return deleted;
}

#pragma mark - Clean directory

- (void)cleanDirectoryNamed:(NSString *)directory {
    NSFileManager *fm = [NSFileManager defaultManager];
    NSError *error = nil;
    for (NSString *file in [fm contentsOfDirectoryAtPath:directory error:&error]) {
        [fm removeItemAtPath:[directory stringByAppendingPathComponent:file] error:&error];
    }
}

- (void)cleanTmpDirectory {
    NSArray* tmpDirectory = [[NSFileManager defaultManager] contentsOfDirectoryAtPath:NSTemporaryDirectory() error:NULL];
    for (NSString *file in tmpDirectory) {
        [[NSFileManager defaultManager] removeItemAtPath:[NSString stringWithFormat:@"%@%@", NSTemporaryDirectory(), file] error:NULL];
    }
}

#pragma mark - Background download

- (void)URLSessionDidFinishEventsForBackgroundURLSession:(NSURLSession *)session {
    // Check if all download tasks have been finished.
    [session getTasksWithCompletionHandler:^(NSArray *dataTasks, NSArray *uploadTasks, NSArray *downloadTasks) {
        if ([dataTasks count] == 0) {
            if (self.backgroundTransferCompletionHandler != nil) {
                // Copy locally the completion handler.
                void(^completionHandler)(void) = self.backgroundTransferCompletionHandler;
                
                [[NSOperationQueue mainQueue] addOperationWithBlock:^{
                    // Call the completion handler to tell the system that there are no other background transfers.
                    completionHandler();
                }];
                
                // Make nil the backgroundTransferCompletionHandler.
                self.backgroundTransferCompletionHandler = nil;
            }
        }
    }];
}

- (void)URLSession:(nonnull NSURLSession *)session downloadTask:(nonnull NSURLSessionDownloadTask *)downloadTask didFinishDownloadingToURL:(nonnull NSURL *)location {
    if (self.backgroundTransferCompletionHandler != nil) {
        // Copy locally the completion handler.
        void(^completionHandler)(void) = self.backgroundTransferCompletionHandler;
        
        [[NSOperationQueue mainQueue] addOperationWithBlock:^{
            // Call the completion handler to tell the system that there are no other background transfers.
            completionHandler();
        }];
        
        // Make nil the backgroundTransferCompletionHandler.
        self.backgroundTransferCompletionHandler = nil;
    }
}

@end

