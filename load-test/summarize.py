"""Turn a k6 --summary-export file plus database checks into one Markdown table row.

Usage: summarize.py <summary.md> <scenario> <k6-summary.json> key=value ...
The key=value pairs are the post-run MySQL/Redis checks collected by run-suite.sh.
"""
import json
import os
import sys

HEADER = (
    "| Scenario | Target rate | Requests | Achieved rate | Accepted | Sold out | Limit | 429 | 5xx | Net err | Dropped "
    "| Success P50 | Success P95 | Success P99 | Reject P95 | Oversold | MySQL left | Redis left | Notes |\n"
    "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | --- |\n"
)


def metric(metrics, name, field="count"):
    value = metrics.get(name, {}).get(field)
    return 0 if value is None else value


def ms(metrics, name, field):
    value = metrics.get(name, {}).get(field)
    return "-" if value is None else "%.1f ms" % value


def main():
    summary_path, scenario, k6_path = sys.argv[1:4]
    checks = dict(arg.split("=", 1) for arg in sys.argv[4:])
    with open(k6_path, encoding="utf-8") as source:
        metrics = json.load(source)["metrics"]
    requests = metric(metrics, "http_reqs")
    accepted = metric(metrics, "orders_accepted")
    total, remaining, sold = (int(checks.get(key, 0)) for key in ("total", "remaining", "sold"))
    oversold = "NO" if sold == total - remaining and remaining >= 0 and sold <= total else "YES"
    row = "| %s | %s/s | %d | %.1f/s | %d | %d | %d | %d | %d | %d | %d | %s | %s | %s | %s | %s | %s | %s | %s |\n" % (
        scenario,
        checks.get("rate", "-"),
        requests,
        metric(metrics, "http_reqs", "rate"),
        accepted,
        metric(metrics, "orders_sold_out"),
        metric(metrics, "orders_limit_exceeded"),
        metric(metrics, "orders_throttled"),
        metric(metrics, "orders_server_errors"),
        metric(metrics, "orders_transport_errors"),
        metric(metrics, "dropped_iterations"),
        ms(metrics, "order_success_latency", "med"),
        ms(metrics, "order_success_latency", "p(95)"),
        ms(metrics, "order_success_latency", "p(99)"),
        ms(metrics, "order_reject_latency", "p(95)"),
        oversold,
        remaining,
        checks.get("redis", "-"),
        checks.get("notes", ""),
    )
    new_file = not os.path.exists(summary_path)
    with open(summary_path, "a", encoding="utf-8") as summary:
        if new_file:
            summary.write(HEADER)
        summary.write(row)
    sys.stdout.write(row)


if __name__ == "__main__":
    main()
