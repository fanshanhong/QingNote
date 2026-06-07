enum Heading {
  h1,
  h2,
  h3,
  h4,
  h5,
  h6;

  static Heading? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }

  int get level => index + 1;
}

enum NoteAlignment {
  start,
  center,
  end;

  static NoteAlignment? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }

  String get appFlowyValue => switch (this) {
        NoteAlignment.start => 'left',
        NoteAlignment.center => 'center',
        NoteAlignment.end => 'right',
      };
}

enum ListType {
  bullet,
  hollowBullet,
  numbered,
  lettered;

  static ListType? fromName(String name) {
    for (final v in values) {
      if (v.name == name) return v;
    }
    return null;
  }
}
