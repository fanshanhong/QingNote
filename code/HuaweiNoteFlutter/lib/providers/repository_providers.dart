import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'database_provider.dart';
import '../repositories/note_repository.dart';
import '../repositories/folder_repository.dart';
import '../repositories/notebook_repository.dart';
import '../repositories/category_repository.dart';
import '../repositories/todo_repository.dart';

final noteRepositoryProvider = Provider<NoteRepository>((ref) {
  return NoteRepository(ref.watch(databaseHelperProvider));
});

final folderRepositoryProvider = Provider<FolderRepository>((ref) {
  return FolderRepository(ref.watch(databaseHelperProvider));
});

final notebookRepositoryProvider = Provider<NotebookRepository>((ref) {
  return NotebookRepository(ref.watch(databaseHelperProvider));
});

final categoryRepositoryProvider = Provider<CategoryRepository>((ref) {
  return CategoryRepository(ref.watch(databaseHelperProvider));
});

final todoRepositoryProvider = Provider<TodoRepository>((ref) {
  return TodoRepository(ref.watch(databaseHelperProvider));
});
