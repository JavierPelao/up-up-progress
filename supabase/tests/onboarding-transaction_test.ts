import { strict as assert } from "node:assert";
import { PGlite } from "npm:@electric-sql/pglite@0.5.15";

const root = new URL("../migrations/", import.meta.url);
const a = "00000000-0000-0000-0000-000000000011";
const b = "00000000-0000-0000-0000-000000000012";
const c = "00000000-0000-0000-0000-000000000013";

Deno.test("alta atómica, idempotencia y permisos en PostgreSQL sintético", async (t) => {
  const db = await PGlite.create();
  const call = "SELECT public.complete_onboarding($1,'Ejemplo',30,175,'male','2026-11-08',70,'maintain_weight','high',$2,$3,$4,$5,$6,$7,$8,$9) AS state";
  const automatic = ["automatic",2473,126,306.78,82.43,0,70,2473.125];
  // Inspección del estado sintético como dueño de la base, sin ampliar permisos de service_role.
  async function inspect<T>(sql: string, args: unknown[]) {
    await db.exec("RESET ROLE");
    try { return await db.query<T>(sql,args); }
    finally { await db.exec("SET ROLE service_role"); }
  }
  try {
    await db.exec([
      "CREATE ROLE anon; CREATE ROLE authenticated; CREATE ROLE service_role BYPASSRLS;",
      "CREATE SCHEMA auth; CREATE TABLE auth.users(id uuid PRIMARY KEY);",
      "CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql AS $$ SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;",
      "INSERT INTO auth.users VALUES ('"+a+"'),('"+b+"'),('"+c+"');",
    ].join("\n"));
    for (const file of [
      "20261026015617_create_profiles.sql","20261026024228_create_user_goals.sql",
      "20261027004817_create_body_weight_logs.sql","20261108230354_align_nutrition_precision.sql",
      "20261108230401_complete_onboarding.sql",
    ]) await db.exec(await Deno.readTextFile(new URL(file,root)));
    await db.exec("SET ROLE service_role");

    await t.step("una cuenta sin perfil no está completa", async () => {
      const { rows } = await db.query<{ state: { is_complete: boolean } }>(
        "SELECT public.get_onboarding_state($1) AS state",[a],
      );
      assert.equal(rows[0].state.is_complete,false);
    });

    await t.step("un fallo después de escribir perfil y peso revierte los tres registros", async () => {
      // El ajuste incorrecto se rechaza en la rutina automática, después de los upsert.
      await assert.rejects(() => db.query(call,[a,...automatic.slice(0,5),-10,70,2473.125]));
      const { rows } = await inspect<{ profiles: string; weights: string; goals: string }>(
        "SELECT (SELECT count(*) FROM public.profiles WHERE user_id=$1)::text AS profiles,(SELECT count(*) FROM public.body_weight_logs WHERE user_id=$1)::text AS weights,(SELECT count(*) FROM public.user_goals WHERE user_id=$1)::text AS goals",[a],
      );
      assert.deepEqual(rows,[{ profiles:"0",weights:"0",goals:"0" }]);
    });

    await t.step("alta automática guarda las tres partes y devuelve los valores persistidos", async () => {
      const { rows } = await db.query<{ state: Record<string,unknown> }>(call,[a,...automatic]);
      const state = rows[0].state;
      assert.equal(state.is_complete,true);
      assert.equal(state.already_completed,false);
      const goal = state.goal as Record<string,unknown>;
      assert.equal(Number(goal.daily_carbs_target_g),306.78);
      assert.equal(Number(goal.estimated_tdee_kcal),2473.125);
      assert.equal((state.profile as Record<string,unknown>).user_id,a);
    });

    await t.step("reintentar o volver a registrarse no duplica ni sobrescribe una cuenta completa", async () => {
      const { rows } = await db.query<{ state: Record<string,unknown> }>(call,[a,...automatic]);
      assert.equal(rows[0].state.already_completed,true);
      await db.query("SELECT public.complete_onboarding($1,'Otro nombre',30,175,'male','2026-11-09',80,'lose_weight','light','manual',2000,100,250,60,NULL,NULL,NULL)",[a]);
      const status = await db.query<{ state: { profile: { display_name: string }; weight: { measured_on: string } } }>(
        "SELECT public.get_onboarding_state($1) AS state",[a],
      );
      assert.equal(status.rows[0].state.profile.display_name,"Ejemplo");
      assert.equal(status.rows[0].state.weight.measured_on,"2026-11-08");
      const count = await inspect<{ count: string }>("SELECT count(*)::text FROM public.user_goals WHERE user_id=$1",[a]);
      assert.equal(count.rows[0].count,"1");
    });

    await t.step("alta manual conserva exactamente las metas y la actividad", async () => {
      const { rows } = await db.query<{ state: { is_complete: boolean; goal: Record<string,unknown> } }>(
        call,[b,"manual",2200,150.55,250.25,65.01,null,null,null],
      );
      assert.equal(rows[0].state.is_complete,true);
      assert.equal(Number(rows[0].state.goal.daily_protein_target_g),150.55);
      assert.equal(rows[0].state.goal.activity_level,"high");
      assert.equal(rows[0].state.goal.estimated_tdee_kcal,null);
    });

    await t.step("los clientes no pueden consultar ni elegir otro propietario por RPC", async () => {
      for (const role of ["authenticated","anon"]) {
        await db.exec("RESET ROLE; SET ROLE "+role);
        await assert.rejects(() => db.query("SELECT public.get_onboarding_state($1)",[a]));
        await assert.rejects(() => db.query(call,[a,...automatic]));
      }
      await db.exec("RESET ROLE; SET ROLE service_role");
    });

    await t.step("un perfil parcial puede completarse sin duplicar el peso del día", async () => {
      await db.exec("RESET ROLE");
      await db.query("INSERT INTO public.profiles(user_id,display_name) VALUES($1,'Parcial')",[c]);
      await db.query("INSERT INTO public.body_weight_logs(user_id,measured_on,weight_kg) VALUES($1,'2026-11-08',60)",[c]);
      await db.exec("SET ROLE service_role");
      await db.query(call,[c,"manual",2200,150,250,65,null,null,null]);
      const { rows } = await db.query<{ state: { is_complete: boolean } }>("SELECT public.get_onboarding_state($1) AS state",[c]);
      assert.equal(rows[0].state.is_complete,true);
      const count = await inspect<{ count: string }>("SELECT count(*)::text FROM public.body_weight_logs WHERE user_id=$1",[c]);
      assert.equal(count.rows[0].count,"1");
    });
  } finally {
    await db.close();
  }
});
