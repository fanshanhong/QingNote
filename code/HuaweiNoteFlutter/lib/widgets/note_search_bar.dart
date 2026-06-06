import 'dart:async';
import 'package:flutter/material.dart';
import '../theme.dart';

class NoteSearchBar extends StatefulWidget {
  final String initialQuery;
  final ValueChanged<String> onQueryChanged;
  const NoteSearchBar({super.key, this.initialQuery = '', required this.onQueryChanged});

  @override
  State<NoteSearchBar> createState() => _NoteSearchBarState();
}

class _NoteSearchBarState extends State<NoteSearchBar> {
  late final _controller = TextEditingController(text: widget.initialQuery);
  Timer? _debounce;

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String text) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 200), () {
      widget.onQueryChanged(text.trim());
    });
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.spacingL, AppDimens.spacingXs, AppDimens.spacingL, AppDimens.spacingM,
      ),
      child: TextField(
        controller: _controller,
        onChanged: _onChanged,
        decoration: InputDecoration(
          hintText: '搜索笔记',
          hintStyle: const TextStyle(color: AppColors.textHint, fontSize: AppDimens.textBody),
          prefixIcon: const Icon(Icons.search, color: AppColors.textHint),
          filled: true, fillColor: const Color(0xFFF5F5F5),
          contentPadding: const EdgeInsets.symmetric(vertical: 10),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(8), borderSide: BorderSide.none,
          ),
        ),
        style: const TextStyle(fontSize: AppDimens.textBody, color: AppColors.textPrimary),
      ),
    );
  }
}
