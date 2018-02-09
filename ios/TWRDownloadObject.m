//
//  TWRDownloadObject.m
//  DownloadManager
//
//  Created by Michelangelo Chasseur on 26/07/14.
//  Copyright (c) 2014 Touchware. All rights reserved.
//

#import "TWRDownloadObject.h"

@implementation TWRDownloadObject

- (instancetype)initWithDownloadTask:(NSURLSessionTask *)downloadTask
                             withUID:(int)uid
                       progressBlock:(TWRDownloadProgressBlock)progressBlock
                         cancelBlock:(TWRDownloadCancelationBlock)cancelBlock
                          errorBlock:(TWRDownloadErrorBlock)errorBlock
                       remainingTime:(TWRDownloadRemainingTimeBlock)remainingTimeBlock
                     completionBlock:(TWRDownloadCompletionBlock)completionBlock {
    self = [super init];
    if (self) {
        self.uid = uid;
        self.isRedownload = NO;
        self.downloadTask = downloadTask;
        self.progressBlock = progressBlock;
        self.remainingTimeBlock = remainingTimeBlock;
        self.completionBlock = completionBlock;
        self.cancelationBlock = cancelBlock;
        self.errorBlock = errorBlock;
    }
    return self;
}

@end
