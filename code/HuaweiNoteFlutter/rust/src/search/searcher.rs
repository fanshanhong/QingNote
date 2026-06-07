use tantivy::collector::TopDocs;
use tantivy::query::QueryParser;
use tantivy::schema::Value;
use tantivy::{ReloadPolicy, Searcher};

use super::indexer::SearchIndex;

pub struct SearchHit {
    pub note_id: i64,
    pub score: f32,
    pub title_highlight: String,
    pub content_snippet: String,
}

fn build_snippet(text: &str, query_terms: &[String], max_len: usize) -> String {
    let text_lower = text.to_lowercase();
    let mut best_pos = 0;
    for term in query_terms {
        if let Some(pos) = text_lower.find(term) {
            best_pos = pos;
            break;
        }
    }

    let half = max_len / 2;
    let start = best_pos.saturating_sub(half);
    let end = (start + max_len).min(text.len());
    let start = adjust_to_char_boundary(text, start);
    let end = adjust_to_char_boundary(text, end);

    let mut snippet = String::new();
    if start > 0 {
        snippet.push_str("...");
    }
    let fragment = &text[start..end];
    snippet.push_str(&highlight(fragment, query_terms));
    if end < text.len() {
        snippet.push_str("...");
    }
    snippet
}

fn highlight(text: &str, query_terms: &[String]) -> String {
    let mut result = text.to_string();
    for term in query_terms {
        let lower = result.to_lowercase();
        let mut output = String::new();
        let mut last = 0;
        for (start, _) in lower.match_indices(term) {
            output.push_str(&result[last..start]);
            output.push_str("<mark>");
            output.push_str(&result[start..start + term.len()]);
            output.push_str("</mark>");
            last = start + term.len();
        }
        output.push_str(&result[last..]);
        result = output;
    }
    result
}

fn adjust_to_char_boundary(s: &str, pos: usize) -> usize {
    if pos >= s.len() {
        return s.len();
    }
    let mut p = pos;
    while !s.is_char_boundary(p) && p > 0 {
        p -= 1;
    }
    p
}

pub fn search(
    search_index: &SearchIndex,
    query_str: &str,
    limit: usize,
) -> tantivy::Result<Vec<SearchHit>> {
    let reader = search_index
        .index
        .reader_builder()
        .reload_policy(ReloadPolicy::OnCommitWithDelay)
        .try_into()?;
    let searcher: Searcher = reader.searcher();

    let query_parser = QueryParser::for_index(
        &search_index.index,
        vec![search_index.title_field, search_index.content_field],
    );

    let query = query_parser
        .parse_query(query_str)
        .map_err(|e| tantivy::TantivyError::InvalidArgument(e.to_string()))?;

    let top_docs = searcher.search(&query, &TopDocs::with_limit(limit))?;

    let query_terms: Vec<String> = jieba_rs::Jieba::new()
        .cut(query_str, true)
        .into_iter()
        .filter(|w| !w.trim().is_empty())
        .map(|w| w.trim().to_lowercase())
        .collect();

    let mut hits = Vec::new();
    for (score, doc_address) in top_docs {
        let doc: tantivy::TantivyDocument = searcher.doc(doc_address)?;

        let note_id = doc
            .get_first(search_index.id_field)
            .and_then(|v| v.as_i64())
            .unwrap_or(0);

        let title = doc
            .get_first(search_index.title_field)
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();

        let content = doc
            .get_first(search_index.content_field)
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string();

        let title_highlight = highlight(&title, &query_terms);
        let content_snippet = build_snippet(&content, &query_terms, 100);

        hits.push(SearchHit {
            note_id,
            score,
            title_highlight,
            content_snippet,
        });
    }

    Ok(hits)
}
