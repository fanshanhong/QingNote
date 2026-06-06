import 'dart:io';
import 'dart:typed_data';
import 'package:path_provider/path_provider.dart';

class NoteFileStorage {
  static Future<Directory> imageDir(int noteId) async {
    final appDir = await getApplicationDocumentsDirectory();
    return imageDirFromBase(appDir.path, noteId);
  }

  static Directory imageDirFromBase(String basePath, int noteId) {
    return Directory('$basePath/notes/$noteId/images');
  }

  static Future<File> saveImage(int noteId, Uint8List bytes) async {
    final appDir = await getApplicationDocumentsDirectory();
    return saveImageToBase(appDir.path, noteId, bytes);
  }

  static Future<File> saveImageToBase(
    String basePath,
    int noteId,
    Uint8List bytes,
  ) async {
    final dir = imageDirFromBase(basePath, noteId);
    if (!dir.existsSync()) dir.createSync(recursive: true);
    final fileName = '${DateTime.now().millisecondsSinceEpoch}.jpg';
    final file = File('${dir.path}/$fileName');
    await file.writeAsBytes(bytes);
    return file;
  }

  static void deleteImage(String filePath) {
    final file = File(filePath);
    if (file.existsSync()) file.deleteSync();
  }

  static void deleteNoteDirFromBase(String basePath, int noteId) {
    final dir = Directory('$basePath/notes/$noteId');
    if (dir.existsSync()) dir.deleteSync(recursive: true);
  }

  static Future<void> deleteNoteDir(int noteId) async {
    final appDir = await getApplicationDocumentsDirectory();
    deleteNoteDirFromBase(appDir.path, noteId);
  }

  static void cleanOrphanFilesInDir(
    Directory dir,
    Set<String> referencedPaths,
  ) {
    if (!dir.existsSync()) return;
    for (final file in dir.listSync().whereType<File>()) {
      if (!referencedPaths.contains(file.path)) {
        file.deleteSync();
      }
    }
  }

  static Future<void> cleanOrphanImages(
    int noteId,
    Set<String> referencedPaths,
  ) async {
    final dir = await imageDir(noteId);
    cleanOrphanFilesInDir(dir, referencedPaths);
  }

  static Directory audioDirFromBase(String basePath, int noteId) {
    return Directory('$basePath/notes/$noteId/audio');
  }

  static Future<Directory> audioDir(int noteId) async {
    final appDir = await getApplicationDocumentsDirectory();
    return audioDirFromBase(appDir.path, noteId);
  }

  static Future<File> saveAudioToBase(
    String basePath,
    int noteId,
    String sourceFilePath,
  ) async {
    final dir = audioDirFromBase(basePath, noteId);
    if (!dir.existsSync()) dir.createSync(recursive: true);
    final fileName = '${DateTime.now().millisecondsSinceEpoch}.m4a';
    final target = File('${dir.path}/$fileName');
    await File(sourceFilePath).copy(target.path);
    return target;
  }

  static Future<File> saveAudio(int noteId, String sourceFilePath) async {
    final appDir = await getApplicationDocumentsDirectory();
    return saveAudioToBase(appDir.path, noteId, sourceFilePath);
  }

  static String audioFilePath(String basePath, int noteId, String fileName) {
    return '$basePath/notes/$noteId/audio/$fileName';
  }

  static Future<void> cleanOrphanAudios(
    int noteId,
    Set<String> referencedFileNames,
  ) async {
    final dir = await audioDir(noteId);
    if (!dir.existsSync()) return;
    for (final file in dir.listSync().whereType<File>()) {
      final name = file.path.split('/').last;
      if (!referencedFileNames.contains(name)) {
        file.deleteSync();
      }
    }
  }
}
