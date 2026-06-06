import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/folder.dart';

void main() {
  group('Folder', () {
    test('creates with defaults', () {
      final f = Folder(id: 0, name: 'Test');
      expect(f.orderIndex, 0);
      expect(f.isDefault, false);
      expect(f.deletedAt, 0);
    });
  });
}
