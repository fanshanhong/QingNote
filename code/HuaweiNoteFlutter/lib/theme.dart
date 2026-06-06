import 'package:flutter/material.dart';

class AppColors {
  AppColors._();
  static const primary = Color(0xFF007DFF);
  static const primaryLight = Color(0xFFE3F2FD);
  static const textPrimary = Color(0xFF212121);
  static const textSecondary = Color(0xFF666666);
  static const textHint = Color(0xFF9E9E9E);
  static const bgWindow = Color(0xFFFAFAFA);
  static const bgCard = Color(0xFFFFFFFF);
  static const divider = Color(0xFFEEEEEE);
  static const cardStroke = Color(0xFFE8E8E8);
  static const bgLinen = Color(0xFFF5F0E8);
  static const bgKraft = Color(0xFFE8D5B7);
  static const bgGrid = Color(0xFFF8F8F8);
}

class AppDimens {
  AppDimens._();
  static const spacingXs = 4.0;
  static const spacingS = 8.0;
  static const spacingM = 12.0;
  static const spacingL = 16.0;
  static const spacingXl = 24.0;
  static const radiusCard = 10.0;
  static const textTitle = 16.0;
  static const textBody = 14.0;
  static const textCaption = 12.0;
  static const textHint = 11.0;
  static const headerTitleSize = 26.0;
  static const bottomNavHeight = 56.0;
}

ThemeData buildAppTheme() {
  return ThemeData(
    colorScheme: ColorScheme.light(
      primary: AppColors.primary,
      surface: AppColors.bgWindow,
    ),
    scaffoldBackgroundColor: AppColors.bgWindow,
    dividerColor: AppColors.divider,
    floatingActionButtonTheme: const FloatingActionButtonThemeData(
      backgroundColor: AppColors.primary,
      foregroundColor: Colors.white,
    ),
  );
}
