// PostgreSQL WASM en memoria. Nunca utiliza credenciales ni conecta a Supabase.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';

const dependency = process.env.PGLITE_MODULE ??
  join(process.env.TEMP ?? '/tmp', 'upup-training-tests', 'node_modules', '@electric-sql', 'pglite', 'dist', 'index.js');
const { PGlite } = await import(pathToFileURL(dependency).href);
const db = new PGlite();
const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const userA = randomUUID(), userB = randomUUID(), common = randomUUID();
let passed = 0;
async function test(name, fn) { await fn(); console.log('PASS ' + name); passed++; }
async function login(id) {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub',$1,false)", [id]);
  await db.exec('set role authenticated');
}
async function count(table, column, id) {
  return Number((await db.query('select count(*)::int as n from public.' + table + ' where ' + column + '=$1', [id])).rows[0].n);
}
async function createRoutine(id, name, items) {
  return (await db.query('select public.save_training_routine($1,$2,$3,$4::jsonb) as id',
    [id, name, 'Ejemplo sintético', JSON.stringify(items)])).rows[0].id;
}
async function choose(id, routine, old = null) {
  return (await db.query("select public.select_training_routine($1,$2,current_date,$3) as id", [id, routine, old])).rows[0].id;
}
try {
  await db.exec(`
    create role anon;
    create role authenticated;
    create schema auth;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as
      $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    grant usage on schema public,auth to authenticated,anon;
  `);
  for (const file of [
    '20261026030305_create_exercises.sql', '20261027002837_create_routines.sql',
    '20261027003313_create_routine_exercises.sql', '20261027004143_create_workout_sessions.sql',
    '20261027004522_create_workout_exercises.sql', '20261027004619_create_performed_sets.sql',
    '20261111021525_training_workflow.sql', '20261111085720_manage_training_routines.sql',
    '20261111132923_complete_training_with_zero_reps.sql',
  ]) await db.exec(await readFile(join(root, 'supabase', 'migrations', file), 'utf8'));
  await db.exec('grant select,insert,update,delete on all tables in schema public to authenticated');
  await db.query('insert into auth.users values($1),($2)', [userA,userB]);
  await db.query('insert into public.exercises(id,name) values($1,$2)', [common,'Ejercicio común']);
  await login(userA);
  const routine = randomUUID();
  const items = [
    { id: randomUUID(), exercise_id: common, name: 'Ejercicio común', planned_sets: 2, planned_reps: 10 },
    { id: randomUUID(), exercise_id: null, name: 'Ejercicio propio', planned_sets: 1, planned_reps: 12 },
  ];

  await test('rutina completa, identidad de ejercicio y reintento sin duplicados', async () => {
    assert.equal(await createRoutine(routine,'Rutina A',items), routine);
    assert.equal(await createRoutine(routine,'Rutina A',items), routine);
    assert.equal(await count('routines','id',routine),1);
    assert.equal(await count('routine_exercises','routine_id',routine),2);
    await assert.rejects(createRoutine(routine,'Contenido cambiado',items));
  });
  await test('rutina inválida no deja cabecera ni ejercicios parciales', async () => {
    const invalid = randomUUID();
    await assert.rejects(createRoutine(invalid,'Inválida',[items[0],{...items[1],planned_reps:0}]));
    assert.equal(await count('routines','id',invalid),0);
  });
  const firstSession = randomUUID();
  await test('iniciar sesión y repetir UUID no duplica el entrenamiento', async () => {
    assert.equal(await choose(firstSession,routine),firstSession);
    assert.equal(await choose(firstSession,routine),firstSession);
    assert.equal(await count('workout_exercises','session_id',firstSession),2);
  });
  const exercises = (await db.query('select id from public.workout_exercises where session_id=$1 order by position',[firstSession])).rows;
  const firstExercise = exercises[0].id, secondExercise = exercises[1].id;
  await test('peso vacío equivale a cero y cada serie se actualiza sin duplicación', async () => {
    const result = await db.query('select public.save_training_set($1,1,null,10) as data',[firstExercise]);
    assert.equal(Number(result.rows[0].data.weight_kg),0);
    await db.query('select public.save_training_set($1,1,0,11)',[firstExercise]);
    assert.equal(await count('performed_sets','workout_exercise_id',firstExercise),1);
    await assert.rejects(db.query('select public.save_training_set($1,2,-1,10)',[firstExercise]));
    await assert.rejects(db.query('select public.save_training_set($1,2,1.234,10)',[firstExercise]));
  });
  await test('completar exige las series, omitir no inventa registros', async () => {
    await assert.rejects(db.query("select public.set_training_exercise_status($1,'completed')",[firstExercise]));
    await db.query('select public.save_training_set($1,3,20,8)',[firstExercise]);
    await assert.rejects(db.query("select public.set_training_exercise_status($1,'completed')",[firstExercise]));
    await db.query('delete from public.performed_sets where workout_exercise_id=$1 and set_number=3',[firstExercise]);
    await db.query('select public.save_training_set($1,2,22.50,8)',[firstExercise]);
    await db.query("select public.set_training_exercise_status($1,'completed')",[firstExercise]);
    await db.query("select public.set_training_exercise_status($1,'skipped')",[secondExercise]);
    assert.equal(await count('performed_sets','workout_exercise_id',secondExercise),0);
    await db.query('select public.save_training_set($1,2,25,9)',[firstExercise]);
    assert.equal((await db.query('select completion_status from public.workout_exercises where id=$1',[firstExercise])).rows[0].completion_status,'pending');
  });
  await test('completar rellena faltantes con cero, conserva valores y no duplica al reintentar', async () => {
    const session = randomUUID();
    await choose(session,routine);
    const id = (await db.query('select id from public.workout_exercises where session_id=$1 order by position',[session])).rows[0].id;
    await db.query('select public.save_training_set($1,1,12.5,8)',[id]);
    const result = (await db.query('select public.complete_training_exercise($1) as data',[id])).rows[0].data;
    assert.equal(result.completion_status,'completed');
    assert.deepEqual(result.performed_sets.map(x=>[x.set_number,Number(x.weight_kg),x.reps]),[[1,12.5,8],[2,0,0]]);
    const retried = (await db.query('select public.complete_training_exercise($1) as data',[id])).rows[0].data;
    assert.deepEqual(retried,result);
    await db.query("select public.set_training_exercise_status($1,'pending')",[id]);
    assert.equal(await count('performed_sets','workout_exercise_id',id),2);
    await db.query('select public.save_training_set($1,2,0,0)',[id]);
    await assert.rejects(db.query('select public.save_training_set($1,2,0,-1)',[id]));
    await assert.rejects(db.query('select public.save_training_set($1,2,0,1001)',[id]));
    await login(userB);
    await assert.rejects(db.query('select public.complete_training_exercise($1)',[id]));
    await login(userA);
  });
  await test('fallo al completar revierte las series rellenadas', async () => {
    const session = randomUUID();
    await choose(session,routine);
    const id = (await db.query('select id from public.workout_exercises where session_id=$1 order by position',[session])).rows[0].id;
    await db.query('select public.save_training_set($1,1,15,8)',[id]);
    await db.exec('reset role');
    await db.exec(`
      create function public.test_reject_completion() returns trigger language plpgsql as $$
      begin
        if new.completion_status='completed' then raise exception 'synthetic failure'; end if;
        return new;
      end; $$;
      create trigger test_reject_completion before update on public.workout_exercises
        for each row execute function public.test_reject_completion();
    `);
    await login(userA);
    await assert.rejects(db.query('select public.complete_training_exercise($1)',[id]));
    assert.equal(await count('performed_sets','workout_exercise_id',id),1);
    assert.equal((await db.query('select completion_status from public.workout_exercises where id=$1',[id])).rows[0].completion_status,'pending');
    await db.exec('reset role');
    await db.exec('drop trigger test_reject_completion on public.workout_exercises; drop function public.test_reject_completion()');
    await login(userA);
  });
  await test('otro usuario no lee, cambia estados ni reemplaza la sesión ajena', async () => {
    await login(userB);
    assert.equal(await count('workout_sessions','id',firstSession),0);
    await assert.rejects(db.query('select public.save_training_set($1,1,40,8)',[firstExercise]));
    await assert.rejects(db.query("select public.set_training_exercise_status($1,'skipped')",[firstExercise]));
    await assert.rejects(choose(randomUUID(),routine,firstSession));
    await login(userA);
    assert.equal(await count('performed_sets','workout_exercise_id',firstExercise),2);
  });
  await test('un fallo después del borrado revierte la sustitución completa', async () => {
    const forbidden = randomUUID(), badRoutine = randomUUID();
    await db.exec('reset role');
    await db.query('insert into public.exercises(id,name,created_by) values($1,$2,$3)',[forbidden,'Privado B',userB]);
    await db.query('insert into public.routines(id,user_id,name) values($1,$2,$3)',[badRoutine,userA,'Fixture incompatible']);
    await db.query('insert into public.routine_exercises(routine_id,exercise_id,position,planned_sets,planned_reps) values($1,$2,1,1,10)',[badRoutine,forbidden]);
    await login(userA);
    await assert.rejects(choose(randomUUID(),badRoutine,firstSession));
    assert.equal(await count('workout_sessions','id',firstSession),1);
    assert.equal(await count('performed_sets','workout_exercise_id',firstExercise),2);
  });
  await test('sustituir borra solo la sesión confirmada y es idempotente', async () => {
    const nextRoutine = randomUUID(), next = randomUUID();
    await createRoutine(nextRoutine,'Rutina B',[{...items[0],id:randomUUID()}]);
    await choose(next,nextRoutine,firstSession);
    assert.equal(await count('workout_sessions','id',firstSession),0);
    assert.equal(await count('performed_sets','workout_exercise_id',firstExercise),0);
    await choose(next,nextRoutine,firstSession);
    assert.equal(await count('workout_sessions','id',next),1);
  });
  const managed = randomUUID(), edited = randomUUID(), managementSession = randomUUID();
  let managementExercise;
  const editedItems = [{...items[0],id:randomUUID(),planned_sets:1,planned_reps:15}];
  async function replace(original,next,name,nextItems) {
    return (await db.query('select public.replace_training_routine($1,$2,$3,$4,$5::jsonb) as id',
      [original,next,name,'Modificada',JSON.stringify(nextItems)])).rows[0].id;
  }
  await test('editar crea una versión y conserva nombre, objetivos y series del entrenamiento anterior', async () => {
    await createRoutine(managed,'Plantilla original',[{...items[0],id:randomUUID()}]);
    await choose(managementSession,managed);
    managementExercise = (await db.query('select id from public.workout_exercises where session_id=$1',[managementSession])).rows[0].id;
    await db.query('select public.save_training_set($1,1,12.5,10)',[managementExercise]);
    await db.query('select public.save_training_set($1,2,14,9)',[managementExercise]);
    assert.equal(await replace(managed,edited,'Plantilla editada',editedItems),edited);
    assert.equal(await replace(managed,edited,'Plantilla editada',editedItems),edited);
    const before = (await db.query('select r.name,r.is_archived,re.planned_sets,re.planned_reps from public.workout_sessions ws join public.routines r on r.id=ws.routine_id join public.routine_exercises re on re.routine_id=r.id where ws.id=$1',[managementSession])).rows[0];
    assert.deepEqual(before,{name:'Plantilla original',is_archived:true,planned_sets:2,planned_reps:10});
    assert.equal(await count('performed_sets','workout_exercise_id',managementExercise),2);
    await db.query("select public.set_training_exercise_status($1,'completed')",[managementExercise]);
    const current = (await db.query('select name,is_archived,replaces_routine_id from public.routines where id=$1',[edited])).rows[0];
    assert.deepEqual(current,{name:'Plantilla editada',is_archived:false,replaces_routine_id:managed});
  });
  await test('eliminar retira la plantilla y conserva entrenamiento; reintentar no borra datos', async () => {
    await db.query('select public.archive_training_routine($1)',[edited]);
    await db.query('select public.archive_training_routine($1)',[edited]);
    assert.equal(await count('workout_sessions','id',managementSession),1);
    assert.equal(await count('performed_sets','workout_exercise_id',managementExercise),2);
    await assert.rejects(choose(randomUUID(),edited));
    await assert.rejects(choose(randomUUID(),managed));
    assert.equal(await choose(managementSession,managed),managementSession);
  });
  await test('edición inválida revierte toda la transacción y una edición obsoleta se rechaza', async () => {
    const valid = randomUUID(), invalid = randomUUID();
    await createRoutine(valid,'Conservar',[{...items[0],id:randomUUID()}]);
    await assert.rejects(replace(valid,invalid,'Inválida',[{...editedItems[0],planned_sets:0}]));
    assert.equal(await count('routines','id',invalid),0);
    assert.equal((await db.query('select is_archived from public.routines where id=$1',[valid])).rows[0].is_archived,false);
    await assert.rejects(replace(managed,randomUUID(),'Obsoleta',[{...editedItems[0],id:randomUUID()}]));
  });
  await test('otro usuario no edita ni elimina una rutina ajena', async () => {
    const privateRoot = randomUUID();
    await createRoutine(privateRoot,'Privada A',[{...items[0],id:randomUUID()}]);
    await login(userB);
    await assert.rejects(db.query('insert into public.routines(id,user_id,name,replaces_routine_id) values($1,$2,$3,$4)',
      [randomUUID(),userB,'Referencia ajena',privateRoot]),{code:'23503'});
    await assert.rejects(db.query('select public.archive_training_routine($1)',[managed]));
    await assert.rejects(replace(managed,randomUUID(),'Ajena',editedItems));
    await login(userA);
    assert.equal(await count('performed_sets','workout_exercise_id',managementExercise),2);
  });
  await test('funciones invoker, acceso anónimo denegado y sesión requerida', async () => {
    await db.exec('reset role');
    const functions = (await db.query("select proname,prosecdef from pg_proc where proname in ('save_training_routine','select_training_routine','save_training_set','set_training_exercise_status','replace_training_routine','archive_training_routine','complete_training_exercise')")).rows;
    assert.equal(functions.length,7);
    assert.ok(functions.every(x=>!x.prosecdef));
    await db.exec('set role anon');
    await assert.rejects(db.query('select public.complete_training_exercise($1)',[firstExercise]));
    await assert.rejects(db.query("select public.set_training_exercise_status($1,'skipped')",[firstExercise]));
    await assert.rejects(db.query('select public.archive_training_routine($1)',[managed]));
    await assert.rejects(replace(managed,randomUUID(),'Anónima',editedItems));
    await login('');
    await assert.rejects(createRoutine(randomUUID(),'Sin sesión',items));
  });
  console.log(passed + ' pruebas PostgreSQL aprobadas; 0 conexiones remotas y 0 solicitudes de IA.');
} finally {
  await db.close();
}
