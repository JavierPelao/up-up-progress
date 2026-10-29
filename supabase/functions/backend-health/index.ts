Deno.serve((req: Request) => {
  if (req.method !== "GET") {
    return Response.json(
      { error: "Método no permitido" },
      { status: 405 },
    );
  }

  return Response.json({
    ok: true,
    service: "up-up-progress",
    message: "Backend funcionando correctamente",
  });
});
