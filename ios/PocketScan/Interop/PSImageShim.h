//
//  PSImageShim.h
//  PocketScan (iOS parity target)
//
//  ⚠️ THIS IS A THIN OBJECTIVE-C INTEROP SHIM.
//
//  It exists purely to demonstrate Swift <-> Objective-C interop in the iOS
//  parity target: a small, legacy-style image-preprocessing helper that Swift
//  code calls before handing a frame to the Vision OCR request. Real, heavy
//  computer-vision work belongs in the Android flagship (OpenCV); here we keep
//  the shim deliberately minimal (grayscale + contrast) so the interop surface
//  is clear and the file stays reviewable.
//

#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

#if TARGET_OS_IPHONE
#import <UIKit/UIKit.h>
#endif

NS_ASSUME_NONNULL_BEGIN

/// Objective-C preprocessing shim consumed by the Swift layer via the
/// bridging header. Named with a `PS` prefix (PocketScan) per Obj-C convention.
@interface PSImageShim : NSObject

/// Returns a grayscale, contrast-boosted copy of @c image suitable for OCR.
/// Implemented with Core Graphics only (no third-party deps).
+ (nullable CGImageRef)preprocessedImageFromCGImage:(CGImageRef)image
    CF_RETURNS_RETAINED;

#if TARGET_OS_IPHONE
/// Convenience wrapper operating on a UIImage. Returns nil on failure.
+ (nullable UIImage *)preprocessedUIImage:(UIImage *)image;
#endif

/// Identifies this component as a shim at runtime (used in the About screen).
@property (class, nonatomic, readonly) NSString *shimDescription;

@end

NS_ASSUME_NONNULL_END
