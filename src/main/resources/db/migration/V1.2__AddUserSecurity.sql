-- Add security fields to user table
ALTER TABLE user 
ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT '$2a$10$slYQmi1U7Y0TQPWJ8YMYGe7LZE.qC.JrG3OX8TFj3cTqNLBYv9Yc6',
ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'USER',
ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Add unique index on uid
ALTER TABLE user 
ADD UNIQUE INDEX idx_uid (uid);

-- Default password is 'password' (BCrypt encoded)
-- Update existing users
UPDATE user SET 
    password = '$2a$10$slYQmi1U7Y0TQPWJ8YMYGe7LZE.qC.JrG3OX8TFj3cTqNLBYv9Yc6',
    role = 'USER',
    enabled = TRUE;
