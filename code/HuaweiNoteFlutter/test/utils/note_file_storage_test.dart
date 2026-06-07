import 'dart:io';
import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/note_file_storage.dart';

void main() {
  late Directory tempDir;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('note_storage_test_');
  });

  tearDown(() {
    if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
  });

  group('NoteFileStorage', () {
    test('imageDir 返回正确路径', () {
      final dir = NoteFileStorage.imageDirFromBase(tempDir.path, 42);
      expect(dir.path, '${tempDir.path}/notes/42/images');
    });

    test('saveImage 创建目录并写入文件', () async {
      final bytes = Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0]);
      final file = await NoteFileStorage.saveImageToBase(
        tempDir.path,
        1,
        bytes,
      );
      expect(file.existsSync(), true);
      expect(file.path.endsWith('.jpg'), true);
      expect(file.readAsBytesSync(), bytes);
    });

    test('deleteImage 删除指定文件', () async {
      final bytes = Uint8List.fromList([1, 2, 3]);
      final file = await NoteFileStorage.saveImageToBase(
        tempDir.path,
        1,
        bytes,
      );
      expect(file.existsSync(), true);
      NoteFileStorage.deleteImage(file.path);
      expect(file.existsSync(), false);
    });

    test('cleanOrphanFiles 删除未引用的文件', () async {
      final dir = NoteFileStorage.imageDirFromBase(tempDir.path, 1);
      dir.createSync(recursive: true);

      final kept = File('${dir.path}/kept.jpg')..writeAsBytesSync([1]);
      final orphan = File('${dir.path}/orphan.jpg')..writeAsBytesSync([2]);

      NoteFileStorage.cleanOrphanFilesInDir(dir, {kept.path});

      expect(kept.existsSync(), true);
      expect(orphan.existsSync(), false);
    });

    test('cleanOrphanFiles 目录不存在时不报错', () {
      final dir = Directory('${tempDir.path}/nonexistent');
      expect(
        () => NoteFileStorage.cleanOrphanFilesInDir(dir, {}),
        returnsNormally,
      );
    });

    test('deleteNoteDir 删除整个笔记目录', () async {
      final bytes = Uint8List.fromList([1, 2, 3]);
      await NoteFileStorage.saveImageToBase(tempDir.path, 5, bytes);
      final noteDir = Directory('${tempDir.path}/notes/5');
      expect(noteDir.existsSync(), true);

      NoteFileStorage.deleteNoteDirFromBase(tempDir.path, 5);
      expect(noteDir.existsSync(), false);
    });
  });
}
