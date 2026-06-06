import 'package:flutter/material.dart';
import '../theme.dart';

class NoteListPage extends StatelessWidget {
  const NoteListPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Text('笔记列表', style: TextStyle(fontSize: AppDimens.textBody)),
      ),
    );
  }
}
