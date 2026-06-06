import 'package:flutter/material.dart';
import '../theme.dart';

class TodoPlaceholderPage extends StatelessWidget {
  const TodoPlaceholderPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: Text(
          '待办功能开发中',
          style: TextStyle(fontSize: AppDimens.textBody, color: AppColors.textHint),
        ),
      ),
    );
  }
}
