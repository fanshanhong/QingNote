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
        let words = JIEBA.cut_for_search(text, true);
        let mut tokens = Vec::new();
        let mut byte_offset = 0;
        for word in words {
            if word.trim().is_empty() {
                byte_offset += word.len();
                continue;
            }
            let start = byte_offset;
            let end = start + word.len();
            tokens.push(Token {
                offset_from: start,
                offset_to: end,
                position: tokens.len(),
                text: word.trim().to_lowercase(),
                position_length: 1,
            });
            byte_offset = end;
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
