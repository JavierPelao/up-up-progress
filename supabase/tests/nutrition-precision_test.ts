import { strict as assert } from "node:assert";
import { PGlite } from "npm:@electric-sql/pglite@0.5.15";

// PostgreSQL efímero en memoria. Nunca conecta al proyecto Supabase.
const userA = "00000000-0000-0000-0000-000000000001";
const userB = "00000000-0000-0000-0000-000000000002";
const migrationUrl = new URL("../migrations/20261108230354_align_nutrition_precision.sql", import.meta.url);

Deno.test("migración de precisión y RPC con datos exclusivamente sintéticos", async (t) => {
  const db = await PGlite.create();
  try {
    await db.exec([
      "CREATE ROLE anon;",
      "CREATE ROLE authenticated;",
      "CREATE ROLE service_role BYPASSRLS;",
      "CREATE SCHEMA auth;",
      "CREATE TABLE auth.users(id uuid PRIMARY KEY);",
      "CREATE TABLE public.profiles(user_id uuid PRIMARY KEY REFERENCES auth.users(id), display_name text);",
      "CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;",
      "GRANT USAGE ON SCHEMA auth TO authenticated, service_role;",
      "INSERT INTO auth.users VALUES ('" + userA + "'), ('" + userB + "');",
      "CREATE TABLE public.user_goals (",
      "id uuid PRIMARY KEY DEFAULT gen_random_uuid(),",
      "user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,",
      "goal_type text NOT NULL CONSTRAINT user_goals_goal_type_check CHECK (goal_type IN ('gain_muscle','lose_weight','maintain_weight','body_recomposition')),",
      "daily_calorie_target integer CHECK (daily_calorie_target > 0),",
      "effective_at timestamptz NOT NULL DEFAULT now(),",
      "activity_level text, nutrition_goal_mode text,",
      "daily_protein_target_g numeric(7,1), daily_carbs_target_g numeric(7,1), daily_fat_target_g numeric(7,1),",
      "calorie_adjustment_pct numeric(5,2), calculation_weight_kg numeric(6,2), estimated_tdee_kcal numeric(8,1));",
      "CREATE SCHEMA private;",
      "CREATE FUNCTION private.insert_manual_nutrition_goal(text,integer,numeric,numeric,numeric) RETURNS public.user_goals LANGUAGE sql AS $$ SELECT NULL::public.user_goals $$;",
      "CREATE FUNCTION public.save_manual_nutrition_goal(text,integer,numeric,numeric,numeric) RETURNS public.user_goals LANGUAGE sql AS $$ SELECT private.insert_manual_nutrition_goal($1,$2,$3,$4,$5) $$;",
      "ALTER TABLE public.user_goals ENABLE ROW LEVEL SECURITY;",
      "CREATE POLICY own_select ON public.user_goals FOR SELECT TO authenticated USING (auth.uid() = user_id);",
      "CREATE POLICY own_insert ON public.user_goals FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);",
      "GRANT SELECT, INSERT ON public.user_goals TO anon, authenticated;",
      "INSERT INTO public.user_goals(user_id,goal_type,daily_calorie_target,daily_protein_target_g) VALUES ('" + userB + "','body_recomposition',2000,123.4);",
      "INSERT INTO public.user_goals(user_id,goal_type,activity_level) VALUES ('" + userB + "','maintain_weight','sedentary'), ('" + userB + "','maintain_weight','moderate');",
    ].join("\n"));
    await db.exec(await Deno.readTextFile(migrationUrl));

    await t.step("columnas conservan dos decimales y seis decimales de TDEE", async () => {
      const { rows } = await db.query<{ column_name: string; numeric_precision: number; numeric_scale: number }>(
        "SELECT column_name,numeric_precision,numeric_scale FROM information_schema.columns WHERE table_name='user_goals' AND column_name IN ('daily_protein_target_g','daily_carbs_target_g','daily_fat_target_g','estimated_tdee_kcal') ORDER BY column_name",
      );
      assert.equal(rows.length, 4);
      for (const row of rows) {
        assert.equal(row.numeric_scale, row.column_name === "estimated_tdee_kcal" ? 6 : 2);
        assert.equal(row.numeric_precision, row.column_name === "estimated_tdee_kcal" ? 13 : 8);
      }
    });

    await t.step("conserva objetivos y actividades históricos retirados", async () => {
      const { rows } = await db.query<{ goal_type: string; activity_level: string | null; protein: string | null }>(
        "SELECT goal_type,activity_level,daily_protein_target_g::text AS protein FROM public.user_goals",
      );
      assert.equal(rows.length, 3);
      assert.ok(rows.some((r) => r.goal_type === "body_recomposition" && r.protein === "123.40"));
      assert.ok(rows.some((r) => r.activity_level === "sedentary"));
      assert.ok(rows.some((r) => r.activity_level === "moderate"));
    });

    await db.query("SELECT set_config('request.jwt.claim.sub',$1,false)", [userA]);
    await db.exec("SET ROLE authenticated");
    await t.step("manual conserva actividad, precisión, propietario e idempotencia", async () => {
      const query = "SELECT id,user_id::text,activity_level,daily_protein_target_g::text AS protein FROM public.save_manual_nutrition_goal('gain_muscle',2500,123.45,250.01,70.99,'high')";
      const { rows } = await db.query<{ id: string; user_id: string; activity_level: string; protein: string }>(query);
      assert.equal(rows[0].user_id, userA);
      assert.equal(rows[0].activity_level, "high");
      assert.equal(rows[0].protein, "123.45");
      assert.equal((await db.query<{ id: string }>(query)).rows[0].id, rows[0].id);
      assert.equal((await db.query("SELECT * FROM public.user_goals")).rows.length, 1);
    });

    await t.step("manual rechaza precisión extra, límites inválidos y el objetivo retirado", async () => {
      for (const args of [
        "'gain_muscle',2500,123.451,250,70",
        "'gain_muscle',20001,123.45,250,70",
        "'gain_muscle',2500,5000.01,250,70",
        "'gain_muscle',2500,'NaN'::numeric,250,70",
        "'body_recomposition',2500,123.45,250,70",
        "'gain_muscle',2500,NULL,250,70",
      ]) await assert.rejects(() => db.query("SELECT public.save_manual_nutrition_goal(" + args + ",'high')"));
    });

    await t.step("authenticated no inserta directamente ni ejecuta el guardado automático", async () => {
      await assert.rejects(() => db.query("INSERT INTO public.user_goals(user_id,goal_type) VALUES ($1,'gain_muscle')", [userA]));
      await assert.rejects(() => db.query(
        "SELECT public.save_automatic_nutrition_goal($1,'maintain_weight','high',2473,126,306.78,82.43,0,70,2473.125)", [userA],
      ));
    });

    await db.exec("RESET ROLE; SET ROLE anon");
    await t.step("anon no ejecuta la RPC manual", async () => {
      await assert.rejects(() => db.query("SELECT public.save_manual_nutrition_goal('gain_muscle',2500,123.45,250,70,'high')"));
    });

    await db.exec("RESET ROLE; SET ROLE service_role");
    await t.step("automático conserva TDEE y macros y evita repetir la meta vigente", async () => {
      const query = "SELECT id,estimated_tdee_kcal::text AS tdee,daily_carbs_target_g::text AS carbs FROM public.save_automatic_nutrition_goal($1,'maintain_weight','high',2473,126,306.78,82.43,0,70,2473.125)";
      const first = await db.query<{ id: string; tdee: string; carbs: string }>(query, [userA]);
      const second = await db.query<{ id: string; tdee: string; carbs: string }>(query, [userA]);
      assert.equal(first.rows[0].id, second.rows[0].id);
      assert.equal(first.rows[0].tdee, "2473.125000");
      assert.equal(first.rows[0].carbs, "306.78");
      const fraction = await db.query<{ tdee: string }>(
        "SELECT estimated_tdee_kcal::text AS tdee FROM public.save_automatic_nutrition_goal($1,'maintain_weight','light',2271,98.35,299.08,75.70,0,70.25,2271.328125)", [userA],
      );
      assert.equal(fraction.rows[0].tdee, "2271.328125");
    });

    await t.step("CHECK y RPC rechazan nuevas actividades retiradas y conservan solo la firma manual nueva", async () => {
      const signatures = await db.query<{ old_public: boolean; old_private: boolean; current_public: boolean }>(
        "SELECT to_regprocedure('public.save_manual_nutrition_goal(text,integer,numeric,numeric,numeric)') IS NULL AS old_public, to_regprocedure('private.insert_manual_nutrition_goal(text,integer,numeric,numeric,numeric)') IS NULL AS old_private, to_regprocedure('public.save_manual_nutrition_goal(text,integer,numeric,numeric,numeric,text)') IS NOT NULL AS current_public",
      );
      assert.deepEqual(signatures.rows, [{ old_public: true, old_private: true, current_public: true }]);
      for (const activity of ["sedentary", "moderate"]) {
        await assert.rejects(() => db.query(
          "INSERT INTO public.user_goals(user_id,goal_type,activity_level) VALUES ($1,'maintain_weight',$2)", [userA,activity],
        ));
        await assert.rejects(() => db.query(
          "SELECT public.save_automatic_nutrition_goal($1,'maintain_weight',$2,2473,126,306.78,82.43,0,70,2473.125)", [userA,activity],
        ));
      }
    });

    await t.step("automático rechaza ajustes, precisión y balance inconsistentes", async () => {
      for (const args of [
        "'maintain_weight','high',2473,126.001,306.78,82.43,0,70,2473.125",
        "'lose_weight','high',2102,140,218,70,-10,70,2473.125",
        "'maintain_weight','high',2473,126,1,82.43,0,70,2473.125",
        "'body_recomposition','high',2473,126,306.78,82.43,0,70,2473.125",
        "'maintain_weight','high',2473,126,306.78,82.43,0,70,2473.1250001",
      ]) await assert.rejects(() => db.query("SELECT public.save_automatic_nutrition_goal($1," + args + ")", [userA]));
    });
  } finally {
    await db.close();
  }
});
