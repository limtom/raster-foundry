-- update campaigns table to include children_count and is_ready columns
ALTER TABLE public.campaigns
ADD COLUMN children_count INTEGER NOT NULL default 0,
ADD COLUMN is_ready BOOLEAN NOT NULL default false;

-- add indices
CREATE INDEX IF NOT EXISTS campaigns_children_count_idx
ON public.campaigns
USING btree (children_count);
CREATE INDEX IF NOT EXISTS campaigns_is_ready_idx
ON public.campaigns
USING btree (is_ready);

-- update children_count column for existing campaigns
UPDATE public.campaigns
SET children_count = campaign_children_count.children_count
FROM (
  SELECT c1.id, count(c2.id) as children_count
  FROM public.campaigns c1
  LEFT JOIN public.campaigns c2
  ON c1.id = c2.parent_campaign_id
  group by c1.id
) campaign_children_count
WHERE campaign_children_count.id = campaigns.id;

-- update is_ready column for existing campaigns
UPDATE public.campaigns
SET is_ready = campaign_is_ready.is_ready
FROM (
  WITH campaign_annotation_project_ready_count AS (
    SELECT c.id, count(ap.id) as ready_count
    FROM campaigns c
    LEFT JOIN annotation_projects ap
    ON ap.campaign_id = c.id
    WHERE ap.status = 'READY'
    group by c.id
  )
  SELECT
    c.id, 
    CASE
      WHEN COALESCE(caprc.ready_count, 0) = count(ap.id) THEN true
      ELSE false
    END 
    AS is_ready
  FROM campaigns c
  LEFT JOIN annotation_projects ap
  ON ap.campaign_id = c.id
  LEFT JOIN campaign_annotation_project_ready_count caprc
  ON caprc.id = c.id
  group by c.id, caprc.ready_count
) campaign_is_ready
WHERE campaign_is_ready.id = campaigns.id;

-- define the trigger function to update children_count for campaigns
CREATE OR REPLACE FUNCTION UPDATE_CAMPAIGN_CHILDREN_COUNT()
  RETURNS trigger AS
$BODY$
DECLARE
  op_campaign_id uuid;
BEGIN
  -- the NEW variable holds row for INSERT/UPDATE operations
  -- the OLD variable holds row for DELETE operations
  -- store the parent campaign ID
  IF TG_OP = 'INSERT' THEN
    op_campaign_id := NEW.parent_campaign_id;
  ELSE
    op_campaign_id := OLD.parent_campaign_id;
  END IF;
  -- update children_count for the parent campaign
  IF op_campaign_id IS NOT NULL THEN
    UPDATE public.campaigns
    SET children_count = (
      SELECT count(id)
      FROM public.campaigns
      WHERE parent_campaign_id = op_campaign_id
    )
    WHERE id = op_campaign_id;
  END IF;
  -- result is ignored since this is an AFTER trigger
  RETURN NULL;
END;
$BODY$
LANGUAGE 'plpgsql';

-- define the trigger function to update is_ready for campaigns
CREATE OR REPLACE FUNCTION UPDATE_CAMPAIGN_IS_READY()
  RETURNS trigger AS
$BODY$
DECLARE
  op_campaign_id uuid;
  is_status_change boolean;
BEGIN
  -- the NEW variable holds row for INSERT/UPDATE operations
  -- the OLD variable holds row for DELETE operations
  -- store the campaign ID from the annotation project update
  is_status_change := false;
  IF TG_OP = 'UPDATE' THEN
    op_campaign_id := NEW.campaign_id;
    is_status_change := NEW.status <> OLD.status;
  END IF;
  -- update is_ready for the parent campaign
  IF op_campaign_id IS NOT NULL AND is_status_change = true  THEN
    UPDATE public.campaigns
    SET is_ready = (
      WITH campaign_annotation_project_ready_count AS (
        SELECT c.id, count(ap.id) as ready_count
        FROM campaigns c
        LEFT JOIN annotation_projects ap
        ON ap.campaign_id = c.id
        WHERE ap.status = 'READY'
        AND c.id = op_campaign_id
        group by c.id
      )
      SELECT
        CASE
          WHEN COALESCE(caprc.ready_count, 0) = count(ap.id) THEN true
          ELSE false
        END 
        AS is_ready
      FROM campaigns c
      LEFT JOIN annotation_projects ap
      ON ap.campaign_id = c.id
      LEFT JOIN campaign_annotation_project_ready_count caprc
      ON caprc.id = c.id
      WHERE c.id = op_campaign_id
      group by c.id, caprc.ready_count
    )
    WHERE id = op_campaign_id;
  END IF;
  -- result is ignored since this is an AFTER trigger
  RETURN NULL;
END;
$BODY$
LANGUAGE 'plpgsql';

-- add a trigger to INSERT OR DELETE operations on campaigns table
CREATE TRIGGER update_campaign_children_count
  AFTER INSERT OR DELETE
  ON campaigns
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_CAMPAIGN_CHILDREN_COUNT();

-- add a trigger to UPDATE operation on campaigns table
CREATE TRIGGER update_campaign_is_ready
  AFTER UPDATE
  ON annotation_projects
  FOR EACH ROW
  EXECUTE PROCEDURE UPDATE_CAMPAIGN_IS_READY();