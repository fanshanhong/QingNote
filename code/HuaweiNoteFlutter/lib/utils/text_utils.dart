class AppTextUtils {
  AppTextUtils._();

  static bool isBlankTitle(String title) => title.trim().isEmpty;

  static String summary(String plainText, {int maxLen = 60}) {
    final flattened =
        plainText.replaceAll('\n', ' ').replaceAll(RegExp(r'\s+'), ' ').trim();
    return flattened.length <= maxLen ? flattened : flattened.substring(0, maxLen);
  }
}
