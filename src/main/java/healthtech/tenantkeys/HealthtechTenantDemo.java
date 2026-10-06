package healthtech.tenantkeys;

/** Runnable request boundary for a healthtech tenant's appointment-operation credential. */
public final class HealthtechTenantDemo {
    public static void main(String[] args) throws Exception {
        TenantControlPlaneConfig config = TenantControlPlaneConfig.fromEnvironment();
        TenantCredentialService service = new TenantCredentialService(
                new InfraiControlPlane(config.baseUrl(), config.apiKey()));

        TenantCredentialService.TenantCredential credential = service.provision(
                new TenantCredentialService.TenantRequest("harbor-clinic", "chenhua@changba.com"));
        try {
            System.out.println("Created tenant user " + credential.userId() + " and scoped key " + credential.keyId());
            System.out.println("Store plaintextKey now; it is not returned again: " + credential.plaintextKey());
        } finally {
            service.offboard(credential);
            System.out.println("Deleted tenant user " + credential.userId() + " and revoked key " + credential.keyId());
        }
    }
}
