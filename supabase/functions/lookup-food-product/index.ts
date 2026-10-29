
import { createClient } from "@supabase/supabase-js";

Deno.serve(async (req: Request) => {
  // 1. Solo permitimos solicitudes GET.
  if (req.method !== "GET") {
    return Response.json(
      { error: "Método no permitido" },
      { status: 405 },
    );
  }

  // 2. Obtener y validar el código de barras.
  const barcode = new URL(req.url).searchParams.get("barcode");

  if (!barcode || !/^\d{8,14}$/.test(barcode)) {
    return Response.json(
      { error: "Código de barras inválido" },
      { status: 400 },
    );
  }

  // 3. Comprobar que se envió un token.
  const authHeader = req.headers.get("Authorization");

  if (!authHeader?.startsWith("Bearer ")) {
    return Response.json(
      { error: "Autenticación requerida" },
      { status: 401 },
    );
  }

  // 4. Crear el cliente Supabase con el token del usuario.
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

  // 5. Verificar la identidad del usuario.
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

// Buscar primero el producto en nuestra base de datos.
const { data: localProduct, error: localError } = await supabase
  .from("food_products")
  .select("*")
  .eq("barcode", barcode)
  .maybeSingle();

if (localError) {
  return Response.json(
    { error: "Error al consultar el catálogo local" },
    { status: 500 },
  );
}

if (localProduct) {
  return Response.json({
    source: "local",
    barcode: localProduct.barcode,
    name: localProduct.name,
    brand: localProduct.brand,
    nutrition_basis: localProduct.nutrition_basis,
    energy_kcal_100: localProduct.energy_kcal_100,
    protein_g_100: localProduct.protein_g_100,
    carbs_g_100: localProduct.carbs_g_100,
    fat_g_100: localProduct.fat_g_100,
  });
}

  // 6. Preparar la consulta a Open Food Facts.
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
        { error: "Producto no encontrado" },
        { status: 404 },
      );
    }

    // 7. Extraer los campos nutricionales.
    const product = data.product;
    const nutrients = product.nutriments ?? {};

	const nutritionBasis =
  product.nutrition_data_per === "100g"
    ? "100g"
    : product.nutrition_data_per === "100ml"
      ? "100ml"
      : null;

const suffix = nutritionBasis === "100ml" ? "_100ml" : "_100g";

   return Response.json({
  source: "open_food_facts",
  barcode: data.code,
  name: product.product_name ?? null,
  brand: product.brands ?? null,
  nutrition_basis: nutritionBasis,
  energy_kcal_100: nutrients["energy-kcal_100g"] ?? null,
  protein_g_100: nutrients.proteins_100g ?? null,
  carbs_g_100: nutrients.carbohydrates_100g ?? null,
  fat_g_100: nutrients.fat_100g ?? null,
});
  } catch {
    return Response.json(
      { error: "Error de comunicación con Open Food Facts" },
      { status: 502 },
    );
  }
});
