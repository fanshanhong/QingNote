import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/category.dart';

void main() {
  group('Category', () {
    test('creates correctly', () {
      final c = Category(id: 1, name: 'Work', color: '#FDD835');
      expect(c.orderIndex, 0);
    });
  });
}
