ALTER TABLE public.varieties
    ADD CONSTRAINT uk_varieties_genus_name UNIQUE (genus, name);
