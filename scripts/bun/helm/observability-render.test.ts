import { beforeAll, describe, expect, test } from "bun:test";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../..", import.meta.url));
let manifest = "";
let documents: string[] = [];

function renderChart(namespace = "dev", valuesFile = "helm/bank/values-dev.yaml"): string {
  const result = Bun.spawnSync({
    cmd: [
      "helm",
      "template",
      "bank",
      "helm/bank",
      "--namespace",
      namespace,
      "-f",
      valuesFile,
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

function optionalNamedResource(kind: string, name: string): string | undefined {
  return resources(kind).find((document) =>
    new RegExp(`^  name: ${name}$`, "m").test(document),
  );
}

function renderedDocuments(renderedManifest: string): string[] {
  return renderedManifest.split(/\r?\n---\r?\n/);
}

function resourceFromDocuments(renderedDocuments: string[], kind: string, name: string): string {
  const resource = renderedDocuments
    .filter((document) => new RegExp(`^kind: ${kind}$`, "m").test(document))
    .find((document) => new RegExp(`^  name: ${name}$`, "m").test(document));
  if (!resource) {
    throw new Error(`${kind}/${name} was not rendered`);
  }
  return resource;
}

function observabilityTestJob(renderedManifest: string): string {
  return resourceFromDocuments(
    renderedDocuments(renderedManifest),
    "Job",
    "bank-observability-test",
  );
}

beforeAll(() => {
  manifest = renderChart();
  documents = renderedDocuments(manifest);
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
      const serviceMonitor = namedResource("ServiceMonitor", application);
      expect(serviceMonitor).toContain("bank/management-service: \"true\"");
      expect(serviceMonitor).toContain("port: management");
      expect(serviceMonitor).toContain("path: /actuator/prometheus");

      const managementService = namedResource("Service", `${application}-management`);
      expect(managementService).toContain("bank/management-service: \"true\"");
      expect(managementService).toContain("name: management");

      const applicationService = optionalNamedResource("Service", application);
      if (applicationService) {
        expect(applicationService).not.toContain("bank/management-service: \"true\"");
      }
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

  test.each([
    ["dev", "helm/bank/values-dev.yaml", true],
    ["test", "helm/bank/values-test.yaml", false],
    ["prod", "helm/bank/values-prod.yaml", false],
  ])(
    "renders Elastic Stack observability checks for %s only when enabled",
    (namespace, valuesFile, shouldRenderElasticStackChecks) => {
      const renderedManifest = namespace === "dev" ? manifest : renderChart(namespace, valuesFile);
      const observabilityJob = observabilityTestJob(renderedManifest);
      const elasticStackChecks = [
        'check_url "Elasticsearch health" "http://elasticsearch:9200/_cluster/health"',
        'check_url "Logstash API" "http://logstash:9600/_node/pipelines/bank-logs"',
        "telnet://logstash:5000",
        'check_url "Kibana status" "http://kibana:5601/api/status"',
      ];

      for (const check of elasticStackChecks) {
        if (shouldRenderElasticStackChecks) {
          expect(observabilityJob).toContain(check);
        } else {
          expect(observabilityJob).not.toContain(check);
        }
      }
    },
  );
});
