local token = os.getenv("TOKEN")
if not token or token == "" then
  error("TOKEN env var is required (a JWT accessToken). See benchmark/README.md.")
end

local ticketTypeId = os.getenv("TICKET_TYPE_ID") or "1"
local quantity = os.getenv("QUANTITY") or "1"

wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.headers["Authorization"] = "Bearer " .. token
wrk.body = string.format('{"ticketTypeId":%s,"quantity":%s}', ticketTypeId, quantity)

done = function(summary, latency, requests)
  io.write("\n")
  io.write(string.format("requests      : %d\n", summary.requests))
  io.write(string.format("duration_s    : %.1f\n", summary.duration / 1000000))
  io.write(string.format("rps           : %.1f\n", summary.requests / (summary.duration / 1000000)))
  io.write(string.format("latency_med_ms: %.0f\n", latency:percentile(50) / 1000))
  io.write(string.format("latency_p95_ms: %.0f\n", latency:percentile(95) / 1000))
  io.write(string.format("latency_p99_ms: %.0f\n", latency:percentile(99) / 1000))
  io.write(string.format("socket_errors : connect=%d read=%d write=%d timeout=%d\n",
    summary.errors.connect, summary.errors.read, summary.errors.write, summary.errors.timeout))
  io.write("NOTE: check wrk's own 'Non-2xx or 3xx responses' line above -- it must be absent (0).\n")
  io.write("If present, the per-user rate limiter was not raised; the run is invalid. See README.\n")
end
