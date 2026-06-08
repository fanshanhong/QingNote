use std::path::Path;
use tantivy::schema::*;
use tantivy::{Index, IndexWriter};

use super::tokenizer::JiebaTokenizer;

const TOKENIZER_NAME: &str = "jieba";

pub struct SearchIndex {
    pub index: Index,
    pub schema: Schema,
    pub id_field: Field,
    pub title_field: Field,
    pub content_field: Field,
}

impl SearchIndex {
    pub fn new(index_path: &str) -> tantivy::Result<Self> {
        let mut schema_builder = Schema::builder();

        let id_field = schema_builder.add_i64_field("id", INDEXED | STORED);

        let text_options = TextOptions::default()
            .set_indexing_options(
                TextFieldIndexing::default()
                    .set_tokenizer(TOKENIZER_NAME)
                    .set_index_option(IndexRecordOption::WithFreqsAndPositions),
            )
            .set_stored();

        let title_field = schema_builder.add_text_field("title", text_options.clone());
        let content_field = schema_builder.add_text_field("content", text_options);

        let schema = schema_builder.build();

        let path = Path::new(index_path);
        let meta_path = path.join("meta.json");
        let index = if meta_path.exists() {
            Index::open_in_dir(path)?
        } else {
            std::fs::create_dir_all(path)
                .map_err(|e| tantivy::TantivyError::SystemError(e.to_string()))?;
            Index::create_in_dir(path, schema.clone())?
        };

        index.tokenizers().register(TOKENIZER_NAME, JiebaTokenizer);

        Ok(Self {
            index,
            schema,
            id_field,
            title_field,
            content_field,
        })
    }

    pub fn writer(&self) -> tantivy::Result<IndexWriter> {
        self.index.writer(50_000_000)
    }

    pub fn upsert(&self, writer: &IndexWriter, id: i64, title: &str, content: &str) {
        let id_term = tantivy::Term::from_field_i64(self.id_field, id);
        writer.delete_term(id_term);

        let mut doc = TantivyDocument::new();
        doc.add_i64(self.id_field, id);
        doc.add_text(self.title_field, title);
        doc.add_text(self.content_field, content);
        let _ = writer.add_document(doc);
    }

    pub fn delete(&self, writer: &IndexWriter, id: i64) {
        let id_term = tantivy::Term::from_field_i64(self.id_field, id);
        writer.delete_term(id_term);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::search::searcher;
    use tempfile::TempDir;

    fn setup() -> (TempDir, SearchIndex) {
        let dir = TempDir::new().unwrap();
        let idx = SearchIndex::new(dir.path().to_str().unwrap()).unwrap();
        (dir, idx)
    }

    #[test]
    fn test_chinese_segmentation_search() {
        let (_dir, idx) = setup();
        let mut writer = idx.writer().unwrap();
        idx.upsert(&writer, 1, "学习笔记", "学习笔记很重要，每天都要复习");
        idx.upsert(&writer, 2, "购物清单", "今天去超市买牛奶和面包");
        idx.upsert(&writer, 3, "工作计划", "下周一开会讨论项目进度");
        writer.commit().unwrap();

        let hits = searcher::search(&idx, "学习", 10).unwrap();
        assert!(!hits.is_empty(), "搜索'学习'应命中");
        assert_eq!(hits[0].note_id, 1);

        let hits = searcher::search(&idx, "笔记", 10).unwrap();
        assert!(!hits.is_empty(), "搜索'笔记'应命中");
        assert_eq!(hits[0].note_id, 1);

        let hits = searcher::search(&idx, "超市", 10).unwrap();
        assert!(!hits.is_empty(), "搜索'超市'应命中");
        assert_eq!(hits[0].note_id, 2);
    }

    #[test]
    fn test_upsert_overwrites() {
        let (_dir, idx) = setup();
        let mut writer = idx.writer().unwrap();
        idx.upsert(&writer, 1, "旧标题", "旧内容");
        writer.commit().unwrap();
        drop(writer);

        let mut writer = idx.writer().unwrap();
        idx.upsert(&writer, 1, "新标题", "新内容完全不同");
        writer.commit().unwrap();

        let hits = searcher::search(&idx, "旧", 10).unwrap();
        assert!(hits.is_empty(), "旧内容应被覆盖");

        let hits = searcher::search(&idx, "新标题", 10).unwrap();
        assert!(!hits.is_empty(), "新内容应能搜到");
    }

    #[test]
    fn test_delete() {
        let (_dir, idx) = setup();
        let mut writer = idx.writer().unwrap();
        idx.upsert(&writer, 1, "测试笔记", "这是一条测试笔记");
        writer.commit().unwrap();
        drop(writer);

        let mut writer = idx.writer().unwrap();
        idx.delete(&writer, 1);
        writer.commit().unwrap();

        let hits = searcher::search(&idx, "测试", 10).unwrap();
        assert!(hits.is_empty(), "删除后应搜不到");
    }

    #[test]
    fn test_highlight_in_results() {
        let (_dir, idx) = setup();
        let mut writer = idx.writer().unwrap();
        idx.upsert(&writer, 1, "Flutter学习笔记", "今天学了Flutter的状态管理");
        writer.commit().unwrap();

        let hits = searcher::search(&idx, "flutter", 10).unwrap();
        assert!(!hits.is_empty());
        assert!(
            hits[0].title_highlight.contains("<mark>"),
            "标题应有高亮标记: {}",
            hits[0].title_highlight
        );
    }
}
