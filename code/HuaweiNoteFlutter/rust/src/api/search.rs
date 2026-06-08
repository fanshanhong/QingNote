use std::sync::Mutex;
use once_cell::sync::Lazy;

use crate::search::indexer::SearchIndex;
use crate::search::searcher;

static ENGINE: Lazy<Mutex<Option<SearchIndex>>> = Lazy::new(|| Mutex::new(None));

#[flutter_rust_bridge::frb]
pub struct SearchHit {
    pub note_id: i64,
    pub score: f32,
    pub title_highlight: String,
    pub content_snippet: String,
}

#[flutter_rust_bridge::frb]
pub struct NoteForIndex {
    pub id: i64,
    pub title: String,
    pub plain_text: String,
}

pub fn init_search_engine(index_path: String) -> anyhow::Result<()> {
    let idx = SearchIndex::new(&index_path)
        .map_err(|e| anyhow::anyhow!("Failed to init search engine: {}", e))?;
    let mut guard = ENGINE.lock().unwrap();
    *guard = Some(idx);
    Ok(())
}

pub fn upsert_note_index(id: i64, title: String, content: String) -> anyhow::Result<()> {
    let guard = ENGINE.lock().unwrap();
    let idx = guard.as_ref().ok_or_else(|| anyhow::anyhow!("Search engine not initialized"))?;
    let mut writer = idx.writer().map_err(|e| anyhow::anyhow!("{}", e))?;
    idx.upsert(&writer, id, &title, &content);
    writer.commit().map_err(|e| anyhow::anyhow!("{}", e))?;
    Ok(())
}

pub fn delete_note_index(id: i64) -> anyhow::Result<()> {
    let guard = ENGINE.lock().unwrap();
    let idx = guard.as_ref().ok_or_else(|| anyhow::anyhow!("Search engine not initialized"))?;
    let mut writer = idx.writer().map_err(|e| anyhow::anyhow!("{}", e))?;
    idx.delete(&writer, id);
    writer.commit().map_err(|e| anyhow::anyhow!("{}", e))?;
    Ok(())
}

pub fn search_notes(query: String, limit: u32) -> anyhow::Result<Vec<SearchHit>> {
    let guard = ENGINE.lock().unwrap();
    let idx = guard.as_ref().ok_or_else(|| anyhow::anyhow!("Search engine not initialized"))?;
    let results = searcher::search(idx, &query, limit as usize)
        .map_err(|e| anyhow::anyhow!("{}", e))?;
    Ok(results
        .into_iter()
        .map(|h| SearchHit {
            note_id: h.note_id,
            score: h.score,
            title_highlight: h.title_highlight,
            content_snippet: h.content_snippet,
        })
        .collect())
}

pub fn rebuild_index(notes: Vec<NoteForIndex>) -> anyhow::Result<()> {
    let guard = ENGINE.lock().unwrap();
    let idx = guard.as_ref().ok_or_else(|| anyhow::anyhow!("Search engine not initialized"))?;
    let mut writer = idx.writer().map_err(|e| anyhow::anyhow!("{}", e))?;
    writer.delete_all_documents().map_err(|e| anyhow::anyhow!("{}", e))?;
    for note in &notes {
        idx.upsert(&writer, note.id, &note.title, &note.plain_text);
    }
    writer.commit().map_err(|e| anyhow::anyhow!("{}", e))?;
    Ok(())
}
