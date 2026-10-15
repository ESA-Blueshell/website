package convert

/*
#cgo LDFLAGS: -lwebpmux -lwebp -lsharpyuv -lm
#cgo darwin CFLAGS: -I/opt/homebrew/include
#cgo darwin LDFLAGS: -L/opt/homebrew/lib

#include <stdlib.h>
#include <webp/encode.h>
#include <webp/mux.h>

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

// An animation is encoded frame by frame, each frame the whole canvas as it is displayed. The
// encoder works out which rectangle changed and whether to blend it, so the frames it stores are
// smaller than the canvas and depend on the ones before; the reader replays them.
static WebPAnimEncoder* animation_new(int width, int height, int lossless, float quality, WebPConfig* config) {
  WebPAnimEncoderOptions options;
  if (!WebPAnimEncoderOptionsInit(&options)) return NULL;
  options.anim_params.loop_count = 0;
  options.anim_params.bgcolor = 0;
  if (!WebPConfigInit(config)) return NULL;
  config->lossless = lossless;
  config->quality = quality;
  if (!WebPValidateConfig(config)) return NULL;
  return WebPAnimEncoderNew(width, height, &options);
}

static int animation_add(WebPAnimEncoder* encoder, const WebPConfig* config, const uint8_t* rgba,
                         int width, int height, int stride, int target_width, int target_height,
                         int timestamp) {
  WebPPicture picture;
  int ok;
  if (!WebPPictureInit(&picture)) return -1;
  picture.use_argb = 1;
  picture.width = width;
  picture.height = height;
  ok = WebPPictureImportRGBA(&picture, rgba, stride);
  if (ok && (target_width != width || target_height != height)) {
    ok = WebPPictureRescale(&picture, target_width, target_height);
  }
  ok = ok && WebPAnimEncoderAdd(encoder, &picture, timestamp, config);
  WebPPictureFree(&picture);
  return ok ? 0 : -1;
}

static int animation_assemble(WebPAnimEncoder* encoder, int timestamp, uint8_t** out, size_t* out_size) {
  WebPData data;
  WebPDataInit(&data);
  if (!WebPAnimEncoderAdd(encoder, NULL, timestamp, NULL) || !WebPAnimEncoderAssemble(encoder, &data)) {
    WebPDataClear(&data);
    return -1;
  }
  *out = (uint8_t*)data.bytes;
  *out_size = data.size;
  return 0;
}
*/
import "C"

import (
	"errors"
	"fmt"
	"image"
	"unsafe"
)

var errAnimationRefused = errors.New("libwebp refused the animation")

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

// animationEncoder assembles an animation at one size from canvases of another.
type animationEncoder struct {
	encoder       *C.WebPAnimEncoder
	config        C.WebPConfig
	width, height int
}

func newAnimationEncoder(width, height int, lossless bool, quality float32) (*animationEncoder, error) {
	e := &animationEncoder{width: width, height: height}
	flag := C.int(0)
	if lossless {
		flag = 1
	}
	e.encoder = C.animation_new(C.int(width), C.int(height), flag, C.float(quality), &e.config)
	if e.encoder == nil {
		return nil, errAnimationRefused
	}
	return e, nil
}

// add is canvas as the frame shown from timestamp milliseconds in.
func (e *animationEncoder) add(canvas *image.NRGBA, timestamp int) error {
	size := canvas.Rect.Size()
	if C.animation_add(e.encoder, &e.config, (*C.uint8_t)(unsafe.Pointer(&canvas.Pix[0])),
		C.int(size.X), C.int(size.Y), C.int(canvas.Stride), C.int(e.width), C.int(e.height), C.int(timestamp)) != 0 {
		return errAnimationRefused
	}
	return nil
}

// assemble is the animation, its last frame shown until end milliseconds in.
func (e *animationEncoder) assemble(end int) ([]byte, error) {
	var out *C.uint8_t
	var size C.size_t
	if C.animation_assemble(e.encoder, C.int(end), &out, &size) != 0 {
		return nil, errAnimationRefused
	}
	defer C.WebPFree(unsafe.Pointer(out))
	return C.GoBytes(unsafe.Pointer(out), C.int(size)), nil
}

func (e *animationEncoder) close() {
	C.WebPAnimEncoderDelete(e.encoder)
}
