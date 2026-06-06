class Folder {
  final int id;
  final String name;
  final int orderIndex;
  final bool isDefault;
  final int deletedAt;

  const Folder({
    required this.id,
    required this.name,
    this.orderIndex = 0,
    this.isDefault = false,
    this.deletedAt = 0,
  });
}
