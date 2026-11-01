-- TD-205: el telefono de un atleta baja SUS pesajes, para ver los que el coach anoto por el
-- o ella. La tabla body_logs solo la lee el coach autenticado (td-169-body.sql); el telefono
-- del atleta no tiene cuenta, asi que va por funcion, como upload_body y delete_body.
--
-- Devuelve solo las filas del perfil que pide: el mismo alcance que ya tienen subir y borrar,
-- que tambien trabajan por perfil sin sesion.
--
-- Correr una vez en Supabase -> SQL Editor. Se puede volver a correr sin dano.

create or replace function public.my_body(
  p_profile_id text
) returns table (date date, payload jsonb)
language sql
security definer
set search_path = public
stable
as $$
  select b.date, b.payload
  from public.body_logs b
  where b.profile_id = p_profile_id
  order by b.date;
$$;

revoke all on function public.my_body(text) from public;
grant execute on function public.my_body(text) to anon, authenticated;
