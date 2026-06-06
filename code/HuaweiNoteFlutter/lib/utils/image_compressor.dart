import 'dart:io';
import 'dart:typed_data';
import 'package:flutter_image_compress/flutter_image_compress.dart';

class ScaledSize {
  final int width;
  final int height;
  const ScaledSize(this.width, this.height);
}

class ImageCompressor {
  static ScaledSize computeScaledSize({
    required int width,
    required int height,
    int maxDimension = 1920,
  }) {
    final longer = width >= height ? width : height;
    if (longer <= maxDimension) return ScaledSize(width, height);
    final ratio = maxDimension / longer;
    return ScaledSize(
      (width * ratio).round(),
      (height * ratio).round(),
    );
  }

  static Future<Uint8List?> compress(
    File source, {
    int maxDimension = 1920,
    int quality = 85,
  }) async {
    final result = await FlutterImageCompress.compressWithFile(
      source.absolute.path,
      minWidth: maxDimension,
      minHeight: maxDimension,
      quality: quality,
      format: CompressFormat.jpeg,
    );
    return result;
  }
}
