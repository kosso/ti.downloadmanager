//
//  TWRDownloadManager.h
//  DownloadManager
//
//  Created by Michelangelo Chasseur on 25/07/14.
//  Copyright (c) 2014 Touchware. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreGraphics/CGBase.h>
#import "TWRDownloadObject.h"

@interface TWRDownloadManager : NSObject

@property (nonatomic, strong) void(^backgroundTransferCompletionHandler)(void);
@property (nonatomic, strong) NSString* userAgent;

+ (instancetype)sharedManager;

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
      enableBackgroundMode:(BOOL)backgroundMode;

- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
                  withName:(NSString *)fileName
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode;

- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode;

- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
             progressBlock:(TWRDownloadProgressBlock)progressBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode;

#pragma mark - Download with estimated time

- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
                  withName:(NSString *)fileName
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode;

- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
          inDirectoryNamed:(NSString *)directory
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode;

- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
      enableBackgroundMode:(BOOL)backgroundMode;

// Kosso
- (void)downloadFileForURL:(NSString *)url
                   withUID:(int)uid
                  withName:(NSString *)fileName
             progressBlock:(TWRDownloadProgressBlock)progressBlock
             remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
           completionBlock:(TWRDownloadCompletionBlock)completionBlock
                errorBlock:(TWRDownloadErrorBlock)errorBlock
      enableBackgroundMode:(BOOL)backgroundMode;


- (void)cancelAllDownloads;
- (void)cancelDownloadForUrl:(NSString *)fileIdentifier;
// Kosso
- (void)cancelDownloadForUID:(int)uid;


- (void)cleanDirectoryNamed:(NSString *)directory;
- (void)cleanTmpDirectory;


- (BOOL)isFileDownloadingForUrl:(NSString *)fileIdentifier;
- (BOOL)isFileDownloadingForUrl:(NSString *)url withProgressBlock:(TWRDownloadProgressBlock)block;
- (BOOL)isFileDownloadingForUrl:(NSString *)url withProgressBlock:(TWRDownloadProgressBlock)block completionBlock:(TWRDownloadCompletionBlock)completionBlock;

- (NSString *)localPathForFile:(NSString *)fileIdentifier;
- (NSString *)localPathForFile:(NSString *)fileIdentifier inDirectory:(NSString *)directoryName;

- (BOOL)fileExistsForUrl:(NSString *)urlString;
- (BOOL)fileExistsForUrl:(NSString *)urlString inDirectory:(NSString *)directoryName;
- (BOOL)fileExistsWithName:(NSString *)fileName;
- (BOOL)fileExistsWithName:(NSString *)fileName inDirectory:(NSString *)directoryName;

- (BOOL)deleteFileForUrl:(NSString *)urlString;
- (BOOL)deleteFileForUrl:(NSString *)urlString inDirectory:(NSString *)directoryName;
- (BOOL)deleteFileWithName:(NSString *)fileName;
- (BOOL)deleteFileWithName:(NSString *)fileName inDirectory:(NSString *)directoryName;

/**
 *  This method helps checking which downloads are currently ongoing.
 *
 *  @return an NSArray of NSString with the URLs of the currently downloading files.
 */
- (NSArray *)currentDownloads;
- (NSArray *)currentDownloadsInfo;


@end

