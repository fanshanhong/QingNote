class Category {
  final int id;
  final String name;
  final String color;
  final int orderIndex;

  const Category({
    required this.id,
    required this.name,
    required this.color,
    this.orderIndex = 0,
  });
}
