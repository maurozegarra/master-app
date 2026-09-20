-- TD-139: las instrucciones de un ejercicio dejan de viajar dentro del APK.
--
-- Se corre UNA vez, en Supabase: SQL Editor -> New query -> pegar todo -> Run.
-- Es idempotente: si se corre dos veces no rompe nada.
--
-- EL PROBLEMA QUE RESUELVE. La unidad real de contenido es el exerciseId -un ejercicio tiene
-- nombre, instrucciones y video- pero hoy esas piezas viajan por tres caminos distintos: el
-- nombre dentro del training (esta base), las instrucciones dentro del APK, y el video por un
-- commit al repo. Solo el training se entrega solo. Consecuencia: un ejercicio nuevo le llega
-- al atleta SIN instrucciones hasta que actualice el app, y falla en silencio -ve un nombre y
-- nada mas-.
--
-- LAS DECISIONES QUE SOSTIENE, del usuario, el 19-sep-2026:
--   1. Lo que lee esta tabla es publico de hecho: la clave publicable viaja dentro del APK.
--      Las instrucciones no son secretas -son como se hace una sentadilla- asi que se leen sin
--      sesion, igual que los perfiles. Escribir sigue exigiendo sesion de entrenador.
--   2. Las columnas de video se crean YA aunque TD-139 no las use: TD-140 publica el mp4 desde
--      el telefono y TD-141 retira videos.json. Crearlas ahora ahorra una migracion despues.

create table if not exists public.exercise_media (
  exercise_id  text        primary key,
  -- Los pasos, en orden, tal cual se ensenan en el player. Array de textos.
  instructions jsonb       not null default '[]'::jsonb,
  -- TD-140/TD-141: version y tamano del mp4 publicado. Nulos hasta entonces.
  video_rev    int,
  video_bytes  bigint,
  updated_at   timestamptz not null default now()
);

-- Por si la tabla ya existia de una corrida anterior sin las columnas de video.
alter table public.exercise_media add column if not exists video_rev   int;
alter table public.exercise_media add column if not exists video_bytes bigint;

alter table public.exercise_media enable row level security;

-- Leer: cualquiera, con o sin sesion. Es lo que hace que un ejercicio nuevo llegue con sus
-- instrucciones sin publicar una version del app.
drop policy if exists "anyone reads exercise media" on public.exercise_media;
create policy "anyone reads exercise media" on public.exercise_media
  for select to anon, authenticated using (true);

-- Escribir: solo el entrenador. Sin politica para anon, RLS se lo niega todo.
drop policy if exists "coach writes exercise media" on public.exercise_media;
create policy "coach writes exercise media" on public.exercise_media
  for insert to authenticated with check (true);

drop policy if exists "coach updates exercise media" on public.exercise_media;
create policy "coach updates exercise media" on public.exercise_media
  for update to authenticated using (true) with check (true);

drop policy if exists "coach deletes exercise media" on public.exercise_media;
create policy "coach deletes exercise media" on public.exercise_media
  for delete to authenticated using (true);

-- Comprobacion rapida tras correrlo (deberia devolver 0 filas y ningun error):
--   select * from public.exercise_media;
