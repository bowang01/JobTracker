-- Technical interview questions can name the programming language they are about (Java, C#, Python, ...).
-- Optional; behavioral questions never have a language. Existing questions keep NULL ("not specified").
ALTER TABLE interview_questions ADD COLUMN tech_language VARCHAR(20) NULL;

ALTER TABLE interview_questions ADD CONSTRAINT ck_q_language CHECK (
    tech_language IS NULL OR tech_language IN
        ('JAVA', 'CSHARP', 'PYTHON', 'JAVASCRIPT', 'TYPESCRIPT', 'CPP', 'GO', 'SQL', 'GENERAL', 'OTHER'));

ALTER TABLE interview_questions ADD CONSTRAINT ck_q_language_technical CHECK (
    tech_language IS NULL OR category = 'TECHNICAL');

-- Hall and bank filters by language
CREATE INDEX idx_q_language ON interview_questions (category, tech_language);
