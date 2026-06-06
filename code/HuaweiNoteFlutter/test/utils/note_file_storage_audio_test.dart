import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:hwnote/utils/note_file_storage.dart';

void main() {
  late Directory tempDir;

  setUp(() {
    tempDir = Directory.systemTemp.createTempSync('note_audio_test_');
  });

  tearDown(() {
    if (tempDir.existsSync()) tempDir.deleteSync(recursive: true);
  });

  group('NoteFileStorage audio', () {
    test('audioDirFromBase 返回正确路径', () {
      final dir = NoteFileStorage.audioDirFromBase(tempDir.path, 7);
      expect(dir.path, '${tempDir.path}/notes/7/audio');
    });

    test('saveAudioToBase 复制文件到音频目录', () async {
      final srcDir = Directory('${tempDir.path}/src')..createSync();
      final srcFile = File('${srcDir.path}/recording.m4a')
        ..writeAsBytesSync([0x00, 0x01, 0x02, 0x03]);

      final saved = await NoteFileStorage.saveAudioToBase(
        tempDir.path, 10, srcFile.path,
      );
      expect(saved.existsSync(), true);
      expect(saved.path.endsWith('.m4a'), true);
      expect(saved.path.contains('/notes/10/audio/'), true);
      expect(saved.readAsBytesSync(), [0x00, 0x01, 0x02, 0x03]);
    });

    test('audioFilePath 返回完整路径', () {
      final path = NoteFileStorage.audioFilePath(tempDir.path, 3, '12345.m4a');
      expect(path, '${tempDir.path}/notes/3/audio/12345.m4a');
    });

    test('cleanOrphanAudios 删除未引用的音频文件', () async {
      final dir = NoteFileStorage.audioDirFromBase(tempDir.path, 1);
      dir.createSync(recursive: true);

      File('${dir.path}/keep.m4a').writeAsBytesSync([1]);
      File('${dir.path}/orphan.m4a').writeAsBytesSync([2]);

      NoteFileStorage.cleanOrphanFilesInDir(dir, {'${dir.path}/keep.m4a'});

      expect(File('${dir.path}/keep.m4a').existsSync(), true);
      expect(File('${dir.path}/orphan.m4a').existsSync(), false);
    });
  });
}
