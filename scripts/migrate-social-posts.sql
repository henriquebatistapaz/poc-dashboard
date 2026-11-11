\set ON_ERROR_STOP on

BEGIN;

DO $$
BEGIN
    IF to_regclass('public.social_posts') IS NULL THEN
        RAISE EXCEPTION 'public.social_posts does not exist in the connected database';
    END IF;

    IF to_regclass('public.collected_item') IS NULL THEN
        RAISE EXCEPTION 'public.collected_item does not exist in the connected database';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.social_posts
        WHERE source IS NULL OR length(btrim(source)) = 0 OR length(btrim(source)) > 40
           OR external_id IS NULL OR length(btrim(external_id)) = 0
           OR length(btrim(external_id)) > 2000
           OR (permalink IS NOT NULL AND length(permalink) > 2000)
    ) THEN
        RAISE EXCEPTION 'social_posts contains rows that do not fit collected_item; no rows were copied';
    END IF;
END $$;

INSERT INTO public.collected_item (source, external_id, title, link, published_at)
SELECT btrim(source),
       btrim(external_id),
       NULLIF(left(text, 1000), ''),
       COALESCE(NULLIF(btrim(permalink), ''),
                'https://legacy.invalid/social-post/' || md5(btrim(source) || ':' || btrim(external_id))),
       published_at
FROM public.social_posts
ON CONFLICT (source, external_id) DO NOTHING;

COMMIT;
