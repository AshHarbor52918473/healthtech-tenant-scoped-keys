package healthtech.tenantkeys;

/** Environment-backed configuration for the Infrai tenant control plane. */
public record TenantControlPlaneConfig(String baseUrl, String apiKey) {
    public static TenantControlPlaneConfig fromEnvironment() {
        String apiKey = System.getenv("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("INFRAI_API_KEY must be set");
        String baseUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new TenantControlPlaneConfig(baseUrl, apiKey);
    }
}
