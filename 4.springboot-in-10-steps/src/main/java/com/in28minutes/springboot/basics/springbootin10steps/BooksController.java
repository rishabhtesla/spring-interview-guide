package com.in28minutes.springboot.basics.springbootin10steps;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BooksController {
    @GetMapping("/books")
    public List<Book> getAllBooks() {
        // Jackson serializes the record components; this hard-coded response does not query a database.
        return List.of(new Book(1L, "Mastering Spring 5.2", "Ranga Karanam"));
    }
}