-- TD-169: los pesajes de los sabados (peso, cintura, musculo esqueletico) suben al coach.
--
-- Se corre UNA vez, en Supabase: SQL Editor -> New query -> pegar todo -> Run.
-- Es idempotente: si se corre dos veces no rompe nada.
--
-- Mismo trato que las sesiones (td-126-sessions.sql): SOLO el coach autenticado lee; un
-- telefono de atleta solo puede subir y borrar los suyos, por funciones, sin leer nada.
-- A diferencia de las sesiones, un pesaje no depende de un training asignado: sube siempre
-- que el telefono tenga perfil.

-- La tabla. Un pesaje por perfil y por dia: anotar otra vez el mismo dia corrige.
create table if not exists public.body_logs (
  profile_id text        not null references public.profiles(id) on delete cascade,
  date       date        not null,
  payload    jsonb       not null,
  updated_at timestamptz not null default now(),
  primary key (profile_id, date)
);

alter table public.body_logs enable row level security;

-- Leer: solo el coach. Sin politica para anon, RLS lo niega.
drop policy if exists "coach reads body logs" on public.body_logs;
create policy "coach reads body logs" on public.body_logs
  for select to authenticated using (true);

-- Subir: upsert, porque corregir un pesaje es volver a anotarlo el mismo dia.
create or replace function public.upload_body(
  p_profile_id text,
  p_date       date,
  p_payload    jsonb
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.body_logs (profile_id, date, payload, updated_at)
  values (p_profile_id, p_date, p_payload, now())
  on conflict (profile_id, date) do update
    set payload    = excluded.payload,
        updated_at = now();
  return true;
end;
$$;

-- Borrar: UN pesaje de UN perfil. Devuelve false si no estaba.
create or replace function public.delete_body(
  p_profile_id text,
  p_date       date
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.body_logs where profile_id = p_profile_id and date = p_date;
  return found;
end;
$$;

revoke all on function public.upload_body(text, date, jsonb) from public;
grant execute on function public.upload_body(text, date, jsonb) to anon, authenticated;
revoke all on function public.delete_body(text, date) from public;
grant execute on function public.delete_body(text, date) to anon, authenticated;
