import 'package:flutter/material.dart';

class AppColorUtils {
  AppColorUtils._();

  static Color? parseHex(String hex) {
    final cleaned = hex.replaceFirst('#', '');
    if (cleaned.length != 6) return null;
    final value = int.tryParse(cleaned, radix: 16);
    if (value == null) return null;
    return Color(0xFF000000 | value);
  }

  static Color tint(Color c, int alpha) {
    return Color.fromARGB(
      alpha,
      (c.r * 255.0).round().clamp(0, 255),
      (c.g * 255.0).round().clamp(0, 255),
      (c.b * 255.0).round().clamp(0, 255),
    );
  }
}
