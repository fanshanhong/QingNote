import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/models/notebook.dart';

void main() {
  group('Notebook', () {
    test('creates with defaults', () {
      final nb = Notebook(id: 0, name: 'Test', folderId: 1);
      expect(nb.color, '#9E9E9E');
      expect(nb.orderIndex, 0);
      expect(nb.isDefault, false);
      expect(nb.deletedAt, 0);
    });
  });
}
