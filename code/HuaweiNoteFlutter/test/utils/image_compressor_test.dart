import 'dart:io';
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/image_compressor.dart';

void main() {
  group('ImageCompressor', () {
    test('computeScaledSize - 横向大图应缩放到 maxDimension', () {
      final result = ImageCompressor.computeScaledSize(
        width: 3840,
        height: 2160,
        maxDimension: 1920,
      );
      expect(result.width, 1920);
      expect(result.height, 1080);
    });

    test('computeScaledSize - 纵向大图应缩放到 maxDimension', () {
      final result = ImageCompressor.computeScaledSize(
        width: 1080,
        height: 3840,
        maxDimension: 1920,
      );
      expect(result.width, 540);
      expect(result.height, 1920);
    });

    test('computeScaledSize - 小图不缩放', () {
      final result = ImageCompressor.computeScaledSize(
        width: 800,
        height: 600,
        maxDimension: 1920,
      );
      expect(result.width, 800);
      expect(result.height, 600);
    });

    test('computeScaledSize - 正方形大图', () {
      final result = ImageCompressor.computeScaledSize(
        width: 4000,
        height: 4000,
        maxDimension: 1920,
      );
      expect(result.width, 1920);
      expect(result.height, 1920);
    });

    test('computeScaledSize - 刚好等于 maxDimension 不缩放', () {
      final result = ImageCompressor.computeScaledSize(
        width: 1920,
        height: 1080,
        maxDimension: 1920,
      );
      expect(result.width, 1920);
      expect(result.height, 1080);
    });
  });
}
