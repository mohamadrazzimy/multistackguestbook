using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();
var gate = new object();
var file = "/data/entries.jsonl";
Directory.CreateDirectory("/data");
app.UseDefaultFiles();
app.UseStaticFiles();
app.MapGet("/api/entries", (HttpContext context) => {
    context.Response.Headers.CacheControl = "no-store";
    lock (gate) {
        var rows = File.Exists(file)
            ? File.ReadAllLines(file).Select(line =>
                JsonSerializer.Deserialize<Entry>(line)!).ToList()
            : new List<Entry>();
        return Results.Json(rows.AsEnumerable().Reverse().Take(100)
            .Select(row => new { comment = row.Comment, createdAt = row.CreatedAt }));
    }
});
app.MapPost("/api/entries", async (HttpRequest request) => {
    if (!(request.ContentType ?? "").StartsWith(
            "application/x-www-form-urlencoded", StringComparison.OrdinalIgnoreCase))
        return Results.Json(new { error = "Use a URL-encoded form." }, statusCode: 415);
    var form = await request.ReadFormAsync();
    var email = form["email"].ToString().Trim();
    var comment = form["comment"].ToString().Trim();
    if (email.Length > 254 || !Regex.IsMatch(email, @"\A[\x21-\x7e]+\z") ||
        !Regex.IsMatch(email, @"\A[^\s@]+@[^\s@]+\.[^\s@]+\z") ||
        comment.Length == 0 || Encoding.UTF8.GetByteCount(comment) > 2000)
        return Results.BadRequest(new {
            error = "Use a valid email and a comment of 1-2000 UTF-8 bytes." });
    try {
        lock (gate) {
            var row = new Entry(email, comment, DateTimeOffset.UtcNow.ToString("O"));
            File.AppendAllText(file, JsonSerializer.Serialize(row) + "\n");
        }
        return Results.Json(new { ok = true }, statusCode: 201);
    } catch (IOException error) {
        app.Logger.LogError(error, "Guestbook write failed");
        return Results.Json(new { error = "Storage unavailable." }, statusCode: 500);
    }
});
app.Run();
record Entry(string Email, string Comment, string CreatedAt);
