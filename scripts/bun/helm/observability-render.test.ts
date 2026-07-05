import { beforeAll, describe, expect, test } from "bun:test";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../..", import.meta.url));
let manifest = "";
let documents: string[] = [];

function renderChart(): string {
  const result = Bun.spawnSync({
    cmd: [
      "helm",
      "template",
      "bank",
      "helm/bank",
      "--namespace",
      "dev",
      "-f",
      "helm/bank/values-dev.yaml",
    ],
    cwd: root,
    stdout: "pipe",
    stderr: "pipe",
  });
  if (result.exitCode !== 0) {
    throw new Error(result.stderr.toString());
  }
  return result.stdout.toString();
}

function resources(kind: string): string[] {
  return documents.filter((document) =>
    new RegExp(`^kind: ${kind}$`, "m").test(document),
  );
}

function namedResource(kind: string, name: string): string {
  const resource = resources(kind).find((document) =>
    new RegExp(`^  name: ${name}$`, "m").test(document),
  );
  if (!resource) {
    throw new Error(`${kind}/${name} was not rendered`);
  }
  return resource;
}

beforeAll(() => {
  manifest = renderChart();
  documents = manifest.split(/\r?\n---\r?\n/);
});

describe("observability Helm render", () => {
  test("uses exact image tags", () => {
    expect(manifest).toContain("openzipkin/zipkin:3.6.1");
    expect(manifest).toContain(
      "docker.elastic.co/elasticsearch/elasticsearch:9.4.3",
    );
    expect(manifest).toContain("docker.elastic.co/logstash/logstash:9.4.3");
    expect(manifest).toContain("docker.elastic.co/kibana/kibana:9.4.3");
    expect(manifest).toContain("curlimages/curl:8.21.0");
    expect(manifest).not.toMatch(/(?:zipkin|elasticsearch|logstash|kibana):latest/);
  });

  test.each([
    ["zipkin", 9411],
    ["elasticsearch", 9200],
    ["logstash", 5000],
    ["kibana", 5601],
    ["grafana", 80],
  ])("renders ClusterIP service %s:%d", (name, port) => {
    const service = namedResource("Service", name);
    expect(service).toContain("type: ClusterIP");
    expect(service).toContain(`port: ${port}`);
  });

  test("renders required persistent storage", () => {
    expect(namedResource("PersistentVolumeClaim", "grafana")).toContain(
      'storage: "1Gi"',
    );
    expect(namedResource("Prometheus", "bank-monitoring-prometheus")).toContain(
      "storage: 5Gi",
    );
    expect(namedResource("StatefulSet", "elasticsearch")).toContain(
      "storage: 5Gi",
    );
  });

  test("renders startup and readiness probes", () => {
    const elasticsearch = namedResource("StatefulSet", "elasticsearch");
    expect(elasticsearch).toContain("startupProbe:");
    expect(elasticsearch).toContain("readinessProbe:");
    expect(namedResource("Deployment", "logstash")).toContain("startupProbe:");
    expect(namedResource("Deployment", "kibana")).toContain("readinessProbe:");
    expect(namedResource("Deployment", "zipkin")).toContain("startupProbe:");
  });

  test("renders application ServiceMonitors and five bank alerts", () => {
    const applications = [
      "front-ui",
      "bank-gateway",
      "accounts-service",
      "cash-service",
      "transfer-service",
      "exchange-service",
      "exchange-generator",
      "blocker-service",
      "notifications-service",
    ];
    for (const application of applications) {
      expect(namedResource("ServiceMonitor", application)).toContain(
        "path: /actuator/prometheus",
      );
    }
    const rule = namedResource("PrometheusRule", "bank-alerts");
    expect((rule.match(/- alert: Bank/g) ?? []).length).toBe(5);
    expect(rule).toContain("clamp_min");
  });

  test("does not expose observability through public routes", () => {
    const publicRoutes = [
      ...resources("HTTPRoute"),
      ...resources("Ingress"),
    ].join("\n");
    expect(publicRoutes).not.toMatch(
      /\b(zipkin|prometheus|grafana|elasticsearch|logstash|kibana)\b/i,
    );
  });

  test("does not render plaintext Grafana credentials", () => {
    expect(manifest).toContain("name: grafana-admin-credentials");
    expect(manifest).not.toMatch(/adminPassword:\s*\S+/);
    expect(manifest).not.toMatch(/admin-password:\s*\S+/);
  });

  test("keeps the Elastic Stack on one version", () => {
    const elasticImages =
      manifest.match(/docker\.elastic\.co\/(?:elasticsearch\/elasticsearch|logstash\/logstash|kibana\/kibana):[^\s"]+/g) ??
      [];
    expect(elasticImages).toHaveLength(3);
    expect(elasticImages.every((image) => image.endsWith(":9.4.3"))).toBeTrue();
  });
});
