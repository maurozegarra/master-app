-- TD-149: una sesion borrada en el telefono del atleta se borra tambien en el servidor.
--
-- Se corre UNA vez, en Supabase: SQL Editor -> New query -> pegar todo -> Run.
-- Es idempotente: si se corre dos veces no rompe nada.
--
-- Por que hace falta: el telefono del atleta no tiene cuenta y la tabla `sessions` no le
-- deja borrar (td-126-sessions.sql). Asi que borrar alla se quedaba alla, y el coach -y el
-- asistente, que lee su respaldo- seguia viendo la sesion. El 21-sep-2026 dos sesiones de
-- una demostracion en el telefono de NIKO se leyeron como entrenamientos de ella.
--
-- Igual que upload_session: una funcion que sabe hacer UNA cosa. Borra UNA sesion de UN
-- perfil, y nada mas; no deja leer nada.
--
-- Devuelve true si la borro y false si no estaba: en los dos casos el telefono lo da por
-- hecho y deja de intentarlo.
create or replace function public.delete_session(
  p_profile_id text,
  p_session_id bigint
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  delete from public.sessions
  where profile_id = p_profile_id and session_id = p_session_id;
  return found;
end;
$$;

revoke all on function public.delete_session(text, bigint) from public;
grant execute on function public.delete_session(text, bigint) to anon, authenticated;

-- Limpieza, una sola vez: tres sesiones a medias del 19-sep (23:45-23:55) que NIKO ya no
-- tiene en su telefono. Se borraron alla antes de que existiera esta funcion, asi que su
-- telefono no va a pedir borrarlas nunca.
delete from public.sessions
where profile_id = 'niko'
  and session_id in (1789879559691, 1789879755834, 1789880102460);
