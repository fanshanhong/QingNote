import 'package:flutter/material.dart';
import '../theme.dart';

class NoteSearchBar extends StatefulWidget {
  final String initialQuery;
  final ValueChanged<String> onQueryChanged;
  const NoteSearchBar(
      {super.key, this.initialQuery = '', required this.onQueryChanged});

  @override
  State<NoteSearchBar> createState() => _NoteSearchBarState();
}

class _NoteSearchBarState extends State<NoteSearchBar> {
  late final _controller = TextEditingController(text: widget.initialQuery);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String text) {
    widget.onQueryChanged(text.trim());
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppDimens.spacingL,
        AppDimens.spacingXs,
        AppDimens.spacingL,
        AppDimens.spacingM,
      ),
      child: TextField(
        controller: _controller,
        onChanged: _onChanged,
        decoration: InputDecoration(
          hintText: '搜索笔记',
          hintStyle: const TextStyle(
              color: AppColors.textHint, fontSize: AppDimens.textBody),
          prefixIcon: const Icon(Icons.search, color: AppColors.textHint),
          filled: true,
          fillColor: const Color(0xFFBBDEFB),
          contentPadding: const EdgeInsets.symmetric(vertical: 10),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(20),
            borderSide: BorderSide.none,
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(20),
            borderSide: BorderSide.none,
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(20),
            borderSide: BorderSide.none,
          ),
        ),
        style: const TextStyle(
            fontSize: AppDimens.textBody, color: AppColors.textPrimary),
      ),
    );
  }
}
