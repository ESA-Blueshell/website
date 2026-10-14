package convert

/*
#cgo LDFLAGS: -lwebp -lsharpyuv -lm
#cgo darwin CFLAGS: -I/opt/homebrew/include
#cgo darwin LDFLAGS: -L/opt/homebrew/lib

#include <stdlib.h>
#include <webp/encode.h>

static int refused(const WebPPicture* picture) {
  return picture->error_code != VP8_ENC_OK ? (int)picture->error_code : -1;
}

// What cwebp does with its defaults, so a picture comes out as it did when the api ran cwebp: the
// picture is kept as ARGB where it is lossless or resized and converted to YUV on import
// otherwise, rescaled with libwebp's own rescaler, and encoded at the given quality.
static int encode(const uint8_t* rgba, int width, int height, int stride,
                  int target_width, int target_height, int lossless, float quality,
                  uint8_t** out, size_t* out_size) {
  WebPConfig config;
  WebPPicture picture;
  WebPMemoryWriter writer;
  int ok;
  const int resized = target_width != width || target_height != height;

  if (!WebPConfigInit(&config)) return -1;
  config.lossless = lossless;
  config.quality = quality;
  if (!WebPValidateConfig(&config)) return -1;

  if (!WebPPictureInit(&picture)) return -1;
  picture.use_argb = lossless || resized;
  picture.width = width;
  picture.height = height;
  if (!WebPPictureImportRGBA(&picture, rgba, stride)) {
    WebPPictureFree(&picture);
    return refused(&picture);
  }
  if (resized && !WebPPictureRescale(&picture, target_width, target_height)) {
    WebPPictureFree(&picture);
    return refused(&picture);
  }

  WebPMemoryWriterInit(&writer);
  picture.writer = WebPMemoryWrite;
  picture.custom_ptr = &writer;
  ok = WebPEncode(&config, &picture);
  WebPPictureFree(&picture);
  if (!ok) {
    WebPMemoryWriterClear(&writer);
    return refused(&picture);
  }
  *out = writer.mem;
  *out_size = writer.size;
  return 0;
}
*/
import "C"

import (
	"fmt"
	"unsafe"
)

// encode writes straight-alpha RGBA pixels as WebP at the target size.
func encode(pix []byte, stride, width, height, targetWidth, targetHeight int, lossless bool, quality float32) ([]byte, error) {
	var out *C.uint8_t
	var size C.size_t
	flag := C.int(0)
	if lossless {
		flag = 1
	}
	code := C.encode(
		(*C.uint8_t)(unsafe.Pointer(&pix[0])), C.int(width), C.int(height), C.int(stride),
		C.int(targetWidth), C.int(targetHeight), flag, C.float(quality),
		&out, &size,
	)
	if code != 0 {
		return nil, fmt.Errorf("libwebp refused the picture (error %d)", int(code))
	}
	defer C.WebPFree(unsafe.Pointer(out))
	return C.GoBytes(unsafe.Pointer(out), C.int(size)), nil
}
