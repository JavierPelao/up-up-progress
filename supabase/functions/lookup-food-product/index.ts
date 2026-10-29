Deno.serve(async (req: Request) => {
  if (req.method !== "GET") {
    return Response.json(
      { error: "Método no permitido" },
      { status: 405 },
    );
  }

  const barcode = new URL(req.url).searchParams.get("barcode");

  if (!barcode || !/^\d{8,14}$/.test(barcode)) {
    return Response.json(
      { error: "Código de barras inválido" },
      { status: 400 },
    );
  }

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

    const product = data.product;
    const nutrients = product.nutriments ?? {};

    return Response.json({
      barcode: data.code,
      name: product.product_name ?? null,
      brand: product.brands ?? null,
      nutrition_data_per: product.nutrition_data_per ?? null,
      energy_kcal_100g: nutrients["energy-kcal_100g"] ?? null,
      protein_g_100g: nutrients.proteins_100g ?? null,
      carbs_g_100g: nutrients.carbohydrates_100g ?? null,
      fat_g_100g: nutrients.fat_100g ?? null,
    });
  } catch {
    return Response.json(
      { error: "Error de comunicación con Open Food Facts" },
      { status: 502 },
    );
  }
});
