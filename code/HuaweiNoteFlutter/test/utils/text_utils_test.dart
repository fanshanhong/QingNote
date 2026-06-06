import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/text_utils.dart';

void main() {
  group('AppTextUtils', () {
    group('isBlankTitle', () {
      test('空字符串返回 true', () => expect(AppTextUtils.isBlankTitle(''), true));
      test('纯空白返回 true', () => expect(AppTextUtils.isBlankTitle('   '), true));
      test('有内容返回 false', () => expect(AppTextUtils.isBlankTitle('会议笔记'), false));
    });

    group('summary', () {
      test('换行替换为空格', () {
        expect(AppTextUtils.summary('line1\nline2\nline3'), 'line1 line2 line3');
      });
      test('连续空白折叠', () {
        expect(AppTextUtils.summary('a   b  c'), 'a b c');
      });
      test('截断到 maxLen', () {
        expect(AppTextUtils.summary('a' * 100).length, 60);
      });
      test('短文本不截断', () => expect(AppTextUtils.summary('hello'), 'hello'));
      test('空文本返回空', () => expect(AppTextUtils.summary(''), ''));
      test('自定义 maxLen', () {
        expect(AppTextUtils.summary('a' * 20, maxLen: 10).length, 10);
      });
    });
  });
}
