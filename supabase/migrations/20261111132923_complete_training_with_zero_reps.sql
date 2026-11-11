-- Preparada: requiere aprobación antes de aplicarse al proyecto compartido.
-- Los registros históricos existentes no cambian.
alter table public.performed_sets drop constraint performed_sets_reps_check;
alter table public.performed_sets add constraint performed_sets_reps_check check (reps >= 0);

create or replace function public.save_training_set(
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
    or p_reps is null or p_reps not between 0 and 1000
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

-- Operación atómica e idempotente: rellenar faltantes, completar y devolver datos confirmados.
create function public.complete_training_exercise(p_workout_exercise_id uuid)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_session uuid;
  v_planned integer;
  v_sets jsonb;
begin
  if v_user is null then raise exception 'Authentication required' using errcode='42501'; end if;
  select s.id into v_session from public.workout_sessions s
    join public.workout_exercises e on e.session_id=s.id
    where e.id=p_workout_exercise_id and s.user_id=v_user for update of s;
  if not found then raise exception 'Exercise unavailable' using errcode='42501'; end if;
  select re.planned_sets into v_planned from public.workout_exercises we
    join public.workout_sessions ws on ws.id=we.session_id
    join public.routine_exercises re on re.routine_id=ws.routine_id
      and re.position=we.position and re.exercise_id=we.exercise_id
    where we.id=p_workout_exercise_id;
  if v_planned is null or v_planned not between 1 and 30 then
    raise exception 'Planned exercise unavailable' using errcode='22023';
  end if;
  insert into public.performed_sets(workout_exercise_id,set_number,weight_kg,reps)
    select p_workout_exercise_id,n,0,0 from generate_series(1,v_planned) as required(n)
    on conflict(workout_exercise_id,set_number) do nothing;
  perform public.set_training_exercise_status(p_workout_exercise_id,'completed');
  select coalesce(jsonb_agg(to_jsonb(ps) order by ps.set_number),'[]'::jsonb)
    into v_sets from public.performed_sets ps where ps.workout_exercise_id=p_workout_exercise_id;
  return jsonb_build_object('id',p_workout_exercise_id,'completion_status','completed','performed_sets',v_sets);
end;
$$;

revoke all on function public.save_training_set(uuid,integer,numeric,integer) from public,anon;
revoke all on function public.complete_training_exercise(uuid) from public,anon;
grant execute on function public.save_training_set(uuid,integer,numeric,integer) to authenticated;
grant execute on function public.complete_training_exercise(uuid) to authenticated;
