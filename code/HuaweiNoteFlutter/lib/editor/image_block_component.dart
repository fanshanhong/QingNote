import 'dart:io';
import 'package:appflowy_editor/appflowy_editor.dart';
import 'package:flutter/material.dart';
import '../theme.dart';

class CustomImageBlockComponentBuilder extends BlockComponentBuilder {
  CustomImageBlockComponentBuilder({
    required this.editable,
    required this.onDelete,
    this.onImageLoaded,
    super.configuration,
  });

  final bool editable;
  final void Function(Node node) onDelete;
  final VoidCallback? onImageLoaded;

  @override
  BlockComponentWidget build(BlockComponentContext blockComponentContext) {
    final node = blockComponentContext.node;
    return CustomImageBlockWidget(
      key: node.key,
      node: node,
      configuration: configuration,
      editable: editable,
      onDelete: onDelete,
      onImageLoaded: onImageLoaded,
    );
  }

  @override
  BlockComponentValidate get validate =>
      (node) => node.attributes[ImageBlockKeys.url] is String;
}

class CustomImageBlockWidget extends BlockComponentStatefulWidget {
  const CustomImageBlockWidget({
    super.key,
    required super.node,
    super.configuration = const BlockComponentConfiguration(),
    required this.editable,
    required this.onDelete,
    this.onImageLoaded,
  });

  final bool editable;
  final void Function(Node node) onDelete;
  final VoidCallback? onImageLoaded;

  @override
  State<CustomImageBlockWidget> createState() => _CustomImageBlockWidgetState();
}

class _CustomImageBlockWidgetState extends State<CustomImageBlockWidget> {
  String get _url =>
      widget.node.attributes[ImageBlockKeys.url] as String? ?? '';

  @override
  Widget build(BuildContext context) {
    final file = File(_url);
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: Stack(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: file.existsSync()
                ? Image.file(
                    file,
                    fit: BoxFit.cover,
                    width: double.infinity,
                    frameBuilder: (context, child, frame, loaded) {
                      if (frame != null && !loaded) {
                        WidgetsBinding.instance.addPostFrameCallback((_) {
                          widget.onImageLoaded?.call();
                        });
                      }
                      return child;
                    },
                  )
                : Container(
                    height: 100,
                    decoration: BoxDecoration(
                      color: AppColors.bgWindow,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Center(
                      child:
                          Icon(Icons.broken_image, color: AppColors.textHint),
                    ),
                  ),
          ),
          if (widget.editable)
            Positioned(
              top: 6,
              right: 6,
              child: GestureDetector(
                onTap: () => widget.onDelete(widget.node),
                child: Container(
                  width: 26,
                  height: 26,
                  decoration: const BoxDecoration(
                    color: Colors.black54,
                    shape: BoxShape.circle,
                  ),
                  child:
                      const Icon(Icons.close, size: 16, color: Colors.white),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
