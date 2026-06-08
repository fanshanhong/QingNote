use jieba_rs::Jieba;
use once_cell::sync::Lazy;
use tantivy::tokenizer::{Token, TokenStream, Tokenizer};

static JIEBA: Lazy<Jieba> = Lazy::new(Jieba::new);

#[derive(Clone)]
pub struct JiebaTokenizer;

pub struct JiebaTokenStream {
    tokens: Vec<Token>,
    index: usize,
}

impl Tokenizer for JiebaTokenizer {
    type TokenStream<'a> = JiebaTokenStream;

    fn token_stream<'a>(&'a mut self, text: &'a str) -> Self::TokenStream<'a> {
        let words = JIEBA.cut(text, true);
        let mut tokens = Vec::new();
        let mut offset = 0;
        for word in words {
            let trimmed = word.trim();
            if trimmed.is_empty() {
                offset += word.len();
                continue;
            }
            let start = text[offset..].find(trimmed).unwrap_or(0) + offset;
            let end = start + trimmed.len();
            tokens.push(Token {
                offset_from: start,
                offset_to: end,
                position: tokens.len(),
                text: trimmed.to_lowercase(),
                position_length: 1,
            });
            offset = end;
        }
        JiebaTokenStream { tokens, index: 0 }
    }
}

impl TokenStream for JiebaTokenStream {
    fn advance(&mut self) -> bool {
        if self.index < self.tokens.len() {
            self.index += 1;
            true
        } else {
            false
        }
    }

    fn token(&self) -> &Token {
        &self.tokens[self.index - 1]
    }

    fn token_mut(&mut self) -> &mut Token {
        &mut self.tokens[self.index - 1]
    }
}
