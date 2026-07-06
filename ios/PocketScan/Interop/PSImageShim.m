//
//  PSImageShim.m
//  PocketScan (iOS parity target)
//
//  ⚠️ THIN OBJECTIVE-C INTEROP SHIM — see header for rationale.
//

#import "PSImageShim.h"

@implementation PSImageShim

+ (nullable CGImageRef)preprocessedImageFromCGImage:(CGImageRef)image
    CF_RETURNS_RETAINED
{
    if (image == NULL) {
        return NULL;
    }

    const size_t width = CGImageGetWidth(image);
    const size_t height = CGImageGetHeight(image);
    if (width == 0 || height == 0) {
        return NULL;
    }

    // Render into an 8-bit grayscale context (device gray colorspace).
    CGColorSpaceRef gray = CGColorSpaceCreateDeviceGray();
    CGContextRef ctx = CGBitmapContextCreate(NULL,
                                             width,
                                             height,
                                             8,
                                             width, // bytesPerRow for 1 channel
                                             gray,
                                             (CGBitmapInfo)kCGImageAlphaNone);
    if (ctx == NULL) {
        CGColorSpaceRelease(gray);
        return NULL;
    }

    CGContextDrawImage(ctx, CGRectMake(0, 0, width, height), image);

    // Simple contrast stretch on the raw grayscale bytes.
    unsigned char *data = (unsigned char *)CGBitmapContextGetData(ctx);
    if (data != NULL) {
        const size_t count = width * height;
        for (size_t i = 0; i < count; i++) {
            // Boost contrast around mid-gray (128) and clamp.
            int v = (int)data[i];
            int boosted = (int)((v - 128) * 1.4) + 128;
            if (boosted < 0) boosted = 0;
            if (boosted > 255) boosted = 255;
            data[i] = (unsigned char)boosted;
        }
    }

    CGImageRef result = CGBitmapContextCreateImage(ctx);
    CGContextRelease(ctx);
    CGColorSpaceRelease(gray);
    return result; // CF_RETURNS_RETAINED — caller owns it.
}

#if TARGET_OS_IPHONE
+ (nullable UIImage *)preprocessedUIImage:(UIImage *)image
{
    CGImageRef src = image.CGImage;
    if (src == NULL) {
        return nil;
    }
    CGImageRef processed = [self preprocessedImageFromCGImage:src];
    if (processed == NULL) {
        return nil;
    }
    UIImage *out = [UIImage imageWithCGImage:processed
                                       scale:image.scale
                                 orientation:image.imageOrientation];
    CGImageRelease(processed);
    return out;
}
#endif

+ (NSString *)shimDescription
{
    return @"PSImageShim — thin Objective-C Core Graphics preprocessing shim "
           @"(grayscale + contrast) bridging into the Swift/Vision OCR path.";
}

@end
