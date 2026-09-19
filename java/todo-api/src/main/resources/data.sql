-- Users
INSERT INTO users (id, username, email, created_at) VALUES (1, 'johndoe', 'john.doe@example.com', '2024-01-10T09:00:00');
INSERT INTO users (id, username, email, created_at) VALUES (2, 'janedoe', 'jane.doe@example.com', '2024-01-15T10:30:00');
INSERT INTO users (id, username, email, created_at) VALUES (3, 'bobsmith', 'bob.smith@example.com', '2024-02-01T08:15:00');
INSERT INTO users (id, username, email, created_at) VALUES (4, 'alicewong', 'alice.wong@example.com', '2024-02-20T14:00:00');

-- Todos for johndoe (user 1)
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (1, 'Buy groceries', 'Milk, eggs, bread, and vegetables', 'HIGH', false, '2024-06-15', '2024-06-01T09:00:00', 1);
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (2, 'Finish project report', 'Complete the Q2 report for the engineering team', 'HIGH', false, '2024-06-20', '2024-06-02T10:00:00', 1);
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (3, 'Schedule dentist appointment', 'Annual checkup — call Dr. Patel', 'LOW', true, '2024-07-01', '2024-06-05T11:30:00', 1);

-- Todos for janedoe (user 2)
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (4, 'Prepare presentation', 'Slides for the Monday team standup', 'MEDIUM', false, '2024-06-18', '2024-06-03T08:00:00', 2);
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (5, 'Read Spring Boot docs', 'Focus on Spring Data JPA and validation', 'LOW', false, '2024-07-10', '2024-06-04T13:00:00', 2);
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (6, 'Update resume', 'Add recent projects and certifications', 'MEDIUM', true, '2024-06-25', '2024-06-06T15:00:00', 2);

-- Todos for bobsmith (user 3)
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (7, 'Fix login bug', 'Users getting 401 on valid credentials', 'HIGH', false, '2024-06-12', '2024-06-01T07:30:00', 3);
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (8, 'Write unit tests', 'Cover the UserService class with tests', 'MEDIUM', false, '2024-06-22', '2024-06-07T09:45:00', 3);
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (9, 'Deploy to staging', 'Push the latest build to the staging environment', 'HIGH', true, '2024-06-10', '2024-06-01T06:00:00', 3);

-- Todos for alicewong (user 4)
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (10, 'Plan team outing', 'Research venues and send out a poll', 'LOW', false, '2024-07-15', '2024-06-08T10:00:00', 4);
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (11, 'Review pull requests', 'Review PRs #42 and #43 from the frontend team', 'MEDIUM', false, '2024-06-14', '2024-06-09T11:00:00', 4);
INSERT INTO todos (id, title, description, priority, completed, due_date, created_at, user_id) VALUES (12, 'Set up CI pipeline', 'Configure GitHub Actions for the new microservice', 'HIGH', false, '2024-06-30', '2024-06-10T14:30:00', 4);

-- Comments
INSERT INTO comments (id, content, author_name, created_at, todo_id) VALUES (1, 'Don''t forget the organic eggs!', 'janedoe', '2024-06-02T10:00:00', 1);
INSERT INTO comments (id, content, author_name, created_at, todo_id) VALUES (2, 'I can help with the charts section.', 'bobsmith', '2024-06-03T11:00:00', 2);
INSERT INTO comments (id, content, author_name, created_at, todo_id) VALUES (3, 'Dr. Patel is available on Tuesdays.', 'alicewong', '2024-06-06T09:00:00', 3);
INSERT INTO comments (id, content, author_name, created_at, todo_id) VALUES (4, 'Use the new slide template from the shared drive.', 'johndoe', '2024-06-04T14:00:00', 4);
INSERT INTO comments (id, content, author_name, created_at, todo_id) VALUES (5, 'Check the Baeldung tutorials — they are great.', 'alicewong', '2024-06-05T16:00:00', 5);
INSERT INTO comments (id, content, author_name, created_at, todo_id) VALUES (6, 'This is blocking the release — prioritize it.', 'janedoe', '2024-06-02T08:00:00', 7);
INSERT INTO comments (id, content, author_name, created_at, todo_id) VALUES (7, 'I already reviewed #42, looks good.', 'bobsmith', '2024-06-10T12:00:00', 11);
INSERT INTO comments (id, content, author_name, created_at, todo_id) VALUES (8, 'Consider using the reusable workflow from the infra repo.', 'johndoe', '2024-06-11T09:30:00', 12);

-- Reset identity sequences so new inserts get IDs after seeded data
ALTER TABLE users ALTER COLUMN id RESTART WITH 5;
ALTER TABLE todos ALTER COLUMN id RESTART WITH 13;
ALTER TABLE comments ALTER COLUMN id RESTART WITH 9;
