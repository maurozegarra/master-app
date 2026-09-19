-- TD-126: el historial de un atleta asignado sube al coach.
--
-- Se corre UNA vez, en Supabase: SQL Editor -> New query -> pegar todo -> Run.
-- Es idempotente: si se corre dos veces no rompe nada.
--
-- Las dos decisiones que sostiene, tomadas por el usuario el 19-sep-2026:
--   1. Las sesiones llevan el dolor de cada dia: SOLO el coach autenticado las lee.
--      Un telefono de atleta solo puede SUBIR las suyas, sin leer ni borrar nada.
--   2. Solo suben las sesiones de trainings ASIGNADOS: lo que un atleta entrena por su
--      cuenta se queda en su telefono. Se comprueba aqui, en el servidor, y no solo en el app.

-- La tabla. La clave es (perfil, id de la sesion): el id lo pone el telefono al cerrarla y
-- es unico por telefono; con el perfil delante no choca entre atletas.
create table if not exists public.sessions (
  profile_id   text        not null references public.profiles(id) on delete cascade,
  session_id   bigint      not null,
  training_uid text        not null,
  completed_at timestamptz,
  payload      jsonb       not null,
  updated_at   timestamptz not null default now(),
  primary key (profile_id, session_id)
);

alter table public.sessions enable row level security;

-- Leer y borrar: solo el coach. No hay politica para anon: sin politica, RLS lo niega todo.
drop policy if exists "coach reads sessions" on public.sessions;
create policy "coach reads sessions" on public.sessions
  for select to authenticated using (true);

drop policy if exists "coach deletes sessions" on public.sessions;
create policy "coach deletes sessions" on public.sessions
  for delete to authenticated using (true);

-- Subir: por esta funcion y por ningun otro lado. Nadie escribe directo en la tabla.
--
-- Hace upsert y no solo insert porque la sesion se sigue completando DESPUES de guardarse:
-- el dolor al despertar, el de despues y el feedback que falto se contestan en la pantalla
-- final, minutos mas tarde. El telefono la vuelve a subir y la fila se actualiza.
--
-- Devuelve false si ese training no esta asignado a ese perfil: el telefono deja de
-- intentarlo en vez de reintentar para siempre.
create or replace function public.upload_session(
  p_profile_id   text,
  p_session_id   bigint,
  p_training_uid text,
  p_completed_at timestamptz,
  p_payload      jsonb
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (
    select 1 from public.assignments a
    where a.profile_id = p_profile_id and a.training_uid = p_training_uid
  ) then
    return false;
  end if;

  insert into public.sessions (profile_id, session_id, training_uid, completed_at, payload, updated_at)
  values (p_profile_id, p_session_id, p_training_uid, p_completed_at, p_payload, now())
  on conflict (profile_id, session_id) do update
    set training_uid = excluded.training_uid,
        completed_at = excluded.completed_at,
        payload      = excluded.payload,
        updated_at   = now();

  return true;
end;
$$;

revoke all on function public.upload_session(text, bigint, text, timestamptz, jsonb) from public;
grant execute on function public.upload_session(text, bigint, text, timestamptz, jsonb) to anon, authenticated;
