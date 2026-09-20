-- TD-140: publicar el vídeo de un ejercicio desde el teléfono.
--
-- Se corre UNA vez, en Supabase: SQL Editor -> New query -> pegar todo -> Run.
-- Es idempotente.
--
-- POR QUE. Hasta ahora, el vídeo que el coach asigna a un ejercicio vivía en el directorio
-- privado del app: no entraba en el payload, no entraba en el respaldo y no llegaba a nadie.
-- Publicar uno exigía PC, el CLI de gh y un commit a videos.json, o sea el asistente.
--
-- EL BUCKET ES PUBLICO, decisión del usuario el 19-sep-2026: "no hay nada que ocultar o
-- derechos de autor, son para uso personal, es una app de ejercicios, no es app bancaria".
-- Los de GitHub ya eran públicos, así que no cambia nada en la práctica. Leer un bucket
-- público por /storage/v1/object/public/videos/... no pasa por RLS, que es justo lo que
-- necesita el teléfono de un atleta: no tiene cuenta.

insert into storage.buckets (id, name, public)
values ('videos', 'videos', true)
on conflict (id) do update set public = true;

-- Escribir: solo con sesión de entrenador. Sin política para anon, RLS se lo niega todo.
drop policy if exists "coach uploads videos" on storage.objects;
create policy "coach uploads videos" on storage.objects
  for insert to authenticated with check (bucket_id = 'videos');

-- Reemplazar un vídeo sube una revisión nueva, pero x-upsert puede tocar la misma ruta si
-- se reintenta una subida cortada: sin update, ese reintento fallaría.
drop policy if exists "coach replaces videos" on storage.objects;
create policy "coach replaces videos" on storage.objects
  for update to authenticated using (bucket_id = 'videos') with check (bucket_id = 'videos');

drop policy if exists "coach deletes videos" on storage.objects;
create policy "coach deletes videos" on storage.objects
  for delete to authenticated using (bucket_id = 'videos');

-- STORAGE NO ESCRIBE SOLO EN storage.objects. Las versiones nuevas escriben tambien en
-- storage.prefixes, que tiene su propio RLS. Con permiso en una y no en la otra, la subida
-- falla con el MISMO mensaje que una peticion sin sesion -"new row violates row-level
-- security policy"- y se pierde media hora buscando el token. Paso el 19-sep-2026.
--
-- Como distinguirlo la proxima vez, por el mensaje que devuelve Storage:
--   token mal formado      -> "JWS Protected Header is invalid"
--   token mal firmado      -> "signature verification failed"
--   politica que rechaza   -> "new row violates row-level security policy"
-- Si sale el tercero, el token llego bien: el problema son las politicas.
do $$
begin
  if to_regclass('storage.prefixes') is not null then
    execute 'drop policy if exists "coach writes video prefixes" on storage.prefixes';
    execute 'create policy "coach writes video prefixes" on storage.prefixes
             for insert to authenticated with check (bucket_id = ''videos'')';
    execute 'drop policy if exists "coach reads video prefixes" on storage.prefixes';
    execute 'create policy "coach reads video prefixes" on storage.prefixes
             for select to authenticated using (bucket_id = ''videos'')';
  end if;
end $$;

-- Leer los objetos con sesion de entrenador: la subida con x-upsert mira antes si el
-- archivo ya existe. (Leer el contenido publicado no pasa por aqui: el bucket es publico.)
drop policy if exists "coach reads videos" on storage.objects;
create policy "coach reads videos" on storage.objects
  for select to authenticated using (bucket_id = 'videos');

-- Comprobación tras correrlo:
--   select id, public from storage.buckets where id = 'videos';   -- debe decir public = true
