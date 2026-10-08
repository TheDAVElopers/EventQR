-- Event requests carry a target audience; copy it onto the event so attendees can see it.
ALTER TABLE public.events ADD COLUMN IF NOT EXISTS target_audience varchar(255);

UPDATE public.events e
SET target_audience = r.target_audience
FROM public.event_requests r
WHERE r.event_id = e.id AND e.target_audience IS NULL AND r.target_audience IS NOT NULL;

UPDATE public.events e
SET category = r.event_category
FROM public.event_requests r
WHERE r.event_id = e.id AND (e.category IS NULL OR e.category = '') AND r.event_category IS NOT NULL;
