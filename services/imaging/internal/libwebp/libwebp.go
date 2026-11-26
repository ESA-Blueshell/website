// Package libwebp calls libwebp's encoder and decides nothing: gobco cannot instrument cgo, so every
// branch lives in convert, which reads the result codes.
package libwebp

/*
#cgo LDFLAGS: -lwebpmux -lwebp -lsharpyuv -lm
#cgo darwin CFLAGS: -I/opt/homebrew/include
#cgo darwin LDFLAGS: -L/opt/homebrew/lib

#include <stdbool.h>
#include <stdlib.h>
#include <webp/encode.h>
#include <webp/mux.h>

static int refused(const WebPPicture* picture) {
  return picture->error_code != VP8_ENC_OK ? (int)picture->error_code : -1;
}

// Mirrors cwebp's defaults, so output matches what the api's cwebp wrote.
static int encode(const uint8_t* rgba, int width, int height, int stride,
                  int target_width, int target_height, bool lossless, float quality,
                  uint8_t** out, size_t* out_size) {
  WebPConfig config;
  WebPPicture picture;
  WebPMemoryWriter writer;
  int ok;
  const int resized = target_width != width || target_height != height;

  *out = NULL;
  *out_size = 0;

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

// Each frame is the whole displayed canvas; the encoder stores only what changed.
static WebPAnimEncoder* animation_new(int width, int height, bool lossless, float quality, WebPConfig* config) {
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
  *out = NULL;
  *out_size = 0;
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

import "unsafe"

// Encode answers the WebP and libwebp's code, zero on success.
func Encode(pix []byte, stride, width, height, targetWidth, targetHeight int, lossless bool, quality float32) ([]byte, int) {
	var out *C.uint8_t
	var size C.size_t
	code := C.encode(
		(*C.uint8_t)(unsafe.Pointer(&pix[0])), C.int(width), C.int(height), C.int(stride),
		C.int(targetWidth), C.int(targetHeight), C.bool(lossless), C.float(quality),
		&out, &size,
	)
	defer C.WebPFree(unsafe.Pointer(out))
	return C.GoBytes(unsafe.Pointer(out), C.int(size)), int(code)
}

// Animation encodes canvases into an animation at the target size.
type Animation struct {
	encoder       *C.WebPAnimEncoder
	config        C.WebPConfig
	width, height int
}

// NewAnimation may be refused; check Refused.
func NewAnimation(width, height int, lossless bool, quality float32) *Animation {
	a := &Animation{width: width, height: height}
	a.encoder = C.animation_new(C.int(width), C.int(height), C.bool(lossless), C.float(quality), &a.config)
	return a
}

// Refused is whether libwebp declined to make the encoder.
func (a *Animation) Refused() bool {
	return a.encoder == nil
}

// Add answers libwebp's code.
func (a *Animation) Add(pix []byte, width, height, stride, timestamp int) int {
	return int(C.animation_add(a.encoder, &a.config, (*C.uint8_t)(unsafe.Pointer(&pix[0])),
		C.int(width), C.int(height), C.int(stride), C.int(a.width), C.int(a.height), C.int(timestamp)))
}

// Assemble answers the animation and libwebp's code.
func (a *Animation) Assemble(end int) ([]byte, int) {
	var out *C.uint8_t
	var size C.size_t
	code := C.animation_assemble(a.encoder, C.int(end), &out, &size)
	defer C.WebPFree(unsafe.Pointer(out))
	return C.GoBytes(unsafe.Pointer(out), C.int(size)), int(code)
}

// Close frees the encoder.
func (a *Animation) Close() {
	C.WebPAnimEncoderDelete(a.encoder)
}
