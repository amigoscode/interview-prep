-- Authors
INSERT INTO authors (id, name, bio, created_at) VALUES (1, 'George Orwell', 'English novelist and essayist, best known for Animal Farm and 1984.', '2024-01-15T10:00:00');
INSERT INTO authors (id, name, bio, created_at) VALUES (2, 'J.K. Rowling', 'British author, best known for the Harry Potter series.', '2024-02-20T11:30:00');
INSERT INTO authors (id, name, bio, created_at) VALUES (3, 'Harper Lee', 'American novelist known for To Kill a Mockingbird.', '2024-03-10T09:15:00');
INSERT INTO authors (id, name, bio, created_at) VALUES (4, 'Frank Herbert', 'American science fiction author, best known for Dune.', '2024-04-05T14:00:00');
INSERT INTO authors (id, name, bio, created_at) VALUES (5, 'Jane Austen', 'English novelist known for Pride and Prejudice and Sense and Sensibility.', '2024-05-12T16:45:00');

-- Books
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (1, '1984', '978-0451524935', 'Dystopian', 9.99, '1949-06-08', 1);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (2, 'Animal Farm', '978-0451526342', 'Satire', 7.99, '1945-08-17', 1);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (3, 'Harry Potter and the Philosophers Stone', '978-0747532699', 'Fantasy', 12.99, '1997-06-26', 2);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (4, 'Harry Potter and the Chamber of Secrets', '978-0747538493', 'Fantasy', 12.99, '1998-07-02', 2);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (5, 'To Kill a Mockingbird', '978-0061120084', 'Fiction', 10.99, '1960-07-11', 3);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (6, 'Go Set a Watchman', '978-0062409850', 'Fiction', 14.99, '2015-07-14', 3);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (7, 'Dune', '978-0441172719', 'Science Fiction', 11.99, '1965-08-01', 4);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (8, 'Dune Messiah', '978-0593098233', 'Science Fiction', 10.99, '1969-10-01', 4);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (9, 'Pride and Prejudice', '978-0141439518', 'Romance', 8.99, '1813-01-28', 5);
INSERT INTO books (id, title, isbn, genre, price, published_date, author_id) VALUES (10, 'Sense and Sensibility', '978-0141439662', 'Romance', 8.99, '1811-10-30', 5);

-- Reviews
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (1, 'Alice Johnson', 'A chilling and prophetic masterpiece. More relevant today than ever.', 5, '2024-06-01T10:00:00', 1);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (2, 'Bob Smith', 'Thought-provoking but quite dark. Not an easy read.', 4, '2024-06-05T14:30:00', 1);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (3, 'Carol Davis', 'A brilliant allegory. Short but impactful.', 5, '2024-06-10T09:00:00', 2);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (4, 'Dan Wilson', 'Magical world-building that captivates readers of all ages.', 5, '2024-06-15T11:00:00', 3);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (5, 'Eve Martinez', 'Great start to an amazing series. My kids loved it.', 4, '2024-06-20T16:00:00', 3);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (6, 'Frank Brown', 'The mystery element makes this one even better than the first.', 4, '2024-07-01T10:30:00', 4);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (7, 'Grace Lee', 'A timeless classic that everyone should read.', 5, '2024-07-05T13:00:00', 5);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (8, 'Henry Taylor', 'Beautiful prose and important themes about justice and empathy.', 5, '2024-07-10T15:30:00', 5);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (9, 'Ivy Chen', 'Not as strong as Mockingbird but still a worthwhile read.', 3, '2024-07-15T09:45:00', 6);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (10, 'Jack Anderson', 'Epic world-building. The depth of the universe is incredible.', 5, '2024-07-20T11:15:00', 7);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (11, 'Karen White', 'Dense but rewarding. A must-read for sci-fi fans.', 4, '2024-07-25T14:00:00', 7);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (12, 'Leo Garcia', 'A philosophical sequel that goes deeper into the themes of Dune.', 4, '2024-08-01T10:00:00', 8);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (13, 'Mia Robinson', 'Witty and romantic. Austen at her finest.', 5, '2024-08-05T12:30:00', 9);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (14, 'Noah Clark', 'The social commentary is sharp and still resonates.', 4, '2024-08-10T16:00:00', 9);
INSERT INTO reviews (id, reviewer_name, content, rating, created_at, book_id) VALUES (15, 'Olivia Hall', 'A beautiful story about love and good sense. Underrated compared to Pride and Prejudice.', 4, '2024-08-15T09:30:00', 10);

-- Reset identity sequences so new inserts get IDs after seeded data
ALTER TABLE authors ALTER COLUMN id RESTART WITH 6;
ALTER TABLE books ALTER COLUMN id RESTART WITH 11;
ALTER TABLE reviews ALTER COLUMN id RESTART WITH 16;
