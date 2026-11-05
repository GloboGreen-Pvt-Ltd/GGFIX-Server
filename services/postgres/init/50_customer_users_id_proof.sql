-- Optional customer ID proof URL used by owner-side customer creation/search.
ALTER TABLE customer_users
    ADD COLUMN IF NOT EXISTS id_proof_url VARCHAR(1000);
