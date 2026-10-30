
import { createClient } from "@supabase/supabase-js";

Deno.serve(async (req: Request) => {
  // 1. Permitir únicamente solicitudes POST.
  if (req.method !== "POST") {
    return Response.json(
      { error: "Método no permitido" },
      { status: 405 },
    );
  }

  // 2. Comprobar que se envió un token.
  const authHeader = req.headers.get("Authorization");

  if (!authHeader?.startsWith("Bearer ")) {
    return Response.json(
      { error: "Autenticación requerida" },
      { status: 401 },
    );
  }

  // 3. Crear el cliente Supabase del usuario (respeta RLS).
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    {
      global: {
        headers: {
          Authorization: authHeader,
        },
      },
    },
  );

  // 4. Verificar la sesión del usuario.
  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser();

  if (authError || !user) {
    return Response.json(
      { error: "Sesión inválida o expirada" },
      { status: 401 },
    );
  }

  // 5. Crear el cliente administrativo.
  // Esta clave nunca debe enviarse a Android.
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!serviceRoleKey) {
    return Response.json(
      { error: "Configuración del backend incompleta" },
      { status: 500 },
    );
  }

  const adminSupabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    serviceRoleKey,
  );

  // 6. Leer el cuerpo JSON.
  let body: { barcode?: unknown };

  try {
    body = await req.json();
  } catch {
    return Response.json(
      { error: "JSON inválido" },
      { status: 400 },
    );
  }

  // 7. Validar el código de barras.
  const barcode = body?.barcode;

  if (
    typeof barcode !== "string" ||
    !/^\d{8,14}$/.test(barcode)
  ) {
    return Response.json(
      { error: "Código de barras inválido" },
      { status: 400 },
    );
  }

  // 8. Buscar primero en el catálogo visible para el usuario.
  const {
    data: localProduct,
    error: localError,
  } = await supabase
    .from("food_products")
    .select("id, barcode, name, visibility_status")
    .eq("barcode", barcode)
    .maybeSingle();

  if (localError) {
    return Response.json(
      { error: "Error al consultar el catálogo local" },
      { status: 500 },
    );
  }

  // 9. Si el usuario puede ver el producto, reutilizarlo.
  if (localProduct) {
    return Response.json({
      ok: true,
      source: "local",
      product_id: localProduct.id,
      barcode: localProduct.barcode,
      name: localProduct.name,
      visibility_status: localProduct.visibility_status,
    });
  }

  // 10. Comprobar si el código ya existe, aunque RLS lo oculte.
  // IMPORTANTE: esto ocurre ANTES de consultar Open Food Facts.
  const {
    data: existingProduct,
    error: existingError,
  } = await adminSupabase
    .from("food_products")
    .select("id")
    .eq("barcode", barcode)
    .maybeSingle();

  if (existingError) {
    return Response.json(
      { error: "Error al verificar el catálogo" },
      { status: 500 },
    );
  }

  if (existingProduct) {
    // No revelar datos de productos privados.
    return Response.json(
      {
        error: "El código de barras ya está registrado",
        code: "BARCODE_CONFLICT",
      },
      { status: 409 },
    );
  }

  // 11. Consultar Open Food Facts si no existe localmente.
  const url =
    `https://world.openfoodfacts.org/api/v2/product/${barcode}.json` +
    "?fields=code,product_name,brands,nutriments,nutrition_data_per";

  try {
    const response = await fetch(url, {
      headers: {
        "User-Agent":
          "UpUpProgress/0.1 (https://github.com/JavierPelao/up-up-progress)",
      },
    });

    if (!response.ok) {
      return Response.json(
        { error: "No se pudo consultar Open Food Facts" },
        { status: 502 },
      );
    }

    const data = await response.json();

    if (data.status !== 1 || !data.product) {
      return Response.json(
        { error: "Producto no encontrado en Open Food Facts" },
        { status: 404 },
      );
    }

    // 12. Extraer y validar el producto.
    const product = data.product;
    const nutrients = product.nutriments ?? {};

    const productName =
      typeof product.product_name === "string"
        ? product.product_name.trim()
        : "";

    if (!productName) {
      return Response.json(
        { error: "El producto no tiene un nombre válido" },
        { status: 422 },
      );
    }

    if (data.code !== barcode) {
      return Response.json(
        { error: "El código de barras no coincide con la consulta" },
        { status: 502 },
      );
    }

    // 13. Determinar la base nutricional.
    const nutritionBasis =
      product.nutrition_data_per === "100g"
        ? "100g"
        : product.nutrition_data_per === "100ml"
          ? "100ml"
          : null;

    // 14. Aceptar solo valores nutricionales válidos.
    const validNutrient = (value: unknown): number | null => {
      if (
        typeof value === "number" &&
        Number.isFinite(value) &&
        value >= 0
      ) {
        return value;
      }

      return null;
    };

    const nutrition = {
      nutrition_basis: nutritionBasis,
      energy_kcal_100: validNutrient(
        nutrients["energy-kcal_100g"],
      ),
      protein_g_100: validNutrient(
        nutrients.proteins_100g,
      ),
      carbs_g_100: validNutrient(
        nutrients.carbohydrates_100g,
      ),
      fat_g_100: validNutrient(
        nutrients.fat_100g,
      ),
    };

    // 15. Guardar el producto validado desde Open Food Facts.
    const {
      data: savedProduct,
      error: insertError,
    } = await adminSupabase
      .from("food_products")
      .insert({
        barcode,
        name: productName,
        brand:
          typeof product.brands === "string"
            ? product.brands
            : null,
        ...nutrition,
        source: "open_food_facts",
        visibility_status: "published",
        created_by: null,
        last_synced_at: new Date().toISOString(),
      })
      .select("id, barcode, name, visibility_status")
      .single();

    if (insertError) {
      // 23505: otro proceso pudo insertar el mismo código.
      if (insertError.code === "23505") {
        return Response.json(
          {
            error: "El código de barras ya está registrado",
            code: "BARCODE_CONFLICT",
          },
          { status: 409 },
        );
      }

      console.error(
        "Error al guardar producto:",
        insertError.code,
      );

      return Response.json(
        { error: "No se pudo guardar el producto" },
        { status: 500 },
      );
    }

    // 16. Devolver el identificador del producto guardado.
    return Response.json({
      ok: true,
      source: "open_food_facts",
      product_id: savedProduct.id,
      barcode: savedProduct.barcode,
      name: savedProduct.name,
      visibility_status: savedProduct.visibility_status,
      ...nutrition,
    });
  } catch {
    return Response.json(
      { error: "Error de comunicación con Open Food Facts" },
      { status: 502 },
    );
  }
});
