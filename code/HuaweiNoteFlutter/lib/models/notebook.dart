class Notebook {
  final int id;
  final String name;
  final int folderId;
  final String color;
  final int orderIndex;
  final bool isDefault;
  final int deletedAt;

  const Notebook({
    required this.id,
    required this.name,
    required this.folderId,
    this.color = '#9E9E9E',
    this.orderIndex = 0,
    this.isDefault = false,
    this.deletedAt = 0,
  });
}
