package com.amigoscode.interview.book;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    // N+1 problem: fetching all books triggers separate queries
    // for each book's author and reviews due to lazy loading
    public List<Book> getAllBooks() {
        List<Book> books = bookRepository.findAll();
        // Accessing author name forces lazy load — one query per book
        books.forEach(book -> {
            if (book.getAuthor() != null) {
                book.getAuthor().getName();
            }
        });
        return books;
    }

    public Book createBook(Book book) {
        return bookRepository.save(book);
    }

    public void deleteBook(Long id) {
        bookRepository.deleteById(id);
    }

    // NOTE: getBookById() is intentionally missing — interview task #1

}
