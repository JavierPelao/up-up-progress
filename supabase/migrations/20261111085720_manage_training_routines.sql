-- Versiones de plantillas: retirar del listado sin alterar entrenamientos existentes.
alter table public.routines
  add column is_archived boolean not null default false,
  add column replaces_routine_id uuid,
  add constraint routines_id_user_unique unique(id,user_id),
  add constraint routines_replacement_owner_fk foreign key(replaces_routine_id,user_id)
    references public.routines(id,user_id) on delete restrict;
create unique index routines_one_replacement on public.routines(replaces_routine_id)
  where replaces_routine_id is not null;

create function public.replace_training_routine(
  p_original_routine_id uuid, p_routine_id uuid, p_name text, p_description text, p_exercises jsonb
) returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_old public.routines%rowtype;
  v_new public.routines%rowtype;
begin
  if v_user is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_original_routine_id is null or p_routine_id is null or p_original_routine_id=p_routine_id then
    raise exception 'Invalid replacement' using errcode='22023';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_routine_id::text,0));
  select * into v_old from public.routines
    where id=p_original_routine_id and user_id=v_user for update;
  if not found then raise exception 'Routine unavailable' using errcode='42501'; end if;
  select * into v_new from public.routines where id=p_routine_id and user_id=v_user;
  if found then
    if v_new.replaces_routine_id is distinct from p_original_routine_id then
      raise exception 'Request ID already used' using errcode='22023';
    end if;
    perform public.save_training_routine(p_routine_id,p_name,p_description,p_exercises);
    return p_routine_id;
  end if;
  if v_old.is_archived then raise exception 'Routine changed or removed; reload' using errcode='22023'; end if;
  perform public.save_training_routine(p_routine_id,p_name,p_description,p_exercises);
  update public.routines set replaces_routine_id=p_original_routine_id where id=p_routine_id and user_id=v_user;
  update public.routines set is_archived=true where id=p_original_routine_id and user_id=v_user;
  return p_routine_id;
end;
$$;

create function public.archive_training_routine(p_routine_id uuid)
returns uuid language plpgsql security invoker set search_path = '' as $$
declare v_user uuid := auth.uid();
begin
  if v_user is null then raise exception 'Authentication required' using errcode='42501'; end if;
  perform 1 from public.routines where id=p_routine_id and user_id=v_user for update;
  if not found then raise exception 'Routine unavailable' using errcode='42501'; end if;
  update public.routines set is_archived=true where id=p_routine_id and user_id=v_user;
  return p_routine_id;
end;
$$;

create or replace function public.select_training_routine(
  p_session_id uuid, p_routine_id uuid, p_performed_on date, p_replace_session_id uuid default null
) returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_existing public.workout_sessions%rowtype;
  v_old public.workout_sessions%rowtype;
  v_count integer;
begin
  if v_user is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_session_id is null or p_routine_id is null or p_performed_on is null
    or p_performed_on > current_date+1 or p_session_id=p_replace_session_id then
    raise exception 'Invalid session' using errcode='22023';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_session_id::text,1));
  select * into v_existing from public.workout_sessions where id=p_session_id and user_id=v_user;
  if found then
    if v_existing.routine_id is distinct from p_routine_id or v_existing.performed_on <> p_performed_on then
      raise exception 'Request ID already used' using errcode='22023';
    end if;
    return p_session_id;
  end if;
  perform 1 from public.routines where id=p_routine_id and user_id=v_user and not is_archived for update;
  if not found then raise exception 'Routine unavailable' using errcode='42501'; end if;
  select count(*) into v_count from public.routine_exercises where routine_id=p_routine_id;
  if v_count not between 1 and 50 then raise exception 'Routine has no valid exercises' using errcode='22023'; end if;
  if p_replace_session_id is not null then
    select * into v_old from public.workout_sessions where id=p_replace_session_id and user_id=v_user for update;
    if not found or v_old.performed_on <> p_performed_on then
      raise exception 'Previous session unavailable' using errcode='42501';
    end if;
    delete from public.workout_sessions where id=v_old.id and user_id=v_user;
  end if;
  insert into public.workout_sessions(id,user_id,routine_id,performed_on)
    values(p_session_id,v_user,p_routine_id,p_performed_on);
  insert into public.workout_exercises(session_id,exercise_id,position)
    select p_session_id,exercise_id,position from public.routine_exercises
    where routine_id=p_routine_id order by position;
  return p_session_id;
end;
$$;


revoke all on function public.replace_training_routine(uuid,uuid,text,text,jsonb) from public,anon;
revoke all on function public.archive_training_routine(uuid) from public,anon;
grant execute on function public.replace_training_routine(uuid,uuid,text,text,jsonb) to authenticated;
grant execute on function public.archive_training_routine(uuid) to authenticated;
