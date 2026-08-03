-- =============================================================================
-- 73_master_models_model_number_jsonb.sql
--
-- Converts master_models.model_number from a single VARCHAR (migration 64) that
-- packed several codes into one slash/comma separated string
--   e.g. "MZB0L8AIN/MZB0L88IN/MZB0L89IN/MZB0L3MIN"
-- into a real jsonb array of individual codes
--   e.g. ["MZB0L8AIN","MZB0L88IN","MZB0L89IN","MZB0L3MIN"]
-- matching the inline colors / ram_storage jsonb arrays (migration 70) and the
-- MasterModel.modelNumber field which now maps List<String> via
-- @JdbcTypeCode(SqlTypes.JSON).
--
-- Robust to THREE shapes a value may hold:
--   1. plain codes            "CPH2665 / CPH2639"   -> split on / , ;
--   2. JSON array TEXT        '["A","B"]'            -> parsed as an array
--   3. JSON quoted-string TEXT '"A,B"'              -> unwrapped, then split
-- Shapes 2/3 occur when a model was saved through the new array-based admin
-- WHILE this column was still varchar (version skew) — those must be parsed, not
-- naively split, or the brackets/quotes leak into the codes. Whitespace is
-- trimmed and empty fragments dropped; NULL / blank -> [].
--
-- Without this, Hibernate's validate-only ddl mode rejects the entity at startup
-- (text column vs jsonb mapping) and every request touching master_models 500s.
--
-- Idempotent: the whole conversion is guarded on the column still being a scalar
-- text type, so re-running after it's already jsonb is a no-op.
-- =============================================================================

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'master_models'
          AND column_name = 'model_number'
          AND data_type <> 'jsonb'
    ) THEN
        -- Per-row parser: handles plain strings AND stray JSON text (see header).
        CREATE OR REPLACE FUNCTION _mig73_to_codes(raw text)
        RETURNS jsonb LANGUAGE plpgsql AS $fn$
        DECLARE
            s text := btrim(coalesce(raw, ''));
            j jsonb;
        BEGIN
            IF s = '' THEN
                RETURN '[]'::jsonb;
            END IF;
            -- Value already looks like JSON (from a pre-migration save)? Parse it.
            IF left(s, 1) IN ('[', '"') THEN
                BEGIN
                    j := s::jsonb;
                    IF jsonb_typeof(j) = 'array' THEN
                        RETURN (
                            SELECT coalesce(jsonb_agg(btrim(e) ORDER BY ord), '[]'::jsonb)
                            FROM jsonb_array_elements_text(j) WITH ORDINALITY AS a(e, ord)
                            WHERE btrim(e) <> ''
                        );
                    ELSIF jsonb_typeof(j) = 'string' THEN
                        s := j #>> '{}';   -- unwrap "A,B" -> A,B, then split below
                    END IF;
                EXCEPTION WHEN others THEN
                    NULL;                  -- not valid JSON, fall through to split
                END;
            END IF;
            -- Plain string: split on / , ; and drop empty fragments.
            RETURN (
                SELECT coalesce(jsonb_agg(part ORDER BY ord), '[]'::jsonb)
                FROM (
                    SELECT btrim(t) AS part, ord
                    FROM regexp_split_to_table(s, '[/,;]') WITH ORDINALITY AS x(t, ord)
                ) q
                WHERE part <> ''
            );
        END;
        $fn$;

        -- Stage a jsonb column, backfill via the parser, then swap in.
        ALTER TABLE master_models
            ADD COLUMN model_number_jsonb jsonb NOT NULL DEFAULT '[]'::jsonb;

        UPDATE master_models
        SET model_number_jsonb = _mig73_to_codes(model_number);

        ALTER TABLE master_models DROP COLUMN model_number;
        ALTER TABLE master_models RENAME COLUMN model_number_jsonb TO model_number;

        DROP FUNCTION _mig73_to_codes(text);
    END IF;
END $$;

COMMIT;
