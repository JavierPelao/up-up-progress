-- Preparada para revisión. No modifica históricos existentes salvo añadir estado pending.
alter table public.workout_exercises
  add column completion_status text not null default 'pending'
  constraint workout_exercises_completion_status_check check (completion_status in ('pending','completed','skipped'));

create function public.save_training_routine(
  p_routine_id uuid, p_name text, p_description text, p_exercises jsonb
) returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_new boolean;
  v_routine public.routines%rowtype;
  v_item jsonb;
  v_index bigint;
  v_exercise uuid;
  v_name text;
  v_lock text;
  v_expected jsonb;
  v_actual jsonb;
begin
  if v_user is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_routine_id is null or p_name is null or char_length(trim(p_name)) not between 1 and 120
    or char_length(coalesce(p_description,'')) > 1000 then
    raise exception 'Invalid routine' using errcode='22023';
  end if;
  if p_exercises is null or jsonb_typeof(p_exercises) <> 'array' then
    raise exception 'Invalid exercises' using errcode='22023';
  end if;
  if jsonb_array_length(p_exercises) not between 1 and 50 then
    raise exception 'Invalid number of exercises' using errcode='22023';
  end if;
  for v_item in select value from jsonb_array_elements(p_exercises) loop
    if jsonb_typeof(v_item) <> 'object' or (v_item->>'id') is null
      or nullif(trim(v_item->>'name'),'') is null or char_length(trim(v_item->>'name')) > 120
      or (v_item->>'planned_sets') is null or (v_item->>'planned_sets')::integer not between 1 and 30
      or (v_item->>'planned_reps') is null or (v_item->>'planned_reps')::integer not between 1 and 1000 then
      raise exception 'Invalid planned exercise' using errcode='22023';
    end if;
    perform (v_item->>'id')::uuid;
  end loop;
  perform pg_advisory_xact_lock(hashtextextended(p_routine_id::text,0));
  insert into public.routines(id,user_id,name,description)
    values(p_routine_id,v_user,trim(p_name),nullif(trim(coalesce(p_description,'')),''))
    on conflict(id) do nothing returning true into v_new;
  select * into v_routine from public.routines where id=p_routine_id and user_id=v_user for update;
  if not found then raise exception 'Routine unavailable' using errcode='42501'; end if;
  if v_new is distinct from true then
    select jsonb_agg(jsonb_build_object('id',x.value->>'id','name',lower(trim(x.value->>'name')),
      'sets',(x.value->>'planned_sets')::integer,'reps',(x.value->>'planned_reps')::integer) order by x.ordinality)
      into v_expected from jsonb_array_elements(p_exercises) with ordinality x(value,ordinality);
    select jsonb_agg(jsonb_build_object('id',re.id::text,'name',lower(trim(e.name)),
      'sets',re.planned_sets,'reps',re.planned_reps) order by re.position)
      into v_actual from public.routine_exercises re join public.exercises e on e.id=re.exercise_id
      where re.routine_id=p_routine_id;
    if v_routine.name is distinct from trim(p_name)
      or v_routine.description is distinct from nullif(trim(coalesce(p_description,'')),'')
      or v_actual is distinct from v_expected then
      raise exception 'Request ID already used with other content' using errcode='22023';
    end if;
    return p_routine_id;
  end if;
  -- Nombres bloqueados en orden estable: reutilizar identidad sin carreras entre rutinas.
  for v_lock in select distinct lower(trim(value->>'name'))
    from jsonb_array_elements(p_exercises) order by 1 loop
    perform pg_advisory_xact_lock(hashtextextended(v_user::text || ':' || v_lock,0));
  end loop;
  for v_item,v_index in select value,ordinality from jsonb_array_elements(p_exercises) with ordinality loop
    v_name := trim(v_item->>'name');
    v_exercise := null;
    if nullif(v_item->>'exercise_id','') is not null then
      select id into v_exercise from public.exercises
        where id=(v_item->>'exercise_id')::uuid
        and (created_by is null or created_by=v_user) and lower(trim(name))=lower(v_name);
      if v_exercise is null then raise exception 'Exercise unavailable' using errcode='42501'; end if;
    else
      select id into v_exercise from public.exercises
        where lower(trim(name))=lower(v_name) and (created_by is null or created_by=v_user)
        order by (created_by is not null),id limit 1;
      if v_exercise is null then
        insert into public.exercises(name,created_by) values(v_name,v_user) returning id into v_exercise;
      end if;
    end if;
    insert into public.routine_exercises(id,routine_id,exercise_id,position,planned_sets,planned_reps)
      values((v_item->>'id')::uuid,p_routine_id,v_exercise,v_index::integer,
        (v_item->>'planned_sets')::integer,(v_item->>'planned_reps')::integer);
  end loop;
  return p_routine_id;
end;
$$;

create function public.select_training_routine(
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
  perform 1 from public.routines where id=p_routine_id and user_id=v_user for update;
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

create function public.save_training_set(
  p_workout_exercise_id uuid, p_set_number integer, p_weight_kg numeric, p_reps integer
) returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_session uuid;
  v_result public.performed_sets%rowtype;
  v_weight numeric := coalesce(p_weight_kg,0);
begin
  if v_user is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_set_number is null or p_set_number not between 1 and 30
    or p_reps is null or p_reps not between 1 and 1000
    or v_weight::text in ('NaN','Infinity','-Infinity')
    or v_weight < 0 or v_weight > 99999.99 or v_weight <> round(v_weight,2) then
    raise exception 'Invalid set' using errcode='22023';
  end if;
  select s.id into v_session from public.workout_sessions s
    join public.workout_exercises e on e.session_id=s.id
    where e.id=p_workout_exercise_id and s.user_id=v_user for update of s;
  if not found then raise exception 'Exercise unavailable' using errcode='42501'; end if;
  insert into public.performed_sets(workout_exercise_id,set_number,weight_kg,reps)
    values(p_workout_exercise_id,p_set_number,v_weight,p_reps)
    on conflict(workout_exercise_id,set_number) do update set weight_kg=excluded.weight_kg,reps=excluded.reps
    returning * into v_result;
  update public.workout_exercises set completion_status='pending'
    where id=p_workout_exercise_id and session_id=v_session;
  return to_jsonb(v_result);
end;
$$;

create function public.set_training_exercise_status(p_workout_exercise_id uuid,p_status text)
returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_session uuid;
  v_planned integer;
  v_count integer;
begin
  if v_user is null then raise exception 'Authentication required' using errcode='42501'; end if;
  if p_status is null or p_status not in ('pending','completed','skipped') then
    raise exception 'Invalid status' using errcode='22023';
  end if;
  select s.id into v_session from public.workout_sessions s join public.workout_exercises e on e.session_id=s.id
    where e.id=p_workout_exercise_id and s.user_id=v_user for update of s;
  if not found then raise exception 'Exercise unavailable' using errcode='42501'; end if;
  if p_status='completed' then
    select re.planned_sets into v_planned from public.workout_exercises we
      join public.workout_sessions ws on ws.id=we.session_id
      join public.routine_exercises re on re.routine_id=ws.routine_id
        and re.position=we.position and re.exercise_id=we.exercise_id
      where we.id=p_workout_exercise_id;
    select count(*) into v_count from public.performed_sets where workout_exercise_id=p_workout_exercise_id;
    if v_count < coalesce(v_planned,1) or exists (
      select 1 from generate_series(1,coalesce(v_planned,1)) as required(n)
      where not exists(select 1 from public.performed_sets ps
        where ps.workout_exercise_id=p_workout_exercise_id and ps.set_number=required.n)
    ) then raise exception 'Missing recorded sets' using errcode='22023'; end if;
  end if;
  update public.workout_exercises set completion_status=p_status where id=p_workout_exercise_id;
  return p_workout_exercise_id;
end;
$$;

revoke all on function public.save_training_routine(uuid,text,text,jsonb) from public,anon;
revoke all on function public.select_training_routine(uuid,uuid,date,uuid) from public,anon;
revoke all on function public.save_training_set(uuid,integer,numeric,integer) from public,anon;
revoke all on function public.set_training_exercise_status(uuid,text) from public,anon;
grant execute on function public.save_training_routine(uuid,text,text,jsonb) to authenticated;
grant execute on function public.select_training_routine(uuid,uuid,date,uuid) to authenticated;
grant execute on function public.save_training_set(uuid,integer,numeric,integer) to authenticated;
grant execute on function public.set_training_exercise_status(uuid,text) to authenticated;
