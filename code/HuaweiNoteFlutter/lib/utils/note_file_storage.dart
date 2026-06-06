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
}
